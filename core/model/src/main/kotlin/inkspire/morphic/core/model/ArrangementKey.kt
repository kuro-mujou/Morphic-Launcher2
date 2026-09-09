package inkspire.morphic.core.model

/**
 * Which stored arrangement — *which item sits where* — a surface reads and writes.
 *
 * Three axes, all of them real: **form factor** × **orientation** × **mode**. The first two say which screen is
 * being drawn; the third says whether the user is arranging that screen on its own or keeping it in step with the
 * other orientation.
 *
 * Deliberately not [DeviceConfiguration], which keys *configuration*: how big a grid is, how large its icons are.
 * A configuration has no mode, which is exactly why these are two types rather than one with four values.
 *
 * **The mode is an axis, so the two modes never share a row.** That is what makes switching `independentLayout` a
 * choice of which pair to read rather than a merge of two layouts into one — and a merge is a question with no
 * good answer, since every way of resolving it discards a layout the user made.
 *
 * **`LINKED` and not `SHARED`:** these are two row-sets kept in step with each other, not one row-set two postures
 * read. A linked landscape holds coordinates of its own because the surface draws from them and a drop lands in
 * them, which is why it cannot be derived at render time. Full model in `docs/LANDSCAPE_PLAN.md`.
 *
 * These names are **persisted** — they key the five `*_placement` tables and `apps_pager_item` — so renaming a
 * value is a storage migration.
 */
enum class ArrangementKey {
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE,
    PHONE_PORTRAIT_LINKED,
    PHONE_LANDSCAPE_LINKED,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE,
    TABLET_PORTRAIT_LINKED,
    TABLET_LANDSCAPE_LINKED,
}

/**
 * The arrangement this configuration reads and writes, given whether the user keeps the two orientations apart.
 *
 * **The one bridge between the two keys**, and every surface goes through it — six call sites. Taking
 * `independentLayout` as a parameter rather than reading it here is what keeps `core:model` free of the settings
 * layer, and it makes the dependency visible at each call: a surface that resolves a key without the flag is a
 * surface writing into the wrong mode.
 */
fun DeviceConfiguration.arrangementKey(independentLayout: Boolean): ArrangementKey = when (this) {
    DeviceConfiguration.PHONE_PORTRAIT ->
        if (independentLayout) ArrangementKey.PHONE_PORTRAIT else ArrangementKey.PHONE_PORTRAIT_LINKED

    DeviceConfiguration.PHONE_LANDSCAPE ->
        if (independentLayout) ArrangementKey.PHONE_LANDSCAPE else ArrangementKey.PHONE_LANDSCAPE_LINKED

    DeviceConfiguration.TABLET_PORTRAIT ->
        if (independentLayout) ArrangementKey.TABLET_PORTRAIT else ArrangementKey.TABLET_PORTRAIT_LINKED

    DeviceConfiguration.TABLET_LANDSCAPE ->
        if (independentLayout) ArrangementKey.TABLET_LANDSCAPE else ArrangementKey.TABLET_LANDSCAPE_LINKED
}

/** True for the four arrangements whose orientation is kept in step with its partner. */
val ArrangementKey.isLinked: Boolean
    get() = this == ArrangementKey.PHONE_PORTRAIT_LINKED ||
        this == ArrangementKey.PHONE_LANDSCAPE_LINKED ||
        this == ArrangementKey.TABLET_PORTRAIT_LINKED ||
        this == ArrangementKey.TABLET_LANDSCAPE_LINKED

/** True for the four arrangements drawn on a screen turned on its side, in either mode. */
val ArrangementKey.isLandscape: Boolean
    get() = this == ArrangementKey.PHONE_LANDSCAPE ||
        this == ArrangementKey.PHONE_LANDSCAPE_LINKED ||
        this == ArrangementKey.TABLET_LANDSCAPE ||
        this == ArrangementKey.TABLET_LANDSCAPE_LINKED

/**
 * The portrait arrangement **of this key's own pair** — same form factor, same mode.
 *
 * The reference a linked landscape re-derives from and writes back into, and the source an independent landscape
 * is seeded from. **Same mode is the load-bearing half**: answering `PHONE_PORTRAIT` for a linked landscape would
 * have the shared layout rebuilt out of a layout the user is arranging separately, which is precisely the leak the
 * mode axis was added to close. It is also the only place in the launcher where the two modes could still meet.
 */
val ArrangementKey.portraitOfPair: ArrangementKey
    get() = when (this) {
        ArrangementKey.PHONE_PORTRAIT, ArrangementKey.PHONE_LANDSCAPE -> ArrangementKey.PHONE_PORTRAIT
        ArrangementKey.PHONE_PORTRAIT_LINKED, ArrangementKey.PHONE_LANDSCAPE_LINKED ->
            ArrangementKey.PHONE_PORTRAIT_LINKED

        ArrangementKey.TABLET_PORTRAIT, ArrangementKey.TABLET_LANDSCAPE -> ArrangementKey.TABLET_PORTRAIT
        ArrangementKey.TABLET_PORTRAIT_LINKED, ArrangementKey.TABLET_LANDSCAPE_LINKED ->
            ArrangementKey.TABLET_PORTRAIT_LINKED
    }

/**
 * The **same posture's** arrangement in linked mode, or null when this key already is one.
 *
 * What an independent arrangement is seeded from the first time the user flips the toggle, so the switch shows the
 * layout they were just looking at rather than the alphabet. Same posture means the same grid, which is why that
 * seed is a verbatim copy rather than a projection — see `LayoutRepository.mirrorArrangementIfEmpty`.
 *
 * One direction only, and there is no inverse: the linked pair is where a launcher starts, so it always has
 * something and never needs seeding from anywhere.
 */
val ArrangementKey.linkedCounterpart: ArrangementKey?
    get() = when (this) {
        ArrangementKey.PHONE_PORTRAIT -> ArrangementKey.PHONE_PORTRAIT_LINKED
        ArrangementKey.PHONE_LANDSCAPE -> ArrangementKey.PHONE_LANDSCAPE_LINKED
        ArrangementKey.TABLET_PORTRAIT -> ArrangementKey.TABLET_PORTRAIT_LINKED
        ArrangementKey.TABLET_LANDSCAPE -> ArrangementKey.TABLET_LANDSCAPE_LINKED
        ArrangementKey.PHONE_PORTRAIT_LINKED,
        ArrangementKey.PHONE_LANDSCAPE_LINKED,
        ArrangementKey.TABLET_PORTRAIT_LINKED,
        ArrangementKey.TABLET_LANDSCAPE_LINKED,
        -> null
    }
