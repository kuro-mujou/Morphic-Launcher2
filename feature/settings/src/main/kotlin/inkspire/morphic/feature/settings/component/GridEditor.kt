package inkspire.morphic.feature.settings.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridEditorEdge
import kotlin.math.ceil

/** Provisional spacing — placeholders, as everywhere else in this module. */
private val ButtonSize = 28.dp
private val ButtonGap = 4.dp
private val PreviewPad = 6.dp
private val CellGap = 3.dp
private val CellCorner = 3.dp

/** How much of the preview's *other* zone shows, and on which side of the edited grid it sits. */
internal data class EditorCompanion(val fraction: Float, val atBottom: Boolean)

/** An edit the preview has been asked to animate. The nonce re-triggers it when the same edge is pressed twice. */
private data class PreviewEdit(val edge: GridEditorEdge, val add: Boolean, val nonce: Int)

/**
 * **The grid editor: a screen-shaped preview with a − / + pair on each editable edge.**
 *
 * One component for every grid, where L1 had `HomeGridEditor` and `DockGridEditor` — two ~220-line composables that
 * were the same code apart from which half of the preview held the lattice. Here that is [companion]: the part of the
 * screen this grid *isn't*, drawn as a plain block, above or below. Home passes the dock; the dock passes the pager;
 * a grid that fills the screen passes null and the split disappears.
 *
 * **Why an edge and not a count.** The button says which side gains or loses, because that is what decides where your
 * items end up — removing the left column shifts everything left, removing the right one drops what was in it.
 * `GridReflow.edit` is the op underneath, and this is its control surface.
 *
 * **The buttons sit on the edge they affect, which is a departure from L1.** L1 put every *remove* along the top and
 * left and every *add* along the right and bottom, and told them apart by colour — red for remove, green for add.
 * That cannot survive this codebase's monochrome palette (greyscale chrome, with red reserved for `error`), and
 * without the colour the mapping is unreadable: nothing about a button above the grid says it removes a *column*. A
 * pair centred on each edge needs no legend — the buttons are *at* the thing they change, and the glyph says which
 * way. The same reason the flash below is greyscale rather than red/green.
 *
 * @param rows how many rows to draw. Normally the stored count; for a **scrolling** grid it is how many happen to fit,
 *   since there is no stored count at all — the caption below tells the two apart rather than asserting a number the
 *   user did not choose.
 * @param rowBounds null when the row axis is not the user's to set, which hides the top and bottom pairs entirely.
 *   Two grids reach that for opposite reasons: a scrolling one has no rows to bound, and the dock's are divided out
 *   of its height.
 * @param aspectRatio the screen's width ÷ height, so the preview is the shape of the device rather than a square.
 */
@Composable
internal fun GridEditor(
    cols: Int,
    rows: Int,
    colBounds: IntRange,
    rowBounds: IntRange?,
    aspectRatio: Float,
    onEdit: (GridEditorEdge, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    companion: EditorCompanion? = null,
) {
    val colors = LocalMorphicColors.current
    var nonce by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<PreviewEdit?>(null) }
    fun act(edge: GridEditorEdge, add: Boolean) {
        nonce += 1
        edit = PreviewEdit(edge, add, nonce)
        onEdit(edge, add)
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Both counts wherever there are two — a derived row count is still a fact about the grid, and hiding the
        // dock's made it look like an editor missing half its buttons rather than one whose rows follow its height.
        // A **scrolling** grid is the one case with genuinely nothing to say there: its rows are however many its
        // content reaches, so the number drawn is what fits rather than what was chosen, and claiming it as a count
        // would be the one thing a caption must not do.
        Text(
            text = if (rowBounds == null) "$cols columns" else "$cols columns × $rows rows",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
            modifier = Modifier.padding(bottom = ButtonGap * 2),
        )

        if (rowBounds != null) {
            EdgePair(
                canRemove = rows > rowBounds.first,
                canAdd = rows < rowBounds.last,
                onRemove = { act(GridEditorEdge.TOP, false) },
                onAdd = { act(GridEditorEdge.TOP, true) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgePair(
                canRemove = cols > colBounds.first,
                canAdd = cols < colBounds.last,
                onRemove = { act(GridEditorEdge.LEFT, false) },
                onAdd = { act(GridEditorEdge.LEFT, true) },
                vertical = true,
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = ButtonGap)
                    .fillMaxWidth(PREVIEW_WIDTH_FRACTION)
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(PreviewPad),
            ) {
                ScreenPreview(cols = cols, rows = rows, edit = edit, companion = companion)
            }
            EdgePair(
                canRemove = cols > colBounds.first,
                canAdd = cols < colBounds.last,
                onRemove = { act(GridEditorEdge.RIGHT, false) },
                onAdd = { act(GridEditorEdge.RIGHT, true) },
                vertical = true,
            )
        }

        if (rowBounds != null) {
            EdgePair(
                canRemove = rows > rowBounds.first,
                canAdd = rows < rowBounds.last,
                onRemove = { act(GridEditorEdge.BOTTOM, false) },
                onAdd = { act(GridEditorEdge.BOTTOM, true) },
            )
        }
    }
}

/**
 * The screen, in miniature: the edited lattice, and — when there is one — the [companion] zone as a plain block
 * taking its real share of the height.
 *
 * The split is by **measured proportion**, not decoration: seeing that the dock eats a fifth of the screen is most of
 * what makes a grid editor legible.
 */
@Composable
private fun ScreenPreview(cols: Int, rows: Int, edit: PreviewEdit?, companion: EditorCompanion?) {
    if (companion == null) {
        GridPreview(cols, rows, edit, Modifier.fillMaxSize())
        return
    }
    val gridWeight = (1f - companion.fraction).coerceIn(MIN_ZONE_WEIGHT, 1f)
    val companionWeight = (1f - gridWeight).coerceAtLeast(MIN_ZONE_WEIGHT)
    Column(Modifier.fillMaxSize()) {
        if (!companion.atBottom) {
            CompanionZone(Modifier.fillMaxWidth().weight(companionWeight))
            Spacer(Modifier.height(PreviewPad))
        }
        GridPreview(cols, rows, edit, Modifier.fillMaxWidth().weight(gridWeight))
        if (companion.atBottom) {
            Spacer(Modifier.height(PreviewPad))
            CompanionZone(Modifier.fillMaxWidth().weight(companionWeight))
        }
    }
}

/** The zone this editor is *not* editing — a plain block, since its own contents are another screen's business. */
@Composable
private fun CompanionZone(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(CellCorner)).background(LocalMorphicColors.current.contentMuted.copy(alpha = 0.25f)))
}

/**
 * The lattice itself: [cols] × [rows] rounded cells that **grow or shrink into** their new count, with the line being
 * added or removed flashing as it goes.
 *
 * **The changing line is always the last drawn index, and the canvas is mirrored for a TOP or LEFT edit** so that
 * last index lands on the near edge instead of the far one. That is L1's trick and it is a good one: it means one
 * drawing path animates all four edges instead of four cases.
 *
 * **Greyscale, not red/green.** An added line flashes to `accent`; a removed one fades out as it collapses. The
 * palette is monochrome by design and reserves red for `error`, which a user removing a row is not — and the collapse
 * already says "going away" without a colour needing to.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GridPreview(cols: Int, rows: Int, edit: PreviewEdit?, modifier: Modifier) {
    val colors = LocalMorphicColors.current
    val currentEdit by rememberUpdatedState(edit)
    var shownCols by remember { mutableIntStateOf(cols) }
    var shownRows by remember { mutableIntStateOf(rows) }
    var flashRow by remember { mutableStateOf(false) }
    var flashIndex by remember { mutableIntStateOf(-1) }
    var flashAdd by remember { mutableStateOf(true) }
    val flash = remember { Animatable(0f) }
    val flashSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(cols, rows) {
        val pending = currentEdit
        if (pending == null || (shownCols == cols && shownRows == rows)) {
            shownCols = cols
            shownRows = rows
            return@LaunchedEffect
        }
        val isRow = pending.edge == GridEditorEdge.TOP || pending.edge == GridEditorEdge.BOTTOM
        flashRow = isRow
        flashAdd = pending.add
        // For an add the new line *is* the new last index; for a remove it is the one still drawn, which the shrink
        // then takes away. Both are "the last index", which is what the mirroring below relies on.
        flashIndex = if (pending.add) {
            if (isRow) rows - 1 else cols - 1
        } else {
            if (isRow) shownRows - 1 else shownCols - 1
        }
        shownCols = cols
        shownRows = rows
        flash.snapTo(1f)
        flash.animateTo(0f, flashSpec)
    }

    val animCols by animateFloatAsState(
        shownCols.toFloat(),
        MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "cols",
    )
    val animRows by animateFloatAsState(
        shownRows.toFloat(),
        MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "rows",
    )

    val cellColor = colors.contentMuted.copy(alpha = 0.45f)
    val flashColor = if (flashAdd) colors.accent else Color.Transparent
    val flashValue = flash.value
    val mirrorRows = edit?.edge == GridEditorEdge.TOP
    val mirrorCols = edit?.edge == GridEditorEdge.LEFT

    Canvas(modifier.clipToBounds()) {
        val cw = animCols.coerceAtLeast(1f)
        val ch = animRows.coerceAtLeast(1f)
        val gap = CellGap.toPx()
        val cellW = ((size.width - gap * (cw - 1f)) / cw).coerceAtLeast(1f)
        val cellH = ((size.height - gap * (ch - 1f)) / ch).coerceAtLeast(1f)
        val corner = CornerRadius(CellCorner.toPx())
        for (r in 0 until ceil(ch).toInt()) {
            for (c in 0 until ceil(cw).toInt()) {
                val flashed = flashValue > 0f &&
                    (if (flashRow) r == flashIndex else c == flashIndex)
                val color = when {
                    !flashed -> cellColor
                    flashAdd -> lerp(cellColor, flashColor, flashValue)
                    else -> lerp(Color.Transparent, cellColor, flashValue)
                }
                val x = c * (cellW + gap)
                val y = r * (cellH + gap)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        if (mirrorCols) size.width - x - cellW else x,
                        if (mirrorRows) size.height - y - cellH else y,
                    ),
                    size = Size(cellW, cellH),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/**
 * A − / + pair for one edge, laid out along it ([vertical] for the left and right edges).
 *
 * Disabled rather than clamping at the ends, so the grid's limits are visible instead of being discovered by
 * pressing — the same rule [SettingsStepperRow] follows.
 */
@Composable
private fun EdgePair(
    canRemove: Boolean,
    canAdd: Boolean,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
    vertical: Boolean = false,
) {
    val remove: @Composable () -> Unit = { EdgeButton("−", canRemove, onRemove) }
    val add: @Composable () -> Unit = { EdgeButton("+", canAdd, onAdd) }
    if (vertical) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ButtonGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            remove()
            add()
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = ButtonGap),
        ) {
            remove()
            add()
        }
    }
}

/**
 * One square edge button.
 *
 * Deliberately not a `MorphicButton`: that is a labelled M3 button with its own minimum touch size and shape morph,
 * and eight of them around a preview would swamp it. This is a plain square, which is what L1 used too — the part
 * worth keeping from its version.
 */
@Composable
private fun EdgeButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Box(
        modifier = Modifier
            .size(ButtonSize)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) colors.content else colors.content.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = colors.surface,
        )
    }
}

/** How much of the row's width the preview takes, leaving the rest for the edge buttons either side. */
private const val PREVIEW_WIDTH_FRACTION = 0.62f

/** Neither zone of a split preview may collapse to nothing, however extreme the real proportion is. */
private const val MIN_ZONE_WEIGHT = 0.08f
