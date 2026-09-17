package inkspire.morphic.data.billing.internal

import inkspire.morphic.data.billing.BillingPeriod
import inkspire.morphic.data.billing.FreeTrial
import inkspire.morphic.data.billing.SubscriptionPlan
import inkspire.morphic.data.billing.TrialUnit

/**
 * One subscription offer as Play returns it, reduced to what choosing a plan reads — so the choice is testable
 * without Play's classes, which cannot be constructed outside the library.
 *
 * @property offerId null for the base plan's own offer, which every base plan has.
 */
internal data class Offer(
    val basePlanId: String,
    val offerId: String?,
    val token: String,
    val phases: List<Phase>,
)

/**
 * One pricing phase of an [Offer].
 *
 * @property billingPeriod ISO 8601, as Play writes it: `P1M`, `P1Y`, `P7D`.
 * @property renews whether this is the phase that repeats until canceled — the plan's real price.
 */
internal data class Phase(
    val billingPeriod: String,
    val priceMicros: Long,
    val formattedPrice: String,
    val renews: Boolean,
)

/**
 * The plans to show: one per base plan that renews monthly or yearly, in [BillingPeriod] order.
 *
 * **Per base plan, the offer with a free trial wins over the plain one**, and the longer trial over the shorter. Play
 * returns only the offers this user is eligible for, so a trial present here is one they can take, and buying through
 * the plain offer instead would silently give it up.
 *
 * A base plan with another period is skipped rather than guessed at, which is what a quarterly plan added in Play
 * Console later would get until the screen is built to show one.
 */
internal fun plansFrom(offers: List<Offer>): List<SubscriptionPlan> =
    offers
        .groupBy { it.basePlanId }
        .values
        .mapNotNull { group ->
            val renewing = group.firstNotNullOfOrNull { offer -> offer.phases.firstOrNull { it.renews } }
                ?: return@mapNotNull null
            val period = billingPeriodOf(renewing.billingPeriod) ?: return@mapNotNull null
            val chosen = group.maxWith(compareBy<Offer> { it.trialDaysForOrdering() }.thenBy { it.offerId == null })
            SubscriptionPlan(
                period = period,
                price = renewing.formattedPrice,
                priceMicros = renewing.priceMicros,
                freeTrial = chosen.freeTrial(),
                offerToken = chosen.token,
            )
        }
        .distinctBy { it.period }
        .sortedBy { it.period }

private fun billingPeriodOf(iso: String): BillingPeriod? = when (isoPeriod(iso)) {
    IsoPeriod(1, TrialUnit.MONTH) -> BillingPeriod.MONTHLY
    IsoPeriod(1, TrialUnit.YEAR) -> BillingPeriod.YEARLY
    else -> null
}

private fun Offer.freeTrial(): FreeTrial? =
    phases
        .firstOrNull { !it.renews && it.priceMicros == 0L }
        ?.let { isoPeriod(it.billingPeriod) }
        ?.let { FreeTrial(it.count, it.unit) }

private const val DaysPerWeek = 7
private const val RoughDaysPerMonth = 30
private const val RoughDaysPerYear = 365

/** Only for ranking two trials; the displayed length is [freeTrial]'s, never this. */
private fun Offer.trialDaysForOrdering(): Int = freeTrial()?.let { trial ->
    trial.count * when (trial.unit) {
        TrialUnit.DAY -> 1
        TrialUnit.WEEK -> DaysPerWeek
        TrialUnit.MONTH -> RoughDaysPerMonth
        TrialUnit.YEAR -> RoughDaysPerYear
    }
} ?: 0

/** A single-unit ISO 8601 period: `P7D` is seven of [TrialUnit.DAY]. */
private data class IsoPeriod(val count: Int, val unit: TrialUnit)

/** Null for anything but a single unit, including a compound `P1Y2M`. */
private fun isoPeriod(iso: String): IsoPeriod? {
    val match = Regex("""P(\d+)([DWMY])""").matchEntire(iso) ?: return null
    val unit = when (match.groupValues[2]) {
        "D" -> TrialUnit.DAY
        "W" -> TrialUnit.WEEK
        "M" -> TrialUnit.MONTH
        else -> TrialUnit.YEAR
    }
    return IsoPeriod(match.groupValues[1].toInt(), unit)
}
