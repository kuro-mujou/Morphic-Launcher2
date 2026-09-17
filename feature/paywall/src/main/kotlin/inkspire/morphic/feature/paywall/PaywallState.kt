package inkspire.morphic.feature.paywall

import inkspire.morphic.data.billing.BillingPeriod
import inkspire.morphic.data.billing.Entitlement
import inkspire.morphic.data.billing.SubscriptionPlan
import inkspire.morphic.data.billing.SubscriptionPlans

/**
 * What the purchase screen shows.
 *
 * @property selected the period the user has picked. Always one of the plans on offer once there are any, since a
 *   selection naming a plan that is not for sale would leave the button buying nothing.
 * @property opening whether Play's purchase sheet is being opened, so a second tap does not open two.
 * @property launchFailed whether the last tap failed to open the sheet. Without it that tap would do nothing visible.
 */
data class PaywallState(
    val plans: SubscriptionPlans = SubscriptionPlans.Loading,
    val entitlement: Entitlement = Entitlement.Checking,
    val selected: BillingPeriod = BillingPeriod.YEARLY,
    val opening: Boolean = false,
    val launchFailed: Boolean = false,
) {
    val selectedPlan: SubscriptionPlan?
        get() = (plans as? SubscriptionPlans.Ready)?.plans?.firstOrNull { it.period == selected }
}

/**
 * How much cheaper [yearly] is than twelve months of [monthly], as a whole percentage — or null when it is not cheaper,
 * since a "save 0%" badge would advertise nothing.
 *
 * Compared in micros, which Play gives in the same currency for both plans. Rounded down, so the badge never claims
 * more than the prices do.
 */
internal fun yearlySavingPercent(monthly: SubscriptionPlan, yearly: SubscriptionPlan): Int? {
    val twelveMonths = monthly.priceMicros * MonthsPerYear
    if (twelveMonths <= 0 || yearly.priceMicros >= twelveMonths) return null
    val percent = ((twelveMonths - yearly.priceMicros) * PercentScale / twelveMonths).toInt()
    return percent.takeIf { it > 0 }
}

private const val MonthsPerYear = 12
private const val PercentScale = 100
