package inkspire.morphic.data.billing

/** How often a [SubscriptionPlan] renews. Only the two base plans the subscription is sold with. */
enum class BillingPeriod { MONTHLY, YEARLY }

/**
 * One way to buy the subscription, as Play prices it for this user.
 *
 * @property price the renewing price, formatted by Play in the user's currency — shown as is, never re-formatted.
 * @property priceMicros the same price in millionths of the currency unit, for comparing plans. Never for display.
 * @property freeTrial the free trial this user is eligible for on this plan, or null. Play only returns offers the user
 *   is eligible for, so a trial here is one they can actually get.
 */
data class SubscriptionPlan(
    val period: BillingPeriod,
    val price: String,
    val priceMicros: Long,
    val freeTrial: FreeTrial?,
    internal val offerToken: String,
)

/**
 * A free trial's length, as Play states it. Kept as a count of a unit rather than converted to days, since a month
 * has no fixed number of them and "30-day trial" for a one-month one would be a claim Play did not make.
 */
data class FreeTrial(val count: Int, val unit: TrialUnit)

enum class TrialUnit { DAY, WEEK, MONTH, YEAR }

/** Why the subscription cannot be sold or read right now. */
enum class BillingProblem {
    /** No Play Billing on this device: no Play Store, a blocked one, or an account that cannot pay. */
    NO_PLAY_BILLING,

    /** Play answered, but has no such subscription for sale — it is not set up in Play Console for this build. */
    NOT_FOR_SALE,

    /** Play could not be reached. Worth retrying. */
    NETWORK,

    /** Anything else Play reported. */
    ERROR,
}

/** The plans on offer. */
sealed interface SubscriptionPlans {
    data object Loading : SubscriptionPlans

    data class Unavailable(val problem: BillingProblem) : SubscriptionPlans

    /** Never empty, and in [BillingPeriod] order. */
    data class Ready(val plans: List<SubscriptionPlan>) : SubscriptionPlans
}

/** Whether the user holds the subscription. What a paid feature gates on. */
sealed interface Entitlement {
    /** Not known yet. A gate must not lock or unlock on this. */
    data object Checking : Entitlement

    /** Paid for. [autoRenewing] is false once the user has canceled but the paid period has not ended. */
    data class Active(val autoRenewing: Boolean) : Entitlement

    /** Bought with a payment method that has not cleared. Not paid for yet. */
    data object Pending : Entitlement

    data object None : Entitlement

    /** Play Billing could not answer. See [BillingProblem] for why. */
    data class Unavailable(val problem: BillingProblem) : Entitlement
}
