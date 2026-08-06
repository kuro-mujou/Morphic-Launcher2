package inkspire.morphic.launcher.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.surface.AxisScroll
import inkspire.morphic.core.designsystem.surface.OneFingerSwipe
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollAxes
import inkspire.morphic.core.designsystem.surface.ScrollEdges
import inkspire.morphic.core.designsystem.surface.SurfaceBinding
import inkspire.morphic.core.designsystem.surface.SurfacePager
import inkspire.morphic.core.designsystem.surface.rememberSurfacePagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.HomeEdge
import kotlinx.coroutines.launch

/**
 * Live explorer for the surface-swipe spec. HOME sits centre; a simulated layout sits off each edge. **Tap a
 * side surface** to cycle it through the six layout kinds, **tap HOME** to cycle its three kinds — the finger
 * policy each edge gets is recomputed and shown (and the surface is tinted by its close policy).
 *
 * The two policies come from content on the swipe's axis, and that derivation is no longer this file's: each kind
 * declares a [ScrollAxes] and [ScrollAxes.oneFingerSwipe] turns it into the policy, exactly as the shell does for the
 * real layouts. This harness worked that rule out first, against simulated content, and then handed it over.
 * - **open** (HOME → surface): from HOME's axes, on the edge's axis.
 * - **close** (surface → HOME): from the side surface's axes, by the same rule.
 *
 * **Every simulated surface really scrolls**, which is what makes [OneFingerSwipe.AT_EDGE] testable here: a bounded
 * axis gets a scroller with far more content than fits, and reports its position through `ReportScrollEdges`. So a
 * one-finger swipe over such an axis scrolls the content, and only a swipe begun with that content already against
 * the edge pans to the next surface. [OneFingerSwipe.NEVER] (an infinite scroller) is drawn as a still surface with
 * a note, since the pan never asks it where it is — two fingers are the only way across.
 *
 * System Back always returns to HOME.
 */
@Composable
fun SurfacePagerPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        val scope = rememberCoroutineScope()
        val state = rememberSurfacePagerState()

        var home by remember { mutableStateOf(HomeKind.PAGER_INFINITE) }
        val sideLayouts = remember {
            mutableStateMapOf(
                HomeEdge.LEFT to SideLayout.PAGER_INFINITE,
                HomeEdge.RIGHT to SideLayout.PAGER_BOUNDED,
                HomeEdge.TOP to SideLayout.PAGER_CATEGORY,
                HomeEdge.BOTTOM to SideLayout.VERTICAL_GRID,
            )
        }

        BackHandler(enabled = state.openEdge != null) { scope.launch { state.close() } }

        SurfacePager(
            state = state,
            modifier = modifier.fillMaxSize(),
            sideContent = HomeEdge.entries.associateWith { edge ->
                val layout = sideLayouts.getValue(edge)
                SurfaceBinding(
                    openSwipe = home.axes.oneFingerSwipe(edge),
                    closeSwipe = layout.axes.oneFingerSwipe(edge),
                ) {
                    SideSurface(
                        edge = edge,
                        layout = layout,
                        close = layout.axes.oneFingerSwipe(edge),
                        onCycle = { sideLayouts[edge] = layout.next() },
                    )
                }
            },
        ) {
            HomeSurface(
                colors = colors,
                open = state.openEdge,
                home = home,
                onCycle = { home = home.next() },
            )
        }
    }
}

