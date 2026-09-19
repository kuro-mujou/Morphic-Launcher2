package inkspire.morphic.core.designsystem.cell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.DragHandoff
import inkspire.morphic.core.designsystem.drag.LocalDragCoordinator
import inkspire.morphic.core.designsystem.grid.LandingGlide
import inkspire.morphic.core.designsystem.grid.rememberLandingGlide
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem

/**
 * A rounded backing plate holding a 2×2 preview of up to four apps' baked icons, [size] on a side.
 *
 * **"A collection of apps, drawn as one icon-sized tile."** Two things need exactly that and are otherwise
 * unrelated, which is why this is its own composable rather than private to either:
 * - [FolderCell] — a folder on a grid, with its label beneath.
 * - the APPS **category card**'s overflow cluster — the "and N more" tile that opens the category.
 *
 * Extracted when the second consumer arrived rather than kept as a copy: the two would otherwise drift apart on
 * corner radius, padding and plate alpha, and a folder tile and a cluster tile that *nearly* match read as a bug.
 *
 * Fewer than four [apps] simply leaves the trailing slots empty, and the plate is still drawn — a tile with one icon
 * in it is a legitimate state for the cluster (a category of exactly five apps), and [FolderCell]'s own model
 * guarantees at least two.
 *
 * **The plate is a background, not a clip**, so an icon landing in it can be drawn from outside its edge.
 *
 * @param size the plate's edge length; the four icon slots are derived from it, so a caller sizes the tile and
 *   nothing else.
 * @param backing whether to draw the rounded translucent plate behind the icons, **and the inset that goes with it**.
 *   The two are one flag rather than two because the inset exists only to keep the icons off the plate's rounded edge —
 *   with no plate there is nothing to inset from, and the 2×2 fills the tile edge to edge.
 *
 *   True for a folder on a grid, which needs the plate to read as one object among loose icons. False for the category
 *   card's overflow cluster, which sits *inside* a tile that already has a fill: a second plate there is a box within a
 *   box, and it made the cluster the only slot on the card with a visible container.
 * @param receivesDrops an app that has just been dropped into this collection lands in it: into its preview slot,
 *   gliding from the drop and shrinking from the carried size, or — past the fourth, with no slot to show it — into
 *   the middle of the plate, fading as it goes. Only for a tile a drag can drop into (a folder); a tile that merely
 *   shows the app somewhere else must not play a landing for it.
 *
 * TODO(launcher backing plate): a plain translucent surface for now. Replace with the themed skin/backing-plate
 *  (the deferred live-Compose backdrop) when that subsystem lands.
 */
@Composable
fun IconPreviewPlate(
    apps: List<AppInfo>,
    size: Dp,
    modifier: Modifier = Modifier,
    backing: Boolean = true,
    receivesDrops: Boolean = false,
) {
    val colors = LocalMorphicColors.current
    // Derived from the plate's own size so the tile scales as one thing: a home folder's plate and a card cluster's
    // plate are wildly different sizes, and fixed dp gaps would swamp the small one.
    val gap = size * PREVIEW_GAP_FRACTION
    val pad = if (backing) size * PREVIEW_PADDING_FRACTION else 0.dp
    val slot = (size - pad * 2 - gap) / 2
    val arrival = if (receivesDrops) rememberArrival(apps) else null
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .size(size)
                .then(
                    if (backing) {
                        Modifier.background(
                            colors.surface.copy(alpha = BACKING_ALPHA),
                            RoundedCornerShape(size * CORNER_FRACTION),
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            PreviewRow(apps.getOrNull(0), apps.getOrNull(1), slot, gap, arrival)
            PreviewRow(apps.getOrNull(2), apps.getOrNull(3), slot, gap, arrival)
        }
        // The app that arrived past the preview's four slots: it has nowhere to be shown, so it glides into the
        // plate and fades there — the drop still visibly goes *into* the folder rather than vanishing on release.
        arrival?.hidden?.let { (app, landing) ->
            key(app.componentKey) {
                ArrivingPastPreview(app, landing, slot, onFinished = { arrival.hidden = null })
            }
        }
    }
}

/**
 * Which app has just been dropped into the collection, read from consecutive [apps] against the coordinator's fresh
 * landing. **Only an app that was not here before** counts: an app dragged *out* of the folder is still a member
 * until its drop is written, and must not glide back in as it leaves.
 */
@Stable
private class Arrival {
    var previous: Set<ComponentKey>? = null

    /** An arrival past the four preview slots, drawn over the plate until its fade finishes. */
    var hidden by mutableStateOf<Pair<AppInfo, DragHandoff>?>(null)

    /** The landing for an arrival that has a preview slot, keyed by the app; its icon takes it. */
    var shown: Map<ComponentKey, DragHandoff> = emptyMap()
}

@Composable
private fun rememberArrival(apps: List<AppInfo>): Arrival {
    val arrival = remember { Arrival() }
    val presented = LocalSurfacePresented.current
    val landing = LocalDragCoordinator.current?.landing?.takeIf { presented && it.isFresh }
    val landed = (landing?.item as? GridItem.App)?.component
    val keys = apps.map { it.componentKey }
    val isNew = landed != null && landed in keys && arrival.previous?.contains(landed) != true
    if (landing != null && landed != null && isNew) {
        if (keys.indexOf(landed) < PREVIEW_SLOTS) {
            arrival.shown = mapOf(landed to landing)
        } else {
            arrival.hidden = apps.first { it.componentKey == landed } to landing
        }
    }
    arrival.previous = keys.toSet()
    return arrival
}

/** One row of the 2×2 preview: two icon slots (or empty spacers) sized to [slot]. */
@Composable
private fun PreviewRow(
    left: AppInfo?,
    right: AppInfo?,
    slot: Dp,
    gap: Dp,
    arrival: Arrival?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        PreviewIcon(
            app = left,
            slot = slot,
            arrival = arrival,
        )
        PreviewIcon(
            app = right,
            slot = slot,
            arrival = arrival,
        )
    }
}

