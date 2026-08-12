package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.IconItem

/**
 * The APPS pager's arrangement as pure list maths: **pages of entries**, each page dense from its first slot.
 *
 * This is the ordered counterpart of `FreeGridPlanner`/`FreePush` — the engine half of the store, with no Room, no
 * coroutines and no Compose in it, so every rule below is unit-testable on plain lists.
 *
 * **The shape carries the invariants.** `List<List<IconItem>>` makes "dense within a page, with a gap only at that
 * page's end" structural: a short page *is* a trailing gap, and there is no way to express a hole in the middle.
 * Callers therefore never have to re-check it, which is the same reason home's placements are a map rather than a
 * grid of nullables.
 *
 * **Pages are hard boundaries.** Removing an entry compacts only the page it was on — nothing is pulled back from
 * later pages to fill the hole, so a page the user emptied stays roomy instead of resucking the next page's first
 * app every time. Adding one that overflows cascades the surplus to the **front** of the next page, preserving
 * overall order.
 */

/** Every entry in reading order, pages concatenated — the pager flattened back to one list. */
internal fun List<List<IconItem>>.flatItems(): List<IconItem> = flatten()

/**
 * Enforces the page capacity: any page longer than [perPage] sheds its surplus onto the **front** of the next page
 * (creating one if needed), repeatedly, and empty pages are dropped.
 *
 * Applied on every read as well as every write, so the stored rows can never present the UI with a page it cannot
 * render — the capacity is a function of the device and the (future) column setting, and so can change underneath
 * a list that was saved at a different width. That makes this the same code path a future rotate would use to
 * seed the second orientation, which is why it is written as "re-fit these pages" rather than "fix a bad write".
 *
 * **Empty pages are dropped, including middle ones.** A page with nothing on it is not a place: the user cannot
 * scroll past it meaningfully and cannot delete it except by dragging something in and out again. Hard boundaries
 * are about moves not disturbing neighbors, not about preserving blanks.
 */
internal fun normalizePages(pages: List<List<IconItem>>, perPage: Int): List<List<IconItem>> {
    require(perPage > 0) { "perPage must be > 0, got $perPage" }
    val out = mutableListOf<MutableList<IconItem>>()
    pages.forEach { out += it.toMutableList() }
    var index = 0
    while (index < out.size) {
        val page = out[index]
        if (page.size > perPage) {
            val surplus = page.subList(perPage, page.size).toList()
            repeat(surplus.size) { page.removeAt(page.size - 1) }
            if (index + 1 == out.size) out += mutableListOf<IconItem>()
            out[index + 1].addAll(0, surplus)
        }
        index++
    }
    return out.filter { it.isNotEmpty() }
}

/** Removes [item] wherever it sits, compacting only its own page. Unchanged when it isn't there. */
internal fun removePagerItem(pages: List<List<IconItem>>, item: IconItem): List<List<IconItem>> =
    pages.map { page -> page.filterNot { it == item } }

/**
 * Inserts [item] at [slot] of [page], cascading any overflow forward.
 *
 * [page] past the last one appends pages until it exists — that is a drop onto the trailing empty page the UI
 * offers mid-drag. [slot] is clamped to the target page's length, so a drop past the last icon appends.
 */
internal fun insertPagerItem(
    pages: List<List<IconItem>>,
    item: IconItem,
    page: Int,
    slot: Int,
    perPage: Int,
): List<List<IconItem>> {
    val target = page.coerceAtLeast(0)
    val out = pages.map { it.toMutableList() }.toMutableList()
    while (out.size <= target) out += mutableListOf<IconItem>()
    val destination = out[target]
    destination.add(slot.coerceIn(0, destination.size), item)
    return normalizePages(out, perPage)
}

/**
 * Moves [item] to [toSlot] of [toPage] — the reorder-and-cross-page op behind [AppsPagerChange.Move].
 *
 * Removal happens first, so a move *within* a page is measured against the list minus the dragged item, which is
 * what the drag layer's MovingGap already previews: the gap the user sees is an index into the others, not into
 * the list including the thing they are holding.
 */
internal fun movePagerItem(
    pages: List<List<IconItem>>,
    item: IconItem,
    toPage: Int,
    toSlot: Int,
    perPage: Int,
): List<List<IconItem>> = insertPagerItem(removePagerItem(pages, item), item, toPage, toSlot, perPage)

/** Replaces [old] with [new] in place, keeping its exact page and slot. Unchanged when [old] isn't there. */
internal fun replacePagerItem(
    pages: List<List<IconItem>>,
    old: IconItem,
    new: IconItem,
): List<List<IconItem>> = pages.map { page -> page.map { if (it == old) new else it } }

/** Appends [items] after the last entry, filling the final page before starting new ones. */
internal fun appendPagerItems(
    pages: List<List<IconItem>>,
    items: List<IconItem>,
    perPage: Int,
): List<List<IconItem>> {
    if (items.isEmpty()) return pages
    val out = pages.map { it.toMutableList() }.toMutableList()
    if (out.isEmpty()) out += mutableListOf<IconItem>()
    items.forEach { item ->
        if (out.last().size >= perPage) out += mutableListOf<IconItem>()
        out.last() += item
    }
    return normalizePages(out, perPage)
}

/**
 * Reconciles the arrangement with what is actually installed: drops entries for apps that are gone, appends apps
 * that are new, and leaves everything else exactly where the user put it.
 *
 * This is also the **first-run seed** — with no stored pages, every installed app is "new", so the same code path
 * lays out the whole list. One path rather than a separate `seedIfEmpty` because the two only differ in how much
 * is missing, and a separate seed is where the two would drift apart.
 *
 * @param installed every installed app **in the order they should appear** when appended (the caller's A–Z, so
 *   locale-aware collation is decided once, in the ViewModel, rather than re-derived here).
 *
 * Folders are never pruned here: a folder is not an app, and its *contents* going stale is `folder_item`'s
 * business (B6's pruning), not the pager's.
 */
internal fun syncPagerPages(
    pages: List<List<IconItem>>,
    installed: List<ComponentKey>,
    perPage: Int,
): List<List<IconItem>> {
    val live = installed.toSet()
    val pruned = pages.map { page ->
        page.filter { it !is IconItem.App || it.component in live }
    }
    val present = pruned.flatItems().filterIsInstance<IconItem.App>().mapTo(mutableSetOf()) { it.component }
    val added = installed.filterNot { it in present }.map { IconItem.App(it) }
    return normalizePages(appendPagerItems(pruned, added, perPage), perPage)
}

/** The page and slot [item] sits at, or null when it isn't in [pages]. */
internal fun locatePagerItem(pages: List<List<IconItem>>, item: IconItem): PagerSlot? {
    pages.forEachIndexed { page, entries ->
        val slot = entries.indexOf(item)
        if (slot >= 0) return PagerSlot(page, slot)
    }
    return null
}

/** Where an entry sits: its [page] and its [slot] within that page. */
internal data class PagerSlot(val page: Int, val slot: Int)
