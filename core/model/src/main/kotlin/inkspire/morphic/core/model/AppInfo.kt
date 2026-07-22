package inkspire.morphic.core.model

/**
 * Display metadata and state for a single installed, launchable app.
 *
 * @property componentKey Unique identity of the launchable component.
 * @property label Human-readable app name shown under the icon.
 * @property isWorkProfile True when the app belongs to a managed work profile.
 * @property isSuspended True when the app is currently suspended (by policy, Digital Wellbeing, etc.).
 * @property isSystem True for a pre-installed system app.
 * @property category Android's `ApplicationInfo.category` value, or -1 when uncategorized.
 * @property firstInstallTime Epoch millis of first install; 0 when unknown.
 * @property lastUpdateTime Epoch millis of the last update; 0 when unknown.
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