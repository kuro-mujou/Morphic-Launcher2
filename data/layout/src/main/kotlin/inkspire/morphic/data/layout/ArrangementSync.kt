package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.isLinked
import inkspire.morphic.core.model.portraitOfPair
import kotlinx.coroutines.flow.first

/**
 * Carrying one HOME arrangement's items into another posture's rows.
 *
 * **One operation behind four callers**, which is why it lives here rather than private to the surface that needed
 * it first: a posture with nothing seeds itself from its pair's portrait, a linked posture re-derives on entry, a
 * drag made away from the reference writes back into it, and an independent posture first used is mirrored from its
 * linked twin. All four are "take that arrangement, put it here" — three re-laying it, one not.
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
 * Copies [from]'s placements into [into] **unchanged** — no projection, no re-lay — and only when [into] is empty.
 *
 * **The mode seed.** Flipping `independentLayout` on for the first time finds the independent pair empty; without
 * this the user would be handed the alphabet instead of the layout they were looking at a moment earlier. Source
 * and target are the *same posture* in the two modes, so they describe the same grid — which is why this must not
 * project: running it through [ArrangementProjection] would close the gaps the user deliberately left, and a seed
 * that tidies the layout it is preserving is not one.
 *
 * That same-grid fact is the whole difference from [copyArrangementIfEmpty], which crosses orientations and so has
 * no choice but to re-lay. Both are guarded on emptiness for [copyArrangementIfEmpty]'s reason: it is what makes
 * them safe to call on every configuration change rather than exactly once, and what stops a seed undoing work.
 *
 * @return whether it wrote, false when [into] already holds something or [from] holds nothing.
 */
suspend fun LayoutRepository.mirrorArrangementIfEmpty(from: ArrangementKey, into: ArrangementKey): Boolean {
    if (from == into) return false
    if (placements(into).first().isNotEmpty()) return false
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
 * **Whether it applies is read off the key, not passed in.** An independent arrangement has no reference to write
 * back to, and the key already says which mode it belongs to — so a caller cannot get the two out of step by
 * handing over the wrong flag.
 *
 * [referenceGrids] is a lambda rather than a value because most calls return before needing it — resolving the
 * reference posture's grids costs a store read that an independent layout, or an edit made *in* portrait, never has
 * any use for.
 *
 * @return whether it wrote.
 */
suspend fun LayoutRepository.writeBackToReference(
    from: ArrangementKey,
    referenceGrids: suspend () -> Map<HomeZone, GridConfig>,
): Boolean {
    if (!from.isLinked) return false
    val reference = from.portraitOfPair
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
