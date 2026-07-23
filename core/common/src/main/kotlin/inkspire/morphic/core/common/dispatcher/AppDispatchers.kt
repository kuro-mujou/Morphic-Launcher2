package inkspire.morphic.core.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * An injectable set of coroutine dispatchers so call sites depend on this abstraction instead of hard-coding
 * `Dispatchers.*`. Lets tests substitute deterministic dispatchers.
 */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

/** Production [AppDispatchers] backed by the real [Dispatchers]. */
class DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}
