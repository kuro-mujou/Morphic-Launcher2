package inkspire.morphic.core.common.scope

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class ApplicationScope(
    dispatchers: AppDispatchers,
) : CoroutineScope {
    override val coroutineContext = SupervisorJob() + dispatchers.default
}
