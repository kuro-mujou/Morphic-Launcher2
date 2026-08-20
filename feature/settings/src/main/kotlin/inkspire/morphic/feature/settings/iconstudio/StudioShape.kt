package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.toggle.MorphicSwitchRow
import inkspire.morphic.core.icon.IconShapes
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.ContentAnchor

/**
 * The selected thing's silhouette — a layer's, or the whole icon's.
 *
 * **Offered on every layer, including custom ones**, which differs from L1: it shaped only the foreground and left
 * custom images to their own alpha. The renderer here masks whatever it is given, so the restriction would be one
 * the UI invented — and a shaped custom layer is an obviously useful thing (a color fill trimmed to a circle is
 * how you put a colored disc behind a legacy icon).
 *
 * **One section for both targets, not two**, which is what the composite being a *selection* rather than a mode
 * buys: the chooser is the same grid of the same silhouettes, and the only difference is that the composite has no
 * anchor to offer. That is stated by [anchor] being null rather than by a second composable — a duplicated grid is
 * how two shape lists end up disagreeing about which shapes exist.
 *
 * **A shape is shown, not named.** This was a grid of text chips, which asks the user to read "rounded_square" and
 * picture it — for the one control on this screen whose entire subject is what something looks like. Every other
 * chooser here already draws its subject (the source tiles are a pack's own artwork, the swatches are the colors), and
 * this is that rule reaching the last section that broke it. The ids are gone from the UI entirely; they stay what
 * `IconShapes` maps and what is written to disk.
 *
 * **Paged, with a grid on each page, because this list is about to get long.** Seven built-ins fit one page today, so
 * the pager shows no dots and never scrolls — the arrangement is here for the shape set that is coming, and building
 * it now means the layout does not have to be reconsidered when it arrives. Adding shapes past a page's capacity adds
 * a page rather than growing the panel, which is what keeps the section a fixed height inside a panel that is already
 * capped and scrolling: **paging horizontally is how this avoids a vertical scroller inside a vertical scroller**,
 * which is the arrangement that makes a drag ambiguous.
 *
 * **The anchor is the second control here, and it is what a shape is cut against rather than which shape it is.**
 * See [ContentAnchor]: the box, or the layer's own artwork carried by its transform. It appears only once a shape is
 * chosen, because with no shape there is nothing to anchor and the studio's rule is that a control which changes
 * nothing is worse than a missing one — the same gate `TintMode`'s control sits behind in Effects. It is the
 * studio's first [MorphicSwitchRow], and the first switch in the launcher at all.
 *
 * **A pick arrives already fitted to the artwork**, so the switch is on when this opens after one — see
 * `IconStudioViewModel.pickShape` for why that is the useful landing and the box is the thing to ask for.
 *
 * @param shape the silhouette in force, or null for none — the first tile in the grid rather than a state outside it.
 * @param anchor what that silhouette is cut against, or **null when the target has no anchor at all**, which is the
 *   composite: it has neither measured ink nor a transform, so the box is its only frame.
 */