/**
 * A single preview icon, or an empty [slot]-sized gap when the collection has no app for this position.
 *
 * Keyed by the app, so the landing belongs to the app rather than to the slot it happens to occupy.
 */
@Composable
private fun PreviewIcon(app: AppInfo?, slot: Dp, arrival: Arrival?) {
    if (app == null) {
        Box(modifier = Modifier.size(slot))
        return
    }
    key(app.componentKey) {
        val glide = rememberLandingGlide()
        // With the slot's size, so an icon carried in at a grid cell's size shrinks into it.
        glide.update(arrival?.shown?.get(app.componentKey), slot)
        val sizePx = with(LocalDensity.current) { slot.roundToPx() }
        AppIcon(
            component = app.componentKey,
            contentDescription = app.label,
            sizePx = sizePx,
            modifier = Modifier
                .size(slot)
                .landed(glide),
        )
    }
}

/** An app landing in the plate past its four slots: glides to the plate's centre, shrinking and fading out. */
@Composable
private fun ArrivingPastPreview(app: AppInfo, landing: DragHandoff, slot: Dp, onFinished: () -> Unit) {
    val glide = rememberLandingGlide()
    glide.update(landing, slot)
    val fade = remember { Animatable(1f) }
    LaunchedEffect(fade) {
        // The glide's own spring, so the icon is gone as it arrives rather than before or after.
        fade.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        onFinished()
    }
    val sizePx = with(LocalDensity.current) { slot.roundToPx() }
    AppIcon(
        component = app.componentKey,
        contentDescription = null,
        sizePx = sizePx,
        modifier = Modifier
            .size(slot)
            .landed(glide) { fade.value },
    )
}

/** Draws at [glide]'s translation and scale, with [fade] as both the alpha and a further shrink. */
private fun Modifier.landed(glide: LandingGlide, fade: () -> Float = { 1f }): Modifier = this
    .onPlaced(glide::onPlaced)
    .graphicsLayer {
        val f = fade()
        val landed = glide.translation()
        translationX = landed.x
        translationY = landed.y
        // Fading to half size rather than to nothing, so it reads as going *into* the plate, not as vanishing.
        val shrink = 1f - FADE_SHRINK * (1f - f)
        scaleX = glide.scale() * shrink
        scaleY = glide.scale() * shrink
        alpha = f.coerceIn(0f, 1f)
    }

private const val PREVIEW_SLOTS = 4
private const val FADE_SHRINK = 0.5f
private const val PREVIEW_GAP_FRACTION = 0.08f
private const val PREVIEW_PADDING_FRACTION = 0.12f
private const val CORNER_FRACTION = 0.24f
private const val BACKING_ALPHA = 0.55f
