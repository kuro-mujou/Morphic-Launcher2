package inkspire.morphic.data.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * The subscription as Play Billing knows it: what is for sale, and whether the user holds it.
 *
 * **Nothing is stored here.** Play caches purchases on the device, so the entitlement is readable offline, and a copy
 * of our own would be a second answer able to disagree with Play's.
 *
 * **Changes made outside the app arrive only on [refresh]**: a cancellation, a renewal or a refund happens in Play, and
 * nothing tells a running app. A screen showing the entitlement refreshes when it resumes.
 */
interface SubscriptionRepository {
    val plans: StateFlow<SubscriptionPlans>

    val entitlement: StateFlow<Entitlement>

    /** Re-reads both from Play. Returns at once; the flows update when Play answers. */
    fun refresh()
}
