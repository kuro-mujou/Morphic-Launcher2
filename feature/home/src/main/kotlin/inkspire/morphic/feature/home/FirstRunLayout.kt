package inkspire.morphic.feature.home

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.data.apps.role.AppRole
import inkspire.morphic.data.layout.LayoutChange

/**
 * **What HOME holds the first time it is drawn** — the launcher's one chance to look like somebody arranged it.
 *
 * Every entry is an [AppRole], never a package: the roles are resolved against the device at seed time, so the dock
 * holds *this* phone's dialer and *this* user's browser. The shape follows what every stock launcher converges on —
 * AOSP's `default_workspace.xml`, Pixel, One UI: **the dock is reach** (call, text, web, camera, the four things
 * done from a standing start) **and the first page is everything else worth having without opening the drawer**.
 *
 * **It is deliberately short, and it sits at the bottom.** Nine icons on a page that holds twenty is the point: this
 * launcher has a whole APPS surface for the rest, and a page seeded to the brim is one the user must clear before it
 * is theirs. Where that spare room goes is the other half — it is left at the **top**, against the status bar rather
 * than the dock, because the top of a home page is where a widget belongs and a seed that filled downwards would put
 * nine icons exactly where the first one wants to go. The spare room is what the free-placement push engine needs
 * too: a full page cannot be rearranged, since pushing an occupant needs somewhere for it to go.
 *
 * A role no device answers is simply skipped and the rest close up behind it, so the result never has holes.
 */
internal object FirstRunLayout {

    /**
     * The dock, in reading order — left to right on a strip, top to bottom on a rail.
     *
     * Four because that is the phone dock's default column count, and because these four are the ones AOSP and Pixel
     * agree on. Contacts is the notable omission: on every current device the dialer *is* the contacts app.
     */
    val Dock: List<AppRole> = listOf(AppRole.PHONE, AppRole.MESSAGING, AppRole.BROWSER, AppRole.CAMERA)

    /**
     * The main area, in reading order.
     *
     * Ordered by how often a first-time user reaches for it, not alphabetically: the store first because a new phone
     * is mostly about installing things, then the personal apps, then the utilities, with the system settings last
     * because it is the one the user can always find another way.
     */
    val Main: List<AppRole> = listOf(
        AppRole.STORE,
        AppRole.EMAIL,
        AppRole.GALLERY,
        AppRole.CLOCK,
        AppRole.CALENDAR,
        AppRole.CALCULATOR,
        AppRole.MAPS,
        AppRole.MUSIC,
        AppRole.SETTINGS,
    )

    /** Every role this layout asks about — what to hand `DefaultAppRoles.resolve`. */
    val Roles: List<AppRole> = Dock + Main

    /**
     * Lays the [resolved] roles into the two zones' grids, one app per **visual** cell.
     *
     * **De-duplicated across both zones, dock first.** One app routinely answers two roles — the camera is usually the
     * gallery, the dialer is sometimes the messenger — and the same app placed twice is not two icons but one row
     * overwriting another. Dock wins because it is the smaller, more deliberate list.
     *
     * **The main area sits on the bottom of the page, against the dock, and the free space is at the top.** That is
     * where a widget goes — the thing you look at above the things you reach for — so a seed that filled downwards
     * would put the icons exactly where the first widget wants to be and make the user move all nine. It also leaves
     * the push engine its slack, which is why the main area is still capped a visual row short of the page: one
     * purpose, one empty row, at the end that is useful.
     *
     * The dock is allowed to fill, since its one free cell is the dragged item's own and a strip only ever pushes
     * along itself.
     *
     * Coordinates are scaled by each grid's `cellMultiplier` so what is stored is in logical space, leaving room for
     * a future sub-cell item to sit between two icons without a migration.
     */
    fun plan(resolved: Map<AppRole, ComponentKey>, dock: GridConfig, main: GridConfig): List<LayoutChange.Move> {
        val inDock = Dock.mapNotNull(resolved::get).distinct().take(dock.visualRows * dock.visualCols)
        val seedRows = (main.visualRows - 1).coerceAtLeast(1)
        val inMain = Main.mapNotNull(resolved::get)
            .distinct()
            .filterNot { it in inDock }
            .take(seedRows * main.visualCols)
        val mainRows = stackedRows(inMain, main.visualCols)
        return place(inDock.chunked(dock.visualCols), topRow = 0, config = dock, zone = HomeZone.DOCK) +
            place(mainRows, topRow = main.visualRows - mainRows.size, config = main, zone = HomeZone.MAIN)
    }

    /**
     * [components] split into rows of [cols] with the **short row first**, which is what makes a bottom-aligned block
     * read as a stack that grew up off the dock rather than a list that ran out.
     *
     * Plain `chunked` leaves the remainder last, so nine apps over four columns would strand a single icon on the row
     * directly above the dock with a full row above it. The same nine here are 1 + 4 + 4, top-down.
     */
    private fun stackedRows(components: List<ComponentKey>, cols: Int): List<List<ComponentKey>> {
        val remainder = components.size % cols
        val head = components.take(remainder)
        val full = components.drop(remainder).chunked(cols)
        return if (head.isEmpty()) full else listOf(head) + full
    }

    /** [rows] into [config]'s visual cells, one app per cell, the first row landing on visual row [topRow]. */
    private fun place(
        rows: List<List<ComponentKey>>,
        topRow: Int,
        config: GridConfig,
        zone: HomeZone,
    ): List<LayoutChange.Move> {
        val mult = config.cellMultiplier
        return rows.flatMapIndexed { row, components ->
            components.mapIndexed { col, component ->
                LayoutChange.Move(
                    item = GridItem.App(component),
                    to = GridPlacement(
                        page = 0,
                        row = (topRow + row) * mult,
                        col = col * mult,
                        rowSpan = mult,
                        colSpan = mult,
                    ),
                    zone = zone,
                )
            }
        }
    }
}
