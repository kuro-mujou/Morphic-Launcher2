package inkspire.morphic.core.designsystem.menu

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import inkspire.morphic.core.designsystem.surface.LocalSurfaceGestureLock
import inkspire.morphic.core.model.ComponentKey

/**
 * One open menu: what it is about, where it is anchored, and what it offers.
 *
 * @property anchor an item's visible extent, or the point an empty-space long-press landed on. For an item it is
 *   reported by `launcherItemGestures` from the very node the finger was on, which is why no surface has to
 *   reconstruct it. Rebuilding that rectangle per surface — cell centers on a grid, an icon half-width plus a Y
 *   offset in a folder, a row's bounds in a list — is three chances to drift from what was drawn.
 * @property title the item's name, shown as a header. **Null for a surface menu**, which has no header at all —
 *   there is no honest title for "the home screen" that is not just a word taking up a row.
 * @property shortcuts loads the app's own shortcuts, or null when there are none to offer (a folder, a surface). A
 *   **suspending lambda rather than a loaded list**, so the menu itself owns the load. Running that `LaunchedEffect`
 *   per surface is three places for the two stages to disagree.
 */
data class MenuRequest(
    val anchor: MenuAnchor,
    val actions: List<MenuAction>,
    val title: String? = null,
    val shortcuts: (suspend () -> List<MenuAction>)? = null,
)

/**
 * Holds whichever menu is open — **one for the whole launcher**, provided by the shell through [LocalMenuHost] and
 * rendered above every surface by [MenuOverlay].
 *
 * **The menu is the launcher's, not a surface's**, for the same reason the drag coordinator and the top-action band
 * are: the verbs on it belong to the *item*, and the same item is reachable from home, from the drawer and from
 * inside a folder. Answering it per surface means writing the two stages, the loading state and the toggle twice.
 *
 * **One host for both kinds, because only one menu is ever open.** An item menu and a surface menu are the same
 * panel with different contents and different anchors ([MenuAnchor]); giving each its own holder would make "both
 * open at once" representable, and then a state nobody meant would need a rule to forbid it.
 *
 * **What a surface contributes is only what that surface owns.** The app verbs below (App info, Uninstall, and the
 * shortcuts stage) are identical everywhere, so they are built here once, from the commands the shell binds in;
 * home adds "Remove" because home is where an item is *placed*, and the drawer adds nothing because there is
 * nothing there to remove it from. The same rule gives every surface menu its Settings row.
 *
 * @param onAppInfo opens the system app-details screen.
 * @param onUninstall opens the system uninstall prompt.
 * @param loadShortcuts reads an app's own shortcuts, already turned into rows. Suspends; called once per menu.
 * @param onOpenSettings goes to the settings surface — the one verb every surface menu ends with.
 */
@Stable
class LauncherMenuHost(
    private val onAppInfo: (ComponentKey) -> Unit,
    private val onEditIcon: (ComponentKey) -> Unit,
    private val onUninstall: (ComponentKey) -> Unit,
    private val loadShortcuts: suspend (ComponentKey) -> List<MenuAction>,
    private val onOpenSettings: () -> Unit,
) {

    var request: MenuRequest? by mutableStateOf(null)
        private set

    /**
     * Opens the menu for an **app**: its shortcuts, then App info, Edit icon, [surfaceActions], and Uninstall.
     *
     * The middle is where a surface's own verbs go — after the ones that describe or
     * customize the app and before the one that destroys it, so the destructive row is never the one a mis-tap
     * lands on next to "Remove". **Edit icon sits beside App info** because both are about the app itself wherever
     * it is showing, unlike a surface's verbs, which are about this particular placement of it.
     */
    fun showApp(
        component: ComponentKey,
        label: String,
        anchor: Rect,
        surfaceActions: List<MenuAction> = emptyList(),
    ) {
        request = MenuRequest(
            anchor = MenuAnchor.Item(anchor),
            title = label,
            actions = buildList {
                add(MenuAction("App info") { onAppInfo(component) })
                add(MenuAction("Edit icon") { onEditIcon(component) })
                addAll(surfaceActions)
                add(MenuAction("Uninstall") { onUninstall(component) })
            },
            shortcuts = { loadShortcuts(component) },
        )
    }

    /**
     * Opens the menu for anything that is **not** an app — a folder today. One stage, since there are no shortcuts
     * to offer, so the header shows no toggle.
     */
    fun show(title: String, anchor: Rect, actions: List<MenuAction>) {
        request = MenuRequest(anchor = MenuAnchor.Item(anchor), title = title, actions = actions)
    }

    /**
     * Opens the **surface** menu: a long-press that landed on empty space at [position], in root coordinates.
     *
     * [surfaceActions] are whatever that surface can do to itself, and Settings is appended. **Today that is the
     * whole menu**, because every verb that would sit above it waits on something not yet built: "Add app" and
     * "Widgets" need pickers, and "Remove page" needs page management. Each returns as its feature lands, which is
     * also why they are absent rather than disabled — a row that does nothing is worse than a row that is not there.
     *
     * There is no title: see [MenuRequest.title].
     */
    fun showSurface(position: Offset, surfaceActions: List<MenuAction> = emptyList()) {
        request = MenuRequest(
            anchor = MenuAnchor.Press(position),
            actions = surfaceActions + MenuAction("Settings", onClick = onOpenSettings),
        )
    }

    /** Takes the menu down. Called when an action runs, when the user taps away, and when a drag begins. */
    fun dismiss() {
        request = null
    }
}

