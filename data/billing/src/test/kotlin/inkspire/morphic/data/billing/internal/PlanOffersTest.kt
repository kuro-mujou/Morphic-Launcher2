package inkspire.morphic.data.billing.internal

import inkspire.morphic.data.billing.BillingPeriod
import inkspire.morphic.data.billing.FreeTrial
import inkspire.morphic.data.billing.TrialUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanOffersTest {

    private fun renewing(period: String, micros: Long) = Phase(period, micros, "$micros", renews = true)

    private fun trial(period: String) = Phase(period, 0L, "Free", renews = false)

    @Test
    fun `one plan per base plan, monthly before yearly`() {
        val plans = plansFrom(
            listOf(
                Offer("yearly", null, "y", listOf(renewing("P1Y", 30_000_000))),
                Offer("monthly", null, "m", listOf(renewing("P1M", 3_000_000))),
            ),
        )

        assertEquals(listOf(BillingPeriod.MONTHLY, BillingPeriod.YEARLY), plans.map { it.period })
        assertEquals(listOf(3_000_000L, 30_000_000L), plans.map { it.priceMicros })
    }

    @Test
    fun `an eligible trial offer is bought through, and the price is still the renewing one`() {
        val plan = plansFrom(
            listOf(
                Offer("yearly", null, "plain", listOf(renewing("P1Y", 30_000_000))),
                Offer("yearly", "trial", "with-trial", listOf(trial("P7D"), renewing("P1Y", 30_000_000))),
            ),
        ).single()

        assertEquals("with-trial", plan.offerToken)
        assertEquals(FreeTrial(7, TrialUnit.DAY), plan.freeTrial)
        assertEquals(30_000_000L, plan.priceMicros)
    }

    @Test
    fun `the longer of two trials wins`() {
        val plan = plansFrom(
            listOf(
                Offer("monthly", "week", "week", listOf(trial("P1W"), renewing("P1M", 3_000_000))),
                Offer("monthly", "days", "days", listOf(trial("P3D"), renewing("P1M", 3_000_000))),
            ),
        ).single()

        assertEquals("week", plan.offerToken)
        assertEquals(FreeTrial(1, TrialUnit.WEEK), plan.freeTrial)
    }

    @Test
    fun `a discounted intro phase is not a free trial`() {
        val plan = plansFrom(
            listOf(
                Offer("monthly", "intro", "intro", listOf(Phase("P1M", 1_000_000, "1", false), renewing("P1M", 3_000_000))),
            ),
        ).single()

        assertNull(plan.freeTrial)
    }

    @Test
    fun `a base plan with any other period is left out`() {
        val plans = plansFrom(
            listOf(
                Offer("quarterly", null, "q", listOf(renewing("P3M", 8_000_000))),
                Offer("monthly", null, "m", listOf(renewing("P1M", 3_000_000))),
            ),
        )

        assertEquals(listOf(BillingPeriod.MONTHLY), plans.map { it.period })
    }
}
