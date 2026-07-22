package inkspire.morphic.core.model

/**
 * Represents comprehensive metadata and state information for an installed application.
 *
 */
data class AppInfo(
    val componentKey: ComponentKey,
    val label: String,
    val isWorkProfile: Boolean,
    val isSuspended: Boolean,
    val isSystem: Boolean,
    val category: Int = -1,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
)