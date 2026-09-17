package inkspire.morphic.data.billing.di

import inkspire.morphic.data.billing.SubscriptionPurchaser
import inkspire.morphic.data.billing.SubscriptionRepository
import inkspire.morphic.data.billing.internal.PlaySubscription
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Koin module for `data:billing`. One instance behind both interfaces, since Play expects one client per app. `Context`
 * comes from `androidContext()` in the application's `startKoin`.
 */
val billingModule = module {
    single { PlaySubscription(get(), get(), get()) } binds
        arrayOf(SubscriptionRepository::class, SubscriptionPurchaser::class)
}
