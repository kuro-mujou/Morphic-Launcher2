package inkspire.morphic.core.model

/**
 * Represents a change signal (such as installed, removed, or updated)
 * that the data layer reacts to.
 */
sealed interface AppEvent {
    /** The app component this event concerns. */
    val component: ComponentKey

    /** The component was newly installed or became available. */
    data class Added(override val component: ComponentKey) : AppEvent

    /** The component was uninstalled or became unavailable. */
    data class Removed(override val component: ComponentKey) : AppEvent

    /** The component changed in place (updated, relabeled, enabled, …). */
    data class Changed(override val component: ComponentKey) : AppEvent

    /** The component's suspended state changed to [isSuspended]. */
    data class Suspended(
        override val component: ComponentKey,
        val isSuspended: Boolean,
    ) : AppEvent
}