@Composable
internal fun ShapeControls(
    shape: IconShape?,
    anchor: ContentAnchor?,
    onPick: (IconShape?) -> Unit,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
) {
    // **`null` is the first cell rather than a row above the grid.** "No shape" is a choice among the same set — the
    // one every layer starts on — so it belongs in the set, and a full-width row above a grid is exactly the
    // settings-list vocabulary this screen exists not to be.
    val pages = remember { (listOf<IconShape?>(null) + IconShapes.All).chunked(ShapesPerPage) }
    val pagerState = rememberPagerState { pages.size }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // **No label over the chooser, because the panel header already carries one.** `StudioToolPanel` names the
        // open section, so a `LabeledControl("Shape")` here put the same word twice in the same 24dp — and it was
        // labeling the section rather than a control *within* it, which is what that helper is for. The rule is one
        // line: a section names its parts, never itself. `Fit to artwork` beneath is a part and keeps its own name.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // **The page height is derived from the width, because it is a consequence of it and not a value
            // anyone owns.** Tiles are square and the columns are fixed, so the width settles the cell and the
            // cell settles two rows plus the gap between them — a formula exists, which is this codebase's own
            // test for derive-versus-store (`derivedCell`, `CellFit`). It was a flat 168dp, and that is wrong on
            // an ordinary phone before it is wrong on a tablet: a 393dp screen leaves ≈361dp inside the panel,
            // so a cell is ≈84dp and two rows plus the gap want ≈176. The eight dp it was short of is not a
            // clipped corner — a `LazyVerticalGrid` in a box too small to hold it **scrolls**, so the fixed
            // height quietly created the vertical-scroller-inside-a-vertical-scroller that paging exists to
            // avoid. `pageSpacing` is not subtracted: with `PageSize.Fill` it is inserted *between* pages and
            // pages stay the full viewport width.
            BoxWithConstraints {
                val cell = (maxWidth - ShapeGridSpacing * (ShapeColumns - 1)) / ShapeColumns
                val pageHeight = cell * ShapeRows + ShapeGridSpacing * (ShapeRows - 1)

                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 8.dp,
                    // Top, against the pager's centered default: the height is a full page's, so a short last page
                    // would float its one row in the middle of the band while every other page started at the top.
                    // Latent today — the set is exactly one page — and wrong the moment a ninth shape is added.
                    // See `EffectGrid`, where the same default was visible.
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.height(pageHeight),
                ) { page ->
                    // **The pick is a command, not a `copy`.** It writes whichever silhouette the target owns and
                    // records its own history — both of which are decisions about the *recipe* rather than about
                    // this grid, and neither of which this section could make for the composite at all.
                    ShapePage(shapes = pages[page], selected = shape, onSelect = onPick)
                }
            }

            // Absent at one page, where a single dot would say nothing about a pager that cannot be paged.
            if (pages.size > 1) PagerDots(current = pagerState.currentPage, count = pages.size)
        }

        // Nothing to anchor without a shape — and nothing to anchor *to* on the composite — so the control is absent
        // rather than disabled, the same rule the monochrome row, the pack-browse row and the tint-style control are
        // each gated by. Two conditions rather than one because they are two different facts: the first is about
        // what the user has picked, the second about what the target can express at all.
        //
        // **A switch and not a pair of chips, even though the model behind it is an enum.** The two anchors are
        // genuinely two frames, which is why `ContentAnchor` names both rather than being a boolean — but what the
        // *user* is doing is turning one behavior on, against a default that is also what every icon rendered as
        // before this existed. The model keeps illegal states unrepresentable; the control says what is being asked
        // for. `MorphicSwitch` reads correctly here because the studio is a fixed-dark theme zone (see
        // `IconStudioScreen`), so the design system's colors are the dark ones whatever the system is set to.
        if (shape != null && anchor != null) {
            MorphicSwitchRow(
                label = "Fit to artwork",
                // **State-dependent, which is unusual for a switch and earns it here.** The difference is invisible
                // on an icon whose artwork already fills its box — most of them, and *every* one with Normalize on,
                // where the two frames provably coincide. A static description would leave the control looking
                // broken on exactly the icons someone tries it on first; saying what the current setting does
                // instead tells them which of the two they are looking at.
                supportingText = anchor.hint,
                checked = anchor == ContentAnchor.CONTENT,
                // Discrete, like every other press in this section, so it records at once and undo steps over it.
                // The section had no `onCommit` at all until this control arrived, which did not look like a bug
                // because the *preview* was always right: the live path deliberately records nothing, so a change
                // showed on the icon immediately and was then invisible to undo.
                onCheckedChange = { on ->
                    onUpdate { it.copy(shapeAnchor = if (on) ContentAnchor.CONTENT else ContentAnchor.BOX) }
                    onCommit()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One line saying what the chosen anchor does — see the call site for why it changes with the state. */
private val ContentAnchor.hint: String
    get() = when (this) {
        ContentAnchor.BOX -> "The shape stays put; moving the layer slides the artwork under it."
        ContentAnchor.CONTENT -> "The shape sits on the artwork and moves, zooms and turns with it."
    }

/**
 * One page of the shape chooser: [ShapeRows] rows of [ShapeColumns] tiles.
 *
 * **Plain rows rather than a `LazyVerticalGrid`, which is what makes the height bug unrepeatable rather than
 * merely fixed.** A page holds at most eight tiles by construction, so laziness saves nothing — and it was an
 * active liability, because a lazy grid given a box too short for its contents *scrolls* instead of clipping. That
 * turned a wrong constant into a nested vertical scroller; plain rows cannot do that whatever height they are
 * handed. It is the grid plan's right-tool-per-surface rule, on a surface small enough that the answer is "no
 * tool".
 *
 * A short last page is **padded with empty weights**, which the lazy grid did for free and a `Row` does not: four
 * columns of `weight(1f)` given two children would hand each half the width, so the final page's tiles would come
 * out twice the size of every other page's.
 */
@Composable
private fun ShapePage(shapes: List<IconShape?>, selected: IconShape?, onSelect: (IconShape?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ShapeGridSpacing)) {
        shapes.chunked(ShapeColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ShapeGridSpacing)) {
                row.forEach { shape ->
                    ShapeTile(
                        shape = shape,
                        selected = selected == shape,
                        onClick = { onSelect(shape) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(ShapeColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One shape, drawn at the size it is offered in.
 *
 * The silhouette is the shape's own vector, tinted — the same drawable the renderer builds its clip mask from, so what
 * is on the tile and what lands on the icon cannot disagree. [IconShapes.drawableResOrNull] returning null is a stale
 * persisted id rather than a state to design for, and it falls through to the same mark `null` uses.
 */
@Composable
private fun ShapeTile(
    shape: IconShape?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resource = shape?.let(IconShapes::drawableResOrNull)

    Box(
        modifier = modifier
            // Square, which is also the assumption the page height is derived from — so a tile that stopped being
            // square would have to change that arithmetic too.
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = resource?.let { painterResource(it) } ?: rememberVectorPainter(Icons.Default.Block),
            // The id is the honest description even though it is no longer drawn: it is what this shape is called
            // everywhere else, and a screen reader has nothing else to go on once the label is a picture.
            contentDescription = shape?.id?.replace('_', ' ') ?: "No shape",
            tint = StudioContentColor.copy(alpha = if (resource == null) 0.5f else 1f),
            modifier = Modifier
                .fillMaxSize()
                .padding(ShapeTileInset),
        )
    }
}

/**
 * Four across, two rows down: eight to a page, which is exactly the seven built-ins plus "no shape" today.
 *
 * [ShapesPerPage] is the **product** rather than a third number, so the page size and the shape of the page cannot
 * disagree — which is what a flat `8` beside a `4` was one edit away from doing.
 */
private const val ShapeColumns = 4
private const val ShapeRows = 2
private const val ShapesPerPage = ShapeColumns * ShapeRows

/** Between tiles on both axes — and, being the gap the rows are separated by, an input to the page height. */
private val ShapeGridSpacing = 8.dp

/** Keeps the silhouette clear of the tile's own rounded corners. */
private val ShapeTileInset = 12.dp
