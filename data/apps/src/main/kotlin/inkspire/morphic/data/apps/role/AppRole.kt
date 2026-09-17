package inkspire.morphic.data.apps.role

/**
 * A job a device has *some* app for, named by the job rather than by the app that does it.
 *
 * **The point is that no package name is ever written down.** "The browser" is Chrome on one device, Samsung
 * Internet on another and Firefox on a third, and a pinned `com.android.chrome` is an icon that silently fails to
 * appear on two of them. Every value here is resolved against the device at the moment it is needed — see
 * [DefaultAppRoles], which holds the intents each one is resolved by. AOSP's own `default_workspace.xml` names its
 * default home screen the same way, through `<resolve>` blocks over intent categories.
 *
 * A role is a *question*, not a promise: a device may answer none of these (a tablet with no dialer), so a consumer
 * must handle an unresolved role rather than assuming a component comes back.
 */
enum class AppRole {
    /** Placing a call. */
    PHONE,

    /** Text messaging — SMS/MMS, not chat apps. */
    MESSAGING,

    /** The web browser. */
    BROWSER,

    /** Taking a photo. */
    CAMERA,

    /** The app store this device installs from. */
    STORE,

    /** Email. */
    EMAIL,

    /** The photo gallery — where pictures already taken are looked at. */
    GALLERY,

    /** Clock, alarms and timers. */
    CLOCK,

    /** The calendar. */
    CALENDAR,

    /** The calculator. */
    CALCULATOR,

    /** Maps and navigation. */
    MAPS,

    /** Music playback. */
    MUSIC,

    /** The system's own settings app — not this launcher's. */
    SETTINGS,
}
