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
/**
 * The other orientation of the same form factor, or null for a `*_SHARED` key, which has no orientation to be the
 * other of.
 *
 * [portraitCounterpart]'s two-way twin: that one names the source a posture is seeded *from*, this one names the
 * posture that has to be brought along when the two are kept in step.
 */
val ArrangementKey.oppositeOrientation: ArrangementKey?
    get() = when (this) {
        ArrangementKey.PHONE_PORTRAIT -> ArrangementKey.PHONE_LANDSCAPE
        ArrangementKey.PHONE_LANDSCAPE -> ArrangementKey.PHONE_PORTRAIT
        ArrangementKey.TABLET_PORTRAIT -> ArrangementKey.TABLET_LANDSCAPE
        ArrangementKey.TABLET_LANDSCAPE -> ArrangementKey.TABLET_PORTRAIT
        ArrangementKey.PHONE_SHARED, ArrangementKey.TABLET_SHARED -> null
    }

/**
 * This form factor's **reference snapshot** — where the shared arrangement is parked while the postures are being
 * edited independently.
 *
 * It holds nothing while the two postures are kept in step, because the reference *is* the portrait posture then;
 * writing a third copy of the same layout would be a row-set to keep in step for no gain. It is written once, when
 * independence is switched on, and read once, if the user later switches independence off and asks to keep neither
 * posture. That is the whole of its job, and it is what makes that third answer mean anything.
 */
val ArrangementKey.referenceSnapshot: ArrangementKey
    get() = when (this) {
        ArrangementKey.PHONE_PORTRAIT,
        ArrangementKey.PHONE_LANDSCAPE,
        ArrangementKey.PHONE_SHARED,
        -> ArrangementKey.PHONE_SHARED

        ArrangementKey.TABLET_PORTRAIT,
        ArrangementKey.TABLET_LANDSCAPE,
        ArrangementKey.TABLET_SHARED,
        -> ArrangementKey.TABLET_SHARED
    }

/**
 * The portrait posture of this key's form factor — the reference while the two are kept in step.
 *
 * Total where [portraitCounterpart] is nullable, because the caller here is asking "which posture is the source?"
 * rather than "does this one need seeding?", and every key has an answer to the first.
 */
val ArrangementKey.portraitOfFormFactor: ArrangementKey
    get() = when (this) {
        ArrangementKey.PHONE_PORTRAIT,
        ArrangementKey.PHONE_LANDSCAPE,
        ArrangementKey.PHONE_SHARED,
        -> ArrangementKey.PHONE_PORTRAIT

        ArrangementKey.TABLET_PORTRAIT,
        ArrangementKey.TABLET_LANDSCAPE,
        ArrangementKey.TABLET_SHARED,
        -> ArrangementKey.TABLET_PORTRAIT
    }

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
