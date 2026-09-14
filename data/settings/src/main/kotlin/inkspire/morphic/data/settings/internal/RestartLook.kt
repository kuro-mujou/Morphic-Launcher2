package inkspire.morphic.data.settings.internal

import inkspire.morphic.data.settings.Look
import kotlinx.serialization.serializer

/**
 * The look in force when the user started first-run setup over, offered back as "Your current setup" — so starting over
 * changes nothing unless a different look is chosen.
 *
 * **Stored rather than held in memory**, because the first-run screen can outlive the process that opened it: a launcher
 * killed mid-choice must still offer the setup it came from, or its preselection would fall to a shipped look and apply
 * that over an arranged home. Cleared once setup finishes, and never carried by a look.
 */
internal val RestartLookSlice = SettingsSlice(
    name = "setup_restart_look",
    serializer = serializer<Look?>(),
    default = null,
)
