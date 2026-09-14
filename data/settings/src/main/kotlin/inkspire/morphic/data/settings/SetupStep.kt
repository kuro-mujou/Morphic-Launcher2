package inkspire.morphic.data.settings

/**
 * **One thing the setup hub can ask the user to do**, in the order the hub lists them.
 *
 * Only the step is named here; whether it is done is never stored. Each is derived from the subsystem that owns the
 * answer — the home role, the wallpaper store, the icon recipe, the placed widgets — so the hub can never tell a user to
 * do what they have already done somewhere else.
 */
enum class SetupStep {
    /** Make this launcher the one the home button opens. */
    DEFAULT_LAUNCHER,

    /** Choose a wallpaper through the launcher. */
    WALLPAPER,

    /** Give app icons a look of their own. */
    ICON_STYLE,

    /** Put a first widget on HOME. */
    FIRST_WIDGET,
    ;

    /**
     * Whether the user may put this step away without doing it. **Every step but [DEFAULT_LAUNCHER]**: a launcher that
     * is not the home app is not working, so that one stays until it is done.
     */
    val dismissible: Boolean get() = this != DEFAULT_LAUNCHER
}
