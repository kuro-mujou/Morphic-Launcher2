package inkspire.morphic.core.model

/**
 * Layout orientation of a grid surface. Persisted per grid so portrait and landscape can each keep their
 * own configuration.
 */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE,
}

/**
 * Which orientation's stored arrangement this device configuration draws.
 *
 * The bridge between the two keys the launcher uses: [DeviceConfiguration] keys *configuration* (how big a grid is,
 * how large its icons are — form factor crossed with orientation, so a tablet and a phone can differ in the same
 * posture), while [Orientation] keys *arrangement* (which app sits where). A surface that re-fits its placements has
 * to know it is looking at the ones it is drawing, which is the one place the two meet.
 */
val DeviceConfiguration.orientation: Orientation
    get() = if (isPortrait) Orientation.PORTRAIT else Orientation.LANDSCAPE