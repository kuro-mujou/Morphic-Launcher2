package inkspire.morphic.feature.onboarding.di

import inkspire.morphic.feature.onboarding.BuiltInLooks
import inkspire.morphic.feature.onboarding.OnboardingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the onboarding gate. `SettingsRepository` and `LookRepository` come from `data:settings`, and
 * `AppDispatchers` from `core:common`; the offered looks are read from this module's own assets.
 */
val onboardingModule = module {
    single { BuiltInLooks(androidContext()) }
    viewModel { OnboardingViewModel(get(), get(), get(), get()) }
}
