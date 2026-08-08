package inkspire.morphic.core.designsystem.menu

import androidx.compose.ui.graphics.ImageBitmap

/**
 * One row of a context menu: a label, optionally an icon, and what happens when it is tapped.
 *
 * Deliberately a plain value rather than a composable slot, so the surfaces that build menus stay declarative
 * lists — a menu is "these verbs, in this order", and letting a caller supply arbitrary content is how a menu row
 * ends up a different shape on each surface, which is exactly what the one-contract rule exists to prevent.
 *
 * @param icon artwork belonging to whatever the row *represents*, drawn untinted — an app shortcut's own icon
 *   today, and nothing else. The launcher's own verbs carry no icon: "App info" and "Uninstall" are words, and a
 *   glyph beside each would compete with the one piece of real artwork in the menu. **An [ImageBitmap] rather than
 *   an `ImageVector`** because the only source is already rasterised, and because `core:designsystem` carries no
 *   material-icons dependency (see `TopActionZone`, which draws its three marks by hand for the same reason). L1
 *   carried both as two nullable fields of which one was ever meaningful.
 * @param enabled a disabled row is drawn muted and does not respond. For a verb that exists but cannot apply right
 *   now; it is **not** how to show an unbuilt feature — an action that does nothing is worse than one that is
 *   absent, the same rule the settings sections follow about controls.
 * @param onClick performed on tap. It does **not** have to close the menu: [ContextMenu] takes itself down after
 *   any action, so no caller can forget to and two callers cannot disagree about whether it should.
 */
data class MenuAction(
    val label: String,
    val icon: ImageBitmap? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)
