package inkspire.morphic.feature.paywall.di

import inkspire.morphic.feature.paywall.PaywallViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for `feature:paywall`. The repository and the purchaser come from `data:billing`'s module. */
val paywallModule = module {
    viewModel { PaywallViewModel(get(), get()) }
}