/**
 * The launcher's menu host. Null outside the launcher shell — settings has no menu, and a null here is what says so
 * rather than a second host quietly appearing.
 */
val LocalMenuHost = staticCompositionLocalOf<LauncherMenuHost?> { null }

/**
 * Renders whichever menu [host] is holding. Emitted by the shell as a sibling above the surface pager, so a menu
 * opened on an item in the drawer is drawn over the drawer and one opened on home over home — without either
 * surface owning a menu of its own.
 *
 * **While a menu is open the surface swipe is locked.** A context menu is modal: it is dismissed by tapping away,
 * so a pan that slid the surface out from under it would leave the menu describing an item that is no longer
 * there. The same [LocalSurfaceGestureLock] the item gesture holds while the finger is down, taken here for the
 * span the menu is up — and the lock counts, so the overlap between the two is not a conflict.
 */
@Composable
fun MenuOverlay(host: LauncherMenuHost) {
    val request = host.request ?: return

    // Back takes the menu down before anything else answers. It is composed above every surface, and back handlers
    // run most-recent-first, so this beats the open folder's — which is right: the menu is what arrived last, and
    // dismissing the folder underneath would leave the menu anchored to an item that is no longer on screen.
    BackHandler(onBack = host::dismiss)

    val lock = LocalSurfaceGestureLock.current
    DisposableEffect(lock) {
        lock?.acquire()
        onDispose { lock?.release() }
    }
    // Keyed on the request so opening a menu on a different item starts its own two-stage state and its own
    // entrance, rather than inheriting the previous item's stage — `AppCollectionOverlay`'s `key(folderId)` rule.
    key(request) {
        RequestedMenu(request = request, onDismiss = host::dismiss)
    }
}

/**
 * Renders a [MenuRequest] — which for an app is the **two-stage** menu: its own shortcuts first, the launcher's
 * actions behind a toggle.
 *
 * That order is the right way round: the shortcuts are what the *app* offers and are what a user
 * long-presses an icon for most often ("New message"), while App info and Uninstall are things done *to* the app
 * and are rarer. Anything with no shortcuts — a folder, a surface, an app that publishes none — skips straight to
 * the actions and shows no toggle, so the second stage never announces itself as missing. A request with no title
 * (a surface menu) shows no header at all, so that case falls out of the same code rather than needing its own.
 */
@Composable
private fun RequestedMenu(request: MenuRequest, onDismiss: () -> Unit) {
    val loader = request.shortcuts
    // Null means "still asking"; an empty list means "asked, and there are none". The distinction is the whole
    // difference between showing a placeholder row and collapsing to one stage.
    val shortcuts by produceState<List<MenuAction>?>(initialValue = null, key1 = loader) {
        value = loader?.invoke() ?: emptyList()
    }

    var showActions by remember { mutableStateOf(loader == null) }
    // An app that publishes no shortcuts would otherwise sit on an empty first stage until the user found the
    // toggle. Runs on the answer arriving, not on composition, because that is when the answer is known.
    LaunchedEffect(shortcuts) {
        if (shortcuts?.isEmpty() == true) showActions = true
    }

    val hasShortcutStage = loader != null && shortcuts?.isEmpty() != true
    val displayed = when {
        showActions -> request.actions
        shortcuts == null -> listOf(LoadingRow)
        else -> shortcuts.orEmpty()
    }

    val title = request.title
    ContextMenu(
        anchor = request.anchor,
        actions = displayed,
        onDismiss = onDismiss,
        header = title?.let {
            {
                MenuHeader(
                    title = it,
                    stage = when {
                        !hasShortcutStage -> null
                        showActions -> MenuStage.BACK
                        else -> MenuStage.FORWARD
                    },
                    onToggle = { showActions = !showActions },
                )
            }
        },
    )
}

/** Stands in for the shortcuts while they are being read. Disabled, so it cannot be tapped, and does nothing. */
private val LoadingRow = MenuAction(label = "Loading…", enabled = false, onClick = {})
