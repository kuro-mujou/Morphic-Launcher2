package inkspire.morphic.feature.settings.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.apps.ConfigurableLayouts
import inkspire.morphic.feature.settings.label
import inkspire.morphic.feature.settings.meta

/** The long side of a register card — L1's `RegisterLongSide`, unchanged; the short side follows the screen's shape. */
private val CardLongSide = 176.dp

/** Floors on the short side, so an extreme window still leaves a card something to draw. L1's pair. */
private val MinCardWidth = 88.dp
private val MinCardHeight = 120.dp

/** The shapes a register card will take, whatever the window is — L1's clamp, and `GridEditor` clamps its own. */
private const val MIN_RATIO = 0.4f
private const val MAX_RATIO = 2.4f

private val CardGap = 8.dp
private val CardCorner = 16.dp
private val IconLabelGap = 6.dp

/** L1's gear: a 32dp target holding an 18dp glyph, small enough not to compete with the card it sits in. */
private val GearTarget = 32.dp
private val GearGlyph = 18.dp

/**
 * **The surface register as a cross: HOME in the middle, and the four edges around it.**
 *
 * The port of L1's `SurfaceRegister`, and the reason this section is worth a component rather than a list of controls:
 * the setting *is* spatial. Which edge opens what is a fact about where things are, and the four labelled chip groups
 * this replaced made the reader rebuild that arrangement in their head — where five cards laid out in a plus simply
 * are it.
 *
 * **A card names what is bound; it does not draw it.** An earlier cut filled each card with a mockup of the layout,
 * on the grounds that the card is already screen-shaped and this feature owns those drawings. It was reversed: at 88dp
 * a mockup is a smudge, five of them at once turn a picker into a wall of texture, and what the reader is actually
 * scanning for here is *which edge has something on it* — which an icon and a name answer instantly. The picture
 * belongs where a layout is being chosen (`SideBindingPicker`) and where one is being sized (the APPS section's
 * editor), not where four of them are being placed.
 *
 * **The card is the shape of this device**, from `usableWindowArea` rather than L1's `LocalConfiguration` — the one
 * place this launcher measures the screen, and the same input `GridEditor` sizes its own mockup from. The long side is
 * a fixed dp for the reason that editor's is: a mockup of a screen wants a legible size, which is a physical decision,
 * and the other side then follows from the ratio.
 *
 * **HOME is a choice too, now that there are two of it.** Its card takes a tap like the four around it, and for the
 * same reason theirs do: the body *changes what is there* and the gear *configures what is there already*. That
 * reverses an earlier note here ("HOME is not a choice, so its card does not take a tap — L1's rule"), which was true
 * only while `PAGER_WITH_DOCK` was the only pairing — L1's own centre card was inert for exactly that reason, and it
 * put its two-way choice in the Home *section* instead. Here the register is where "what is HOME" is answered, which
 * is what this section's own KDoc had reserved the spot for.
 *
 * @param homeLayout HOME's current pairing; the centre card is named for it.
 * @param bindings the register's current per-edge bindings; a missing edge is unbound.
 * @param onPick opens the slot picker for an edge. The picker itself is hoisted to the section, so at most one is ever
 *   composed whatever the cross is doing.
 * @param onOpenSettings jumps to a section — and, for a side, to the layout bound there. L1's gear: place a surface,
 *   then size it without going back out through the list. The layout goes with it because the APPS section configures
 *   **one** layout at a time, so landing on the section alone would open whichever one happened to be selected.
 */
@Composable
internal fun SurfaceRegisterCross(
    homeLayout: HomeLayout,
    bindings: Map<HomeEdge, SideBinding>,
    onPick: (HomeEdge) -> Unit,
    onPickHomeLayout: () -> Unit,
    onOpenSettings: (SettingsSection, AppsLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = usableWindowArea(uiInsets)
    val ratio = (window.heightDp / window.widthDp.coerceAtLeast(1f)).coerceIn(MIN_RATIO, MAX_RATIO)
    val cardWidth: Dp
    val cardHeight: Dp
    if (ratio >= 1f) {
        cardHeight = CardLongSide
        cardWidth = (CardLongSide / ratio).coerceAtLeast(MinCardWidth)
    } else {
        cardWidth = CardLongSide
        cardHeight = (CardLongSide * ratio).coerceAtLeast(MinCardHeight)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SideSlot(HomeEdge.TOP, homeLayout, bindings, cardWidth, cardHeight, onPick, onOpenSettings)
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
            SideSlot(HomeEdge.LEFT, homeLayout, bindings, cardWidth, cardHeight, onPick, onOpenSettings)
            HomeSlot(homeLayout, cardWidth, cardHeight, onPickHomeLayout, onOpenSettings)
            SideSlot(HomeEdge.RIGHT, homeLayout, bindings, cardWidth, cardHeight, onPick, onOpenSettings)
        }
        SideSlot(HomeEdge.BOTTOM, homeLayout, bindings, cardWidth, cardHeight, onPick, onOpenSettings)
    }
}

