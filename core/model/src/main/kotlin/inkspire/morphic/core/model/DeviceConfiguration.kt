package inkspire.morphic.core.model

/**
 * Device form factor crossed with orientation — the key used to pick per-configuration grid defaults and
 * layouts. This is a pure domain enum; window-size detection lives in `core:designsystem`
 * (`DeviceConfiguration.fromWindowSizeClass` / `currentDeviceConfiguration`), which is why the empty
 * [companion object] exists here.
 */
enum class DeviceConfiguration {
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE;

    /** True for the two portrait configurations. */
    val isPortrait: Boolean get() = this == PHONE_PORTRAIT || this == TABLET_PORTRAIT

    /** True for the two landscape configurations. */
    val isLandscape: Boolean get() = !isPortrait

    /** True for the two tablet configurations. */
    val isTablet: Boolean get() = this == TABLET_PORTRAIT || this == TABLET_LANDSCAPE

    /**
     * This form factor held upright.
     *
     * Portrait is the reference posture while the two are kept in step, so anything writing *into* the reference
     * needs the grids it will be drawn against — which are this configuration's, not the one on screen.
     */
    val portrait: DeviceConfiguration get() = if (isTablet) TABLET_PORTRAIT else PHONE_PORTRAIT

    companion object
}
