package inkspire.morphic.data.billing

import android.app.Activity

/**
 * Starts buying the subscription: Play's purchase sheet, over [Activity].
 *
 * **The outcome is not returned.** The user finishes in Play's own UI, possibly minutes later, and the result reaches
 * [SubscriptionRepository.entitlement]. What returns is only whether the sheet opened.
 */
interface SubscriptionPurchaser {
    suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseLaunch
}

/** Whether Play's purchase sheet opened. */
enum class PurchaseLaunch {
    OPENED,

    /** The user already holds the subscription. The entitlement is refreshed so the screen can catch up. */
    ALREADY_SUBSCRIBED,

    FAILED,
}
