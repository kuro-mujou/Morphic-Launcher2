package inkspire.morphic.core.common.scope

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * App-lifetime [CoroutineScope] for work that must outlive any single screen. Runs on a [SupervisorJob] (one
 * failing child does not cancel its siblings) and the default dispatcher, with a [CoroutineExceptionHandler]
 * so uncaught failures are logged instead of silently lost.
 */
class ApplicationScope(
    dispatchers: AppDispatchers,
) : CoroutineScope {
    private val handler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Uncaught exception in ApplicationScope")
    }

    override val coroutineContext = SupervisorJob() + dispatchers.default + handler
}
