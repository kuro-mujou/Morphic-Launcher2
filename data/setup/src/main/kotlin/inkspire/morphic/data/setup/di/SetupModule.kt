package inkspire.morphic.data.setup.di

import inkspire.morphic.data.setup.SetupSteps
import inkspire.morphic.data.setup.internal.SetupStepsImpl
import org.koin.dsl.module

/**
 * Koin module for `data:setup`. A `single`, because the home-role answer is held in memory and both of its readers must
 * see the same one. Its five dependencies come from `data:settings`, `data:apps`, `data:wallpaper`, `data:layout` and
 * `core:common`.
 */
val setupModule = module {
    single<SetupSteps> { SetupStepsImpl(get(), get(), get(), get(), get()) }
}
