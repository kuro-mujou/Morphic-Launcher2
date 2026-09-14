package inkspire.morphic.feature.onboarding.di

import inkspire.morphic.feature.onboarding.OnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module for the onboarding gate. Its `SettingsRepository` comes from `data:settings`. */
val onboardingModule = module {
    viewModel { OnboardingViewModel(get()) }
}