/** One edge: the layout bound to it, or an empty slot inviting one. */
@Composable
private fun SideSlot(
    edge: HomeEdge,
    homeLayout: HomeLayout,
    bindings: Map<HomeEdge, SideBinding>,
    width: Dp,
    height: Dp,
    onPick: (HomeEdge) -> Unit,
    onOpenSettings: (SettingsSection, AppsLayout?) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val bound = bindings[edge] as? SideBinding.Apps

    if (bound == null) {
        EmptySlot(width, height) { onPick(edge) }
    } else {
        // The same glyph for every layout, because every binding is the same *surface* — the label carries which
        // arrangement of it. L1 varied the icon because its two side surfaces were two modules; ours collapsed into
        // one, which is the surface taxonomy showing up in the picker.
        FilledSlot(
            width = width,
            height = height,
            label = bound.layout.label,
            icon = SettingsSection.APPS.meta(homeLayout).icon,
            container = colors.surface,
            content = colors.content,
            onClick = { onPick(edge) },
            // **No gear where there is nothing to open.** L1's `settingsSection` is nullable for exactly this reason
            // and its card omits the gear when it is null. The category card is the one layout the APPS section cannot
            // configure ([ConfigurableLayouts]), so a gear here would land on a pane with no chip for it — and, worse,
            // on controls that would ask a tile grid for icon sizing it does not declare.
            onSettings = if (bound.layout in ConfigurableLayouts) {
                { onOpenSettings(SettingsSection.APPS, bound.layout) }
            } else {
                null
            },
        )
    }
}

/**
 * The centre: HOME, named for the pairing it is drawing.
 *
 * In `accent` where the sides are plain, since it is the fixed point the others are arranged around — L1 used
 * `primaryContainer` for the same distinction. Two targets like every filled side slot: the body swaps the pairing,
 * the gear opens the section that sizes whichever main area it brings.
 */
@Composable
private fun HomeSlot(
    layout: HomeLayout,
    width: Dp,
    height: Dp,
    onPick: () -> Unit,
    onOpenSettings: (SettingsSection, AppsLayout?) -> Unit,
) {
    val colors = LocalMorphicColors.current
    FilledSlot(
        width = width,
        height = height,
        label = layout.label,
        icon = SettingsSection.HOME_GRID.meta(layout).icon,
        container = colors.accent,
        content = colors.onAccent,
        onClick = onPick,
        onSettings = { onOpenSettings(SettingsSection.HOME_GRID, null) },
    )
}

/**
 * A bound slot: its glyph over its name, with L1's gear under a divider.
 *
 * **Two targets, and the divider is what says so** — the body *changes* what is bound, the gear *configures* what is
 * bound already. L1 drew exactly this, and it is the rule the APPS category card follows too: a container with two jobs
 * marks the boundary rather than leaving the user to discover it. The body is clipped and clickable in its own right,
 * so the ripple stops at the divider instead of flashing under the gear.
 */
@Composable
private fun FilledSlot(
    width: Dp,
    height: Dp,
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: (() -> Unit)?,
    onSettings: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(CardCorner))
            .background(container)
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner - 8.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content)
            Spacer(Modifier.height(IconLabelGap))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        if (onSettings != null) {
            HorizontalDivider(color = content.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onSettings, modifier = Modifier.size(GearTarget)) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "$label settings",
                        tint = content,
                        modifier = Modifier.size(GearGlyph),
                    )
                }
            }
        }
    }
}

/** An unbound edge — L1's dashed outline and `+`, which say "nothing here yet" without a caption having to. */
@Composable
private fun EmptySlot(width: Dp, height: Dp, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(CardCorner))
            .dashedBorder(colors.contentMuted.copy(alpha = 0.5f), CardCorner)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Bind this edge",
            tint = colors.contentMuted,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** L1's dashed rounded outline, drawn rather than composed — there is no dashed `border` in Compose. */
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
        ),
    )
}
