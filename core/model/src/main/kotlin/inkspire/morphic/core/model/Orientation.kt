package inkspire.morphic.core.model

/**
 * Which way round a full-screen image is.
 *
 * **The wallpaper's concern, and nothing else's.** Android has no per-orientation static wallpaper, so a rotating
 * pair is two files and "the wallpaper" is not one picture until this says which of them.
 *
 * Grid *arrangement* is keyed by [ArrangementKey] and grid *configuration* by [DeviceConfiguration]. Neither goes
 * through here, and neither should: this enum cannot tell a phone from a tablet, so a layout keyed on it would
 * silently share one arrangement between two devices that divide their window quite differently.
 */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE,
}
