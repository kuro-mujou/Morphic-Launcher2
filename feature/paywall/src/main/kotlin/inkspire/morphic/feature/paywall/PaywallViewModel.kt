package inkspire.morphic.feature.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.billing.BillingPeriod
import inkspire.morphic.data.billing.PurchaseLaunch
import inkspire.morphic.data.billing.SubscriptionPlans
import inkspire.morphic.data.billing.SubscriptionPurchaser
import inkspire.morphic.data.billing.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The purchase screen: the plans for sale, whether the user already holds one, and opening Play's purchase sheet.
 *
 * **What was bought is never tracked here.** Play reports the outcome to the repository, whose entitlement this screen
 * reads like any other state — so a purchase finished after the screen was left still lands.
 */
class PaywallViewModel(
    private val repository: SubscriptionRepository,
    private val purchaser: SubscriptionPurchaser,
) : ViewModel() {

    private val selected = MutableStateFlow(BillingPeriod.YEARLY)
    private val opening = MutableStateFlow(false)
    private val launchFailed = MutableStateFlow(false)

    val state: StateFlow<PaywallState> =
        combine(
            repository.plans,
            repository.entitlement,
            selected,
            opening,
            launchFailed,
        ) { plans, entitlement, period, isOpening, failed ->
            PaywallState(
                plans = plans,
                entitlement = entitlement,
                selected = period.onOffer(plans),
                opening = isOpening,
                launchFailed = failed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMs), PaywallState())

    /** Re-reads the plans and entitlement from Play; the screen calls it on every resume. */
    fun refresh() = repository.refresh()

    fun select(period: BillingPeriod) {
        selected.value = period
    }

    fun subscribe(activity: Activity) {
        val plan = state.value.selectedPlan ?: return
        if (opening.value) return
        opening.value = true
        launchFailed.value = false
        viewModelScope.launch {
            try {
                launchFailed.value = purchaser.purchase(activity, plan) == PurchaseLaunch.FAILED
            } finally {
                opening.value = false
            }
        }
    }
}

/** This period if it is for sale, otherwise the first that is. Unchanged while nothing is for sale. */
private fun BillingPeriod.onOffer(plans: SubscriptionPlans): BillingPeriod {
    val offered = (plans as? SubscriptionPlans.Ready)?.plans ?: return this
    return if (offered.any { it.period == this }) this else offered.first().period
}

private const val StopTimeoutMs = 5_000L
