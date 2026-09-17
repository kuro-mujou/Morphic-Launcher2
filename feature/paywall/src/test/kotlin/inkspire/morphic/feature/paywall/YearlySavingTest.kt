package inkspire.morphic.feature.paywall

import inkspire.morphic.data.billing.BillingPeriod
import inkspire.morphic.data.billing.SubscriptionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YearlySavingTest {

    private fun plan(period: BillingPeriod, micros: Long) = SubscriptionPlan(period, "", micros, null, "")

    @Test
    fun `rounds down, so the badge never claims more than the prices`() {
        // 12 x 2.99 = 35.88; 19.99 is 44.28% less.
        assertEquals(44, yearlySavingPercent(plan(BillingPeriod.MONTHLY, 2_990_000), plan(BillingPeriod.YEARLY, 19_990_000)))
    }

    @Test
    fun `no badge when the year is not cheaper`() {
        assertNull(yearlySavingPercent(plan(BillingPeriod.MONTHLY, 1_000_000), plan(BillingPeriod.YEARLY, 12_000_000)))
        assertNull(yearlySavingPercent(plan(BillingPeriod.MONTHLY, 1_000_000), plan(BillingPeriod.YEARLY, 13_000_000)))
    }

    @Test
    fun `no badge for a saving under one percent`() {
        assertNull(yearlySavingPercent(plan(BillingPeriod.MONTHLY, 1_000_000), plan(BillingPeriod.YEARLY, 11_990_000)))
    }
}
