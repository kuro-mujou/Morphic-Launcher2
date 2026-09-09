package inkspire.morphic.core.model

/**
 * Which stored arrangement — *which item sits where* — a surface reads and writes.
 *
 * Deliberately not [DeviceConfiguration], which keys *configuration*: how big a grid is, how large its icons are.
 * The two look like the same four postures and are not, because an arrangement may be authored **once** and drawn
 * in every posture. [PHONE_SHARED] and [TABLET_SHARED] are that once: a reference layout belonging to no posture,
 * which each posture of its form factor projects from. Folding those two roles into one enum would make "the
 * layout every posture shares" unrepresentable, and the alternative — a nullable posture meaning "shared" — puts
 * the distinction in a null check rather than in the type.
 *
 * **One reference per form factor, not one overall.** A phone window and a tablet window divide into different
 * lattices, so a single reference would have to be projected into both anyway; and a foldable changes form factor
 * without the user having asked for anything, so the two need to be separable.
 */
enum class ArrangementKey {
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE,
    PHONE_SHARED,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE,
    TABLET_SHARED,
}

/**
 * The arrangement this configuration authors when it owns a layout of its own.
 *
 * The bridge between the two keys the launcher uses, and the *only* honest one: a configuration maps to exactly
 * one authored arrangement, while the reverse does not hold — no configuration maps to a `*_SHARED` key, which is
 * chosen by policy rather than read off the window.
 */
val DeviceConfiguration.authoredArrangement: ArrangementKey
    get() = when (this) {
        DeviceConfiguration.PHONE_PORTRAIT -> ArrangementKey.PHONE_PORTRAIT
        DeviceConfiguration.PHONE_LANDSCAPE -> ArrangementKey.PHONE_LANDSCAPE
        DeviceConfiguration.TABLET_PORTRAIT -> ArrangementKey.TABLET_PORTRAIT
        DeviceConfiguration.TABLET_LANDSCAPE -> ArrangementKey.TABLET_LANDSCAPE
    }

/**
 * The portrait arrangement of the same form factor, or null when this **is** one.
 *
 * What a posture with no layout of its own is seeded from, and portrait is the source rather than "whichever one
 * has something in it" because a launcher is set up in portrait: it is the arrangement a user has actually
 * arranged. Null for the two `*_SHARED` keys as well — a reference layout is the thing others are seeded *from*,
 * so being seeded from a posture would invert it.
 */
val ArrangementKey.portraitCounterpart: ArrangementKey?
    get() = when (this) {
        ArrangementKey.PHONE_LANDSCAPE -> ArrangementKey.PHONE_PORTRAIT
        ArrangementKey.TABLET_LANDSCAPE -> ArrangementKey.TABLET_PORTRAIT
        ArrangementKey.PHONE_PORTRAIT,
        ArrangementKey.TABLET_PORTRAIT,
        ArrangementKey.PHONE_SHARED,
        ArrangementKey.TABLET_SHARED,
        -> null
    }
