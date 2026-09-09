package inkspire.morphic.core.model

/**
 * Which orientations the launcher lets itself be drawn in.
 *
 * A *request* to the platform rather than a description of the window: what the launcher is actually drawn in is
 * still the system's answer, which is why every surface keeps reading its own [DeviceConfiguration] instead of
 * inferring one from this.
 *
 * **[AUTO] is the absence of a request, not a third behavior.** It asks for nothing and so inherits the device's own
 * rotation setting — including the user having auto-rotate switched off system-wide, which no launcher preference
 * should be able to override.
 */
enum class RotationMode {
    AUTO,
    PORTRAIT,
    LANDSCAPE,
}