@Composable
private fun HomeSurface(
    colors: inkspire.morphic.core.designsystem.theme.MorphicColors,
    open: HomeEdge?,
    home: HomeKind,
    onCycle: () -> Unit,
) {
    SimulatedContent(axes = home.axes, modifier = Modifier.background(colors.background)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("HOME", color = colors.content, fontWeight = FontWeight.Bold)
            Text(
                text = open?.let { "→ ${it.name}" } ?: "swipe any edge",
                color = colors.contentMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "HOME: ${home.label}  (tap)",
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCycle)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** A full-screen side surface, tinted by its close policy and tappable to cycle its simulated layout. */
@Composable
private fun SideSurface(edge: HomeEdge, layout: SideLayout, close: OneFingerSwipe, onCycle: () -> Unit) {
    SimulatedContent(axes = layout.axes, modifier = Modifier.background(close.tint)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(onClick = onCycle).padding(24.dp),
        ) {
            Text("${edge.name} · ${layout.label}", color = Color.White, fontWeight = FontWeight.Bold)
            Text("close: ${close.hint}", color = Color(0xEEFFFFFF), textAlign = TextAlign.Center)
            Text("(tap to cycle layout)", color = Color(0x99FFFFFF), textAlign = TextAlign.Center)
        }
    }
}

/**
 * A surface body that genuinely scrolls on whichever of [axes] is [AxisScroll.BOUNDED], and reports where it is
 * resting — so the hand-off the gesture performs is the real one, over real scroll state.
 *
 * [AxisScroll.INFINITE] deliberately gets **no** scroller. It is a wrap-around pager, which cannot be simulated by a
 * bounded `horizontalScroll`, and the simulation would be pointless anyway: that axis is `OneFingerSwipe.NEVER`, so
 * the gesture never asks where the content is.
 *
 * One vertical [ScrollState] is shared by every horizontal page, so a two-axis surface scrolls its pages in lockstep.
 * A real category pager keeps one per page and reports the current one; here the difference is invisible, and one
 * state keeps the harness about the gesture rather than about paging.
 */
@Composable
private fun SimulatedContent(
    axes: ScrollAxes,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val scrollsX = axes.horizontal == AxisScroll.BOUNDED
    val scrollsY = axes.vertical == AxisScroll.BOUNDED

    ReportScrollEdges {
        ScrollEdges(
            atLeft = !scrollsX || !horizontal.canScrollBackward,
            atRight = !scrollsX || !horizontal.canScrollForward,
            atTop = !scrollsY || !vertical.canScrollBackward,
            atBottom = !scrollsY || !vertical.canScrollForward,
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val pageWidth = maxWidth
        Row(Modifier.fillMaxSize().then(if (scrollsX) Modifier.horizontalScroll(horizontal) else Modifier)) {
            repeat(if (scrollsX) SimulatedPages else 1) { page ->
                Column(
                    modifier = Modifier
                        .width(pageWidth)
                        .fillMaxSize()
                        .then(if (scrollsY) Modifier.verticalScroll(vertical) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (page == 0) Box(Modifier.height(160.dp), contentAlignment = Alignment.Center) { label() }
                    repeat(if (scrollsY) SimulatedRows else 0) { row ->
                        Text(
                            text = "page ${page + 1} · row ${row + 1}",
                            color = Color(0xCCFFFFFF),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (scrollsX && !scrollsY) {
                        Text("page ${page + 1} of $SimulatedPages", color = Color(0xCCFFFFFF))
                    }
                }
            }
        }
    }
}

/** Enough pages / rows that the simulated content is comfortably longer than the viewport. */
private const val SimulatedPages = 3
private const val SimulatedRows = 30

/** HOME's three simulated layouts and what each of them scrolls. */
private enum class HomeKind(val label: String, val axes: ScrollAxes) {
    PAGER_INFINITE("pager (∞)", ScrollAxes(horizontal = AxisScroll.INFINITE)),
    PAGER_BOUNDED("pager", ScrollAxes(horizontal = AxisScroll.BOUNDED)),
    LIST("vertical list", ScrollAxes(vertical = AxisScroll.BOUNDED)),
    ;

    fun next(): HomeKind = entries[(ordinal + 1) % entries.size]
}

/** The six side-surface layouts from the spec, each as what it scrolls on the two axes. */
private enum class SideLayout(val label: String, val axes: ScrollAxes) {
    PAGER_BOUNDED("Pager", ScrollAxes(horizontal = AxisScroll.BOUNDED)),
    PAGER_INFINITE("Pager (∞)", ScrollAxes(horizontal = AxisScroll.INFINITE)),
    PAGER_CATEGORY(
        "Pager + category",
        ScrollAxes(horizontal = AxisScroll.BOUNDED, vertical = AxisScroll.BOUNDED),
    ),
    PAGER_CATEGORY_INFINITE(
        "Pager + category (∞)",
        ScrollAxes(horizontal = AxisScroll.INFINITE, vertical = AxisScroll.BOUNDED),
    ),
    VERTICAL_GRID("Vertical grid", ScrollAxes(vertical = AxisScroll.BOUNDED)),
    VERTICAL_LIST("Vertical list", ScrollAxes(vertical = AxisScroll.BOUNDED)),
    ;

    fun next(): SideLayout = entries[(ordinal + 1) % entries.size]
}

/** Short label for a one-finger policy, plus whether it uses a nested-scroll hand-off. */
private val OneFingerSwipe.hint: String
    get() = when (this) {
        OneFingerSwipe.ALWAYS -> "1-finger"
        OneFingerSwipe.AT_EDGE -> "1-finger at edge (nested)"
        OneFingerSwipe.NEVER -> "2-finger only"
    }

/** Colour a surface by its close policy so the table is readable at a glance. */
private val OneFingerSwipe.tint: Color
    get() = when (this) {
        OneFingerSwipe.ALWAYS -> Color(0xFF3F7D6E)   // free one finger
        OneFingerSwipe.AT_EDGE -> Color(0xFFB08040)  // nested hand-off
        OneFingerSwipe.NEVER -> Color(0xFF6A5A9C)    // two-finger only
    }
