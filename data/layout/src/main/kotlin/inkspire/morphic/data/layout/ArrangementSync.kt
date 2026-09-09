package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.portraitOfFormFactor
import kotlinx.coroutines.flow.first

/**
 * Carrying one HOME arrangement's items into another posture's rows.
 *
 * **One operation behind four callers**, which is why it lives here rather than private to the surface that needed
 * it first: a posture with nothing seeds itself from portrait, a posture drawn while the two are kept in step
 * re-derives on entry, a drag made away from portrait writes back into it, and the independence toggle copies in
 * whichever direction the user picked. All four are "take that arrangement, lay it out here".
 *
 * **Extensions rather than an injectable class**, for `SettingsRepository.homeZoneGrids`' reason: these compose
 * what [LayoutRepository] already offers and hold no state of their own, so an object would be a dependency every
 * caller carries for nothing — and it pushed `HomeViewModel` past detekt's parameter bound to make the point.
 *
 * **Grids arrive as a parameter rather than being read.** They belong to `data:settings`, which this module does
 * not depend on and should not: placement arithmetic has no business resolving a user's grid size. The caller
 * already holds them for its own drawing, so passing them costs nothing and keeps the dependency out.
 */

/**
 * Replaces [into]'s arrangement with [from]'s, each zone re-laid against its own grid in [configs].
 *
 * **A replace, not a merge**, because the two postures are meant to agree afterwards: leaving whatever [into]
 * already held would keep items the source no longer has. See [LayoutRepository.replacePlacements] for what that
 * costs an item the source never had.
 *
 * **Every zone into its own grid.** A dock is a separate coordinate space from the main area, so projecting them
 * together would pack dock items into home cells. Driven by [HomeZone.entries] with `getValue`, so a zone missing
 * from [configs] fails loudly rather than having its items silently dropped.
 *
 * Does nothing when [from] holds nothing — an empty source would otherwise *clear* the target, which is a plausible
 * reading of "make them agree" and a terrible one to arrive at by accident on a first run.
 *
 * @return whether it wrote, so a caller can fall back to whatever it does for a posture with no source.
 */
suspend fun LayoutRepository.copyArrangement(
    from: ArrangementKey,
    into: ArrangementKey,
    configs: Map<HomeZone, GridConfig>,
): Boolean {
    if (from == into) return false
    val source = placements(from).first()
    if (source.isEmpty()) return false

    val projected = HomeZone.entries.flatMap { zone ->
        val inZone = source.filterValues { it.zone == zone }.mapValues { it.value.placement }
        ArrangementProjection.project(inZone, configs.getValue(zone))
            .map { (item, at) -> item to PlacedItem(at, zone) }
    }
    // **Skipped when it would change nothing**, which matters because the re-derive on entry is unconditional:
    // without this, every configuration change rewrites the whole target and Room re-emits a map identical to the
    // one already on screen. Correct either way, but a write per rotation for no reason.
    val next = projected.toMap()
    if (next == placements(into).first()) return false
    replacePlacements(into, next)
    return true
}

/**
 * Copies [from]'s placements into [into] **unchanged** — no projection, no re-lay.
 *
 * The reference snapshot, and the one carry that must *not* project: source and target describe the same grid, and
 * running them through [ArrangementProjection] would close the gaps the user deliberately left. A snapshot that
 * quietly tidies the layout it is preserving is not a snapshot.
 *
 * @return whether it wrote, false when [from] holds nothing.
 */
suspend fun LayoutRepository.snapshotArrangement(from: ArrangementKey, into: ArrangementKey): Boolean {
    if (from == into) return false
    val source = placements(from).first()
    if (source.isEmpty()) return false
    replacePlacements(into, source)
    return true
}

/**
 * Carries an edit made away from the reference posture back into it, while the two are kept in step.
 *
 * **Without this the edit does not survive the next rotation**: the re-derive on entry rebuilds a non-reference
 * posture from portrait, so anything portrait never heard about is overwritten the moment the device turns twice.
 *
 * **Extracted on its second caller.** It began private to `HomeViewModel`, where every drag goes; the remove band
 * is the second, and it lives on `ShellViewModel` because it spans every surface. A near-copy there would be two
 * implementations of "make portrait agree" that could drift, which is exactly the divergence this codebase keeps
 * paying for.
 *
 * [referenceGrids] is a lambda rather than a value because most calls return before needing it — resolving the
 * reference posture's grids costs a store read that an independent layout, or an edit made *in* portrait, never has
 * any use for.
 *
 * @return whether it wrote.
 */
suspend fun LayoutRepository.writeBackToReference(
    from: ArrangementKey,
    independent: Boolean,
    referenceGrids: suspend () -> Map<HomeZone, GridConfig>,
): Boolean {
    if (independent) return false
    val reference = from.portraitOfFormFactor
    if (from == reference) return false
    return copyArrangement(from, reference, referenceGrids())
}

/**
 * [copyArrangement], but only when [into] holds nothing at all — the seed rather than the sync.
 *
 * Separate rather than a flag on it, because the guard is the whole difference between "this posture has never been
 * used" and "these two are kept in step", and a boolean at the call site would not say which the caller meant.
 *
 * @return whether it wrote, so a caller can skip whatever it would otherwise fall back to.
 */
suspend fun LayoutRepository.copyArrangementIfEmpty(
    from: ArrangementKey,
    into: ArrangementKey,
    configs: Map<HomeZone, GridConfig>,
): Boolean {
    if (placements(into).first().isNotEmpty()) return false
    return copyArrangement(from, into, configs)
}
