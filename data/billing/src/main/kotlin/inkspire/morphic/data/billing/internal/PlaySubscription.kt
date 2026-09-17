package inkspire.morphic.data.billing.internal

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.common.scope.ApplicationScope
import inkspire.morphic.data.billing.BillingProblem
import inkspire.morphic.data.billing.Entitlement
import inkspire.morphic.data.billing.PurchaseLaunch
import inkspire.morphic.data.billing.SubscriptionPlan
import inkspire.morphic.data.billing.SubscriptionPlans
import inkspire.morphic.data.billing.SubscriptionPurchaser
import inkspire.morphic.data.billing.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * The subscription's product ID in Play Console. **A placeholder until the product is created there**, and it must
 * match exactly: a mismatch is not an error, Play just reports nothing for sale.
 */
private const val SubscriptionProductId = "morphic_premium"

/**
 * Both [SubscriptionRepository] and [SubscriptionPurchaser], over one `BillingClient`.
 *
 * **One class for two interfaces because both need the same two things**: the client, which Play expects one of per
 * app, and the `ProductDetails` last read, which a purchase must be launched with. Consumers still see two types.
 *
 * **Every purchase is acknowledged on the next read.** Play refunds one left unacknowledged for three days, and the
 * purchase sheet's own callback is not a reliable place to do it — the process can die before it runs. So the
 * callback only triggers a [refresh], and the refresh acknowledges whatever needs it.
 */
internal class PlaySubscription(
    context: Context,
    private val scope: ApplicationScope,
    private val dispatchers: AppDispatchers,
) : SubscriptionRepository, SubscriptionPurchaser {

    private val mutablePlans = MutableStateFlow<SubscriptionPlans>(SubscriptionPlans.Loading)
    override val plans: StateFlow<SubscriptionPlans> = mutablePlans.asStateFlow()

    private val mutableEntitlement = MutableStateFlow<Entitlement>(Entitlement.Checking)
    override val entitlement: StateFlow<Entitlement> = mutableEntitlement.asStateFlow()

    @Volatile
    private var product: ProductDetails? = null
    private val connecting = Mutex()
    private val reading = Mutex()

    private val client = BillingClient.newBuilder(context)
        .setListener { result, _ ->
            // Only a signal: what was bought is read back, and acknowledged, by the refresh. A cancel changes nothing.
            if (result.responseCode == BillingResponseCode.OK) refresh()
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun refresh() {
        scope.launch { reading.withLock { read() } }
    }

    override suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseLaunch {
        val details = product
        val setup = connect()
        if (details == null || setup.responseCode != BillingResponseCode.OK) return PurchaseLaunch.FAILED
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build(),
                ),
            )
            .build()
        val result = withContext(dispatchers.main) { client.launchBillingFlow(activity, params) }
        return when (result.responseCode) {
            BillingResponseCode.OK -> PurchaseLaunch.OPENED
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refresh()
                PurchaseLaunch.ALREADY_SUBSCRIBED
            }
            else -> {
                Timber.w("Purchase sheet did not open: %s", result)
                PurchaseLaunch.FAILED
            }
        }
    }

    private suspend fun read() {
        val setup = connect()
        if (setup.responseCode != BillingResponseCode.OK) {
            val problem = problemOf(setup)
            mutablePlans.value = SubscriptionPlans.Unavailable(problem)
            mutableEntitlement.value = Entitlement.Unavailable(problem)
            return
        }
        readPlans()
        readEntitlement()
    }

    private suspend fun readPlans() {
        val query = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(SubscriptionProductId)
            .setProductType(ProductType.SUBS)
            .build()
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(query)).build())
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            mutablePlans.value = SubscriptionPlans.Unavailable(problemOf(result.billingResult))
            return
        }
        val details = result.productDetailsList.orEmpty().firstOrNull { it.productId == SubscriptionProductId }
        product = details
        val plans = plansFrom(details?.subscriptionOfferDetails.orEmpty().map { it.toOffer() })
        mutablePlans.value = if (plans.isEmpty()) {
            SubscriptionPlans.Unavailable(BillingProblem.NOT_FOR_SALE)
        } else {
            SubscriptionPlans.Ready(plans)
        }
    }

    private suspend fun readEntitlement() {
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build())
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            mutableEntitlement.value = Entitlement.Unavailable(problemOf(result.billingResult))
            return
        }
        val ours = result.purchasesList.filter { SubscriptionProductId in it.products }
        ours
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { acknowledge(it) }
        mutableEntitlement.value = when {
            ours.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } ->
                Entitlement.Active(autoRenewing = ours.any { it.isAutoRenewing })
            ours.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> Entitlement.Pending
            else -> Entitlement.None
        }
    }

    /** A failure is only logged: the purchase still counts, and the next read tries again. */
    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        val result = client.acknowledgePurchase(params)
        if (result.responseCode != BillingResponseCode.OK) Timber.w("Acknowledge failed: %s", result)
    }

    /**
     * Connects once; afterwards `enableAutoServiceReconnection` reconnects on any call made while disconnected. The
     * lock is what stops two first calls from each starting a connection.
     */
    private suspend fun connect(): BillingResult = connecting.withLock {
        if (client.isReady) return@withLock BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build()
        suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(result)
                    }

                    override fun onBillingServiceDisconnected() = Unit
                },
            )
        }
    }
}

private fun ProductDetails.SubscriptionOfferDetails.toOffer() = Offer(
    basePlanId = basePlanId,
    offerId = offerId,
    token = offerToken,
    phases = pricingPhases.pricingPhaseList.map { phase ->
        Phase(
            billingPeriod = phase.billingPeriod,
            priceMicros = phase.priceAmountMicros,
            formattedPrice = phase.formattedPrice,
            renews = phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING,
        )
    },
)

private fun problemOf(result: BillingResult): BillingProblem = when (result.responseCode) {
    BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingProblem.NO_PLAY_BILLING
    BillingResponseCode.ITEM_UNAVAILABLE -> BillingProblem.NOT_FOR_SALE
    BillingResponseCode.NETWORK_ERROR,
    BillingResponseCode.SERVICE_UNAVAILABLE,
    BillingResponseCode.SERVICE_TIMEOUT,
    BillingResponseCode.SERVICE_DISCONNECTED,
    -> BillingProblem.NETWORK
    else -> {
        Timber.w("Play Billing: %s", result)
        BillingProblem.ERROR
    }
}
