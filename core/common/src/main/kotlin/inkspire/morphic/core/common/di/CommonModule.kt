package inkspire.morphic.core.common.di

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.common.dispatcher.DefaultAppDispatchers
import inkspire.morphic.core.common.scope.ApplicationScope
import org.koin.dsl.module

val commonModule = module {
    single<AppDispatchers> { DefaultAppDispatchers() }
    single { ApplicationScope(get()) }
}