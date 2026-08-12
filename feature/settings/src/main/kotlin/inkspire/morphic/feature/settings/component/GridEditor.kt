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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.model.GridEditorEdge
import inkspire.morphic.core.model.SideZoneEdge
import kotlin.math.ceil

/** Provisional spacing — placeholders, as everywhere else in this module. */
private val ButtonSize = 24.dp
private val ButtonGap = 4.dp
private val ButtonCorner = 6.dp
private val PreviewPad = 6.dp
private val CellGap = 3.dp
private val CellCorner = 3.dp

/**
 * The shapes the mockup will take, whatever the real screen is.
 *
 * A ratio outside these is a window we do not draw well — a freeform split, a very wide foldable — and a preview
 * stretched to match would be less recognizable as a screen than a clamped one. L1 clamps the same pair.
 */
private const val MIN_PREVIEW_RATIO = 0.35f
private const val MAX_PREVIEW_RATIO = 2.5f

/**
 * Which side of the edited grid the companion zone sits on.
 *
 * The same four values as `SideZoneEdge` and deliberately a separate type — see [of]. Each HOME section sees the
 * split from its own end: the main area's companion *is* the side zone (wherever it sits), and the side zone's is the
 * main area (the opposite edge).
 */
internal enum class CompanionSide { TOP, BOTTOM, START, END;

    /** Empty, so [of] can hang off it — Kotlin enums carry no implicit companion. */
    companion object
}

/**
 * Where a companion zone sitting on [edge] is drawn.
 *
 * The two enums are the same four values and stay separate on purpose: [SideZoneEdge] is a fact about HOME (a domain
 * value with a rule behind it), while [CompanionSide] is a fact about *this editor* — a grid whose companion is
 * neither of HOME's zones would still need one. This is the one-line bridge, so the four sections that need it do not
 * each write their own `when`.
 */
internal fun CompanionSide.Companion.of(edge: SideZoneEdge): CompanionSide = when (edge) {
    SideZoneEdge.TOP -> CompanionSide.TOP
    SideZoneEdge.BOTTOM -> CompanionSide.BOTTOM
    SideZoneEdge.START -> CompanionSide.START
    SideZoneEdge.END -> CompanionSide.END
}

/** How much of the preview the *other* zone takes, and on which side of the edited grid it sits. */
internal data class EditorCompanion(val fraction: Float, val side: CompanionSide)

/** An edit the preview has been asked to animate. The nonce re-triggers it when the same edge is pressed twice. */
internal data class PreviewEdit(val edge: GridEditorEdge, val add: Boolean, val nonce: Int)

/**
 * **The grid editor: a screen-shaped mockup ringed by − and + buttons, one per edge per action.**
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
 * **The arrangement is L1's: removes along the top and left, adds along the right and bottom**, each pair spread to
 * the preview's own extent so a button sits at the corner it acts on. The top-left − takes the left column and is
 * directly above it; the right rail's lower + adds a row at the bottom and is beside it.
 *
 * **An earlier cut here centered a −/+ pair on each edge instead**, on the grounds that L1 tells add from remove by
 * color (red / green) and this codebase's palette cannot — grayscale chrome, red reserved for `error`. The premise
 * was right and the conclusion was wrong: in L1 the *position already encodes the action*, top/left removing and
 * bottom/right adding, so the color was reinforcement rather than the signal. Taking the arrangement and dropping
 * the color keeps everything the palette forbids and nothing it doesn't, and the glyphs carry what is left. The
 * flash in the preview stays grayscale for the same reason.
 *
 * **The mockup is a fixed size per posture, not a fraction of the pane** — see the four numbers in the body, which
 * are L1's. A settings pane is half a tablet and the whole of a phone, so a fraction gave a different preview in
 * each; worse, `aspectRatio` derives *height* from width, so at a tall phone ratio a wide pane bought a preview
 * taller than the section holding it.
 *
 * @param preview the layout's own mockup, or null for the plain lattice (plus [companion] when there is one). Home
 *   and the dock pass null; each APPS layout passes its own, so the editor shows the chrome that layout really has.
 * @param cols how many columns to draw — **the count the surface will actually draw**, not the one in storage. Every
 *   caller fits its stored size first (`CellFit`), because a count chosen at one icon size outlives it: enlarge the
 *   icons and fewer columns fit, and an editor claiming the stored number would contradict the surface *and* count from
 *   a number nobody can see. The one exception is stated at its call site (the APPS pager, whose count is a store
 *   capacity).
 * @param rows how many rows to draw, on the same terms; for a **scrolling** grid it is how many happen to fit, since
 *   there is no stored count at all — the caption below tells the two apart rather than asserting a number the user did
 *   not choose.
 * @param rowBounds null when the row axis is not the user's to set, which hides the top and bottom pairs entirely.
 *   Two grids reach that for opposite reasons: a scrolling one has no rows to bound, and the dock's are divided out
 *   of its height.
 * @param aspectRatio **the whole screen's** width ÷ height, so the preview is the shape of the device rather than a
 *   square — and deliberately *not* the width the grid is left with after [horizontalInsetFraction]. Narrowing this
 *   instead of insetting inside it is the bug that made the preview grow taller and taller as the margin widened: the
 *   box is `fillMaxWidth().aspectRatio(ratio)`, so a smaller ratio buys height.
 * @param horizontalInsetFraction how much of the preview's width the grid's own margin takes, per side, as a fraction
 *   of the whole. Drawn as blank space *within* the screen shape, which is what a margin actually looks like. Capped
 *   below at leaving something to draw, since the slider's ceiling is independent of this device's width. L1 does the
 *   same, down to computing the fraction from the padding and the measured area.
 */
@Composable
internal fun GridEditor(
    cols: Int,
    rows: Int,
    colBounds: IntRange?,
    rowBounds: IntRange?,
    aspectRatio: Float,
    onEdit: (GridEditorEdge, Boolean) -> Unit,
    horizontalInsetFraction: Float = 0f,
    preview: (@Composable (PreviewEdit?) -> Unit)? = null,
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

    // **A fixed mockup size per posture, as L1 sizes its own** — not a fraction of the pane. A settings pane is half
    // a tablet and all of a phone, so `fillMaxWidth(f).aspectRatio(r)` made the preview a different size in each and,
    // at a tall phone ratio, very tall indeed: height is width ÷ ratio, so 62% of a wide pane bought a preview taller
    // than the section it sits in. A mockup of a screen wants a *legible* size, which is a physical decision, and the
    // width then follows from the shape. L1's four numbers, unchanged.
    val previewHeight = when (currentDeviceConfiguration()) {
        DeviceConfiguration.PHONE_PORTRAIT -> 240.dp
        DeviceConfiguration.PHONE_LANDSCAPE -> 140.dp
        DeviceConfiguration.TABLET_PORTRAIT -> 360.dp
        DeviceConfiguration.TABLET_LANDSCAPE -> 280.dp
    }
    val previewWidth = previewHeight * aspectRatio.coerceIn(MIN_PREVIEW_RATIO, MAX_PREVIEW_RATIO)

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Both counts wherever there are two — a derived row count is still a fact about the grid, and hiding the
        // dock's made it look like an editor missing half its buttons rather than one whose rows follow its height.
        // A **scrolling** grid is the one case with genuinely nothing to say there: its rows are however many its
        // content reaches, so the number drawn is what fits rather than what was chosen, and claiming it as a count
        // would be the one thing a caption must not do.
        // Omitted entirely when neither axis is editable — a list is one lane, so "1 column" would be a count
        // masquerading as a choice. The caller states what it has instead (the list says its row height).
        if (colBounds != null || rowBounds != null) {
            Text(
                text = when {
                    rowBounds == null -> "$cols columns"
                    colBounds == null -> "$rows rows"
                    else -> "$cols columns × $rows rows"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
                modifier = Modifier.padding(bottom = ButtonGap * 2),
            )
        }

        // **Three arrangements, and L1 has all three.**
        //
        // *Both axes* — the plus-shape: removes along the top and left, adds along the right and bottom, each pair
        // spread to the preview's extent so a button sits at the corner it acts on. Pressing the top-left − takes the
        // left column and is directly above it.
        //
        // *Columns only* — the top and bottom rows go, and each side rail carries **− above + for its own edge**
        // instead. That is L1's `showRowControls = false` branch, and it is better than hiding the rails and leaving
        // the two column rows: with no rows to edit, the plus-shape's top and bottom rows are the only controls left
        // and they sit furthest from the columns they change.
        //
        // *Neither* — the framed preview alone. A list has one lane and a declared row height, so there is no count to
        // press; what it needs is somewhere to *see* the height its slider sets.
        //
        // An earlier cut centered a −/+ pair on each edge, reasoning that a grayscale palette cannot tell add from
        // remove by color as L1's red/green does. The premise was right, the conclusion wrong: in L1 the *position*
        // encodes the action, so the color was reinforcement. The arrangement is kept and the color is not.
        val framedPreview: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .padding(ButtonGap * 2)
                    .width(previewWidth)
                    .height(previewHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(PreviewPad),
            ) {
                // The companion split wraps **whatever** fills the edited half — a caller's own mockup or the plain
                // lattice. Passing a `preview` used to replace the whole screen, which was fine while only the APPS
                // layouts passed one (they have no companion zone) and wrong the moment HOME's vertical list did:
                // it has a mockup *and* a widget area beside it.
                ScreenPreview(companion) { half ->
                    if (preview != null) Box(half) { preview(edit) }
                    else GridPreview(cols, rows, edit, horizontalInsetFraction, half)
                }
            }
        }

        if (colBounds != null && rowBounds != null) {
            Row(modifier = Modifier.width(previewWidth), horizontalArrangement = Arrangement.SpaceBetween) {
                EdgeButton("−", cols > colBounds.first) { act(GridEditorEdge.LEFT, false) }
                EdgeButton("−", cols > colBounds.first) { act(GridEditorEdge.RIGHT, false) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeRail(previewHeight) {
                    EdgeButton("−", rows > rowBounds.first) { act(GridEditorEdge.TOP, false) }
                    EdgeButton("−", rows > rowBounds.first) { act(GridEditorEdge.BOTTOM, false) }
                }
                framedPreview()
                EdgeRail(previewHeight) {
                    EdgeButton("+", rows < rowBounds.last) { act(GridEditorEdge.TOP, true) }
                    EdgeButton("+", rows < rowBounds.last) { act(GridEditorEdge.BOTTOM, true) }
                }
            }
            Row(modifier = Modifier.width(previewWidth), horizontalArrangement = Arrangement.SpaceBetween) {
                EdgeButton("+", cols < colBounds.last) { act(GridEditorEdge.LEFT, true) }
                EdgeButton("+", cols < colBounds.last) { act(GridEditorEdge.RIGHT, true) }
            }
        } else if (colBounds != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeRail(previewHeight) {
                    EdgeButton("−", cols > colBounds.first) { act(GridEditorEdge.LEFT, false) }
                    EdgeButton("+", cols < colBounds.last) { act(GridEditorEdge.LEFT, true) }
                }
                framedPreview()
                EdgeRail(previewHeight) {
                    EdgeButton("−", cols > colBounds.first) { act(GridEditorEdge.RIGHT, false) }
                    EdgeButton("+", cols < colBounds.last) { act(GridEditorEdge.RIGHT, true) }
                }
            }
        } else {
            framedPreview()
        }
    }
}

/**
 * One vertical run of two edge buttons, spread to the preview's height so each lands at the row it addresses.
 *
 * What each rail *carries* depends on which axes are editable — two removes when the rows are editable, or a − and a +
 * for one column edge when they are not. The rail itself only spreads them.
 */
@Composable
private fun EdgeRail(height: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * The screen, in miniature: the **edited** half — whatever fills it — and, when there is one, the [companion] zone as
 * a plain block taking its real share of the screen.
 *
 * The split is by **measured proportion**, not decoration: seeing that the dock eats a fifth of the screen is most of
 * what makes a grid editor legible.
 *
 * **It splits along the companion's own axis** — a `Column` for a zone above or below, a `Row` for one beside. That is
 * what makes the phone-landscape dock legible: it is a rail, so the mockup has to show a rail, and a horizontal split
 * at the same fraction would say the opposite of what the surface does.
 *
 * **The margin reaches the edited half and never the companion**, because it is *this grid's* setting: home's two
 * zones each store their own, so insetting both from one number would show the user a dock narrowing because they
 * moved the pager's slider. That is why the inset is applied inside [edited] rather than around this split — L1 draws
 * it the same way, passing `insetFraction` to its `GridPreview` and none to its `NonGridPreview`. An earlier cut here
 * wrapped the whole mockup, which shrank the screen rather than the grid in it.
 */
@Composable
private fun ScreenPreview(companion: EditorCompanion?, edited: @Composable (Modifier) -> Unit) {
    if (companion == null) {
        edited(Modifier.fillMaxSize())
        return
    }
    val gridWeight = (1f - companion.fraction).coerceIn(MIN_ZONE_WEIGHT, 1f)
    val companionWeight = (1f - gridWeight).coerceAtLeast(MIN_ZONE_WEIGHT)
    val first = companion.side == CompanionSide.TOP || companion.side == CompanionSide.START
    when (companion.side) {
        CompanionSide.TOP, CompanionSide.BOTTOM -> Column(Modifier.fillMaxSize()) {
            if (first) {
                CompanionZone(Modifier.fillMaxWidth().weight(companionWeight))
                Spacer(Modifier.height(PreviewPad))
            }
            edited(Modifier.fillMaxWidth().weight(gridWeight))
            if (!first) {
                Spacer(Modifier.height(PreviewPad))
                CompanionZone(Modifier.fillMaxWidth().weight(companionWeight))
            }
        }
        CompanionSide.START, CompanionSide.END -> Row(Modifier.fillMaxSize()) {
            if (first) {
                CompanionZone(Modifier.fillMaxHeight().weight(companionWeight))
                Spacer(Modifier.width(PreviewPad))
            }
            edited(Modifier.fillMaxHeight().weight(gridWeight))
            if (!first) {
                Spacer(Modifier.width(PreviewPad))
                CompanionZone(Modifier.fillMaxHeight().weight(companionWeight))
            }
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
 * **Grayscale, not red/green.** An added line flashes to `accent`; a removed one fades out as it collapses. The
 * palette is monochrome by design and reserves red for `error`, which a user removing a row is not — and the collapse
 * already says "going away" without a color needing to.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GridPreview(cols: Int, rows: Int, edit: PreviewEdit?, insetFraction: Float, modifier: Modifier) {
    val colors = LocalMorphicColors.current
    val currentEdit by rememberUpdatedState(edit)
    var shownCols by remember { mutableIntStateOf(cols) }
    var shownRows by remember { mutableIntStateOf(rows) }
    var flashRow by remember { mutableStateOf(false) }
    var flashIndex by remember { mutableIntStateOf(-1) }
    var flashAdd by remember { mutableStateOf(true) }
    // The last press already animated. **A count can change without a press** — committing a larger minimum icon size
    // re-fits the grid under the editor — and without this the pending edit would be replayed, flashing the edge of
    // whichever button was touched last and coloring a removal as an add. One nonce, consumed once.
    var flashedNonce by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    val flashSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(cols, rows) {
        val pending = currentEdit
        if (pending == null || pending.nonce == flashedNonce || (shownCols == cols && shownRows == rows)) {
            shownCols = cols
            shownRows = rows
            return@LaunchedEffect
        }
        flashedNonce = pending.nonce
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

    // The margin, taken off the lattice itself. A fraction rather than dp because the mockup is already the screen's
    // shape, so a fraction of its width is the same fraction of the real one — no dp conversion, and it stays right
    // when the posture changes the preview's size.
    val inset = insetFraction.coerceIn(0f, MAX_PREVIEW_INSET)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth(1f - inset * 2).fillMaxHeight().clipToBounds()) {
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
}


/**
 * One square edge button.
 *
 * Deliberately not a `MorphicButton`: that is a labeled M3 button with its own minimum touch size and shape morph,
 * and eight of them around a preview would swamp it. This is a plain square, which is what L1 used too — the part
 * worth keeping from its version.
 */
@Composable
private fun EdgeButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Box(
        // The inset is L1's, and it is load-bearing now that the buttons sit at the preview's corners rather than
        // centered on its edges: without it the outermost pair touches the very ends of the row and the group reads as
        // wider than the screen it is describing.
        modifier = Modifier
            .padding(ButtonGap)
            .size(ButtonSize)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(if (enabled) colors.content else colors.content.copy(alpha = 0.25f))
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
            style = MaterialTheme.typography.labelLarge,
            color = colors.surface,
        )
    }
}

/**
 * The most of the preview's width, per side, the margin may be drawn as.
 *
 * A guard rather than a rule about padding: the slider's ceiling is in dp and this device's width is not, so a wide
 * margin on a narrow screen could otherwise leave the lattice nothing to draw in and the preview would read as broken
 * rather than as tight. L1 clamps the same fraction at the same kind of value.
 */
private const val MAX_PREVIEW_INSET = 0.4f

/** Neither zone of a split preview may collapse to nothing, however extreme the real proportion is. */
private const val MIN_ZONE_WEIGHT = 0.08f
