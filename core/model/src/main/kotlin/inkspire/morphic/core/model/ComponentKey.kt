package inkspire.morphic.core.model

import kotlinx.serialization.Serializable

/**
 * A unique identifier for a launchable application component.
 *
 * A package name alone is insufficient to uniquely identify a launchable item because:
 * 1. **Multiple Entry Points**: A single package can contain multiple launcher activities.
 * 2. **Multi-User Support**: The same component can be installed across different user profiles
 *
 * @property packageName The Android package name (e.g., "com.example.app").
 * @property className The full class name of the specific activity to launch.
 * @property userSerial A unique identifier for the user profile (Personal, Work, etc.).
 */
@Serializable
data class ComponentKey(
    val packageName: String,
    val className: String,
    val userSerial: Long = 0L,
) {
    fun flatten(): String = "$packageName/$className#$userSerial"

    companion object {
        fun parse(flat: String): ComponentKey? {
            val hashIdx = flat.lastIndexOf('#')
            if (hashIdx == -1) return null
            val slashIdx = flat.lastIndexOf('/')
            if (slashIdx == -1) return null
            val packageName = flat.substring(0, slashIdx)
            val className = flat.substring(slashIdx + 1, hashIdx)
            val userSerial = flat.substring(hashIdx + 1).toLongOrNull() ?: return null
            if (packageName.isEmpty() || className.isEmpty()) return null
            return ComponentKey(packageName, className, userSerial)
        }
    }
}