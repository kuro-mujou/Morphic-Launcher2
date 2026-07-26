package inkspire.morphic.launcher.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import inkspire.morphic.core.designsystem.surface.OneFingerSwipe
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
 * The two policies come from content on the swipe's axis:
 * - **open** (HOME → surface): from HOME's content. Pager owns horizontal, list owns vertical; infinite → the
 *   owned edges are two-finger ([OneFingerSwipe.NEVER]), bounded → one finger at the page edge
 *   ([OneFingerSwipe.AT_EDGE]), the free axis → one finger anytime ([OneFingerSwipe.ALWAYS]).
 * - **close** (surface → HOME): from the side surface's own content, by the same rule on the close axis.
 *
 * Nothing scrolls yet, so [OneFingerSwipe.AT_EDGE] currently behaves like [OneFingerSwipe.ALWAYS] (one finger);
 * only [OneFingerSwipe.NEVER] is two-finger. The at-edge hand-off is the deferred nested-scroll step. System
 * Back always returns to HOME.
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
                    openSwipe = home.openSwipe(edge),
                    closeSwipe = layout.closeSwipe(edge),
                ) {
                    SideSurface(
                        edge = edge,
                        layout = layout,
                        close = layout.closeSwipe(edge),
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
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
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
    Box(
        Modifier.fillMaxSize().background(close.tint).clickable(onClick = onCycle).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${edge.name} · ${layout.label}", color = Color.White, fontWeight = FontWeight.Bold)
            Text("close: ${close.hint}", color = Color(0xEEFFFFFF), textAlign = TextAlign.Center)
            Text("(tap to cycle layout)", color = Color(0x99FFFFFF), textAlign = TextAlign.Center)
        }
    }
}

private val HomeEdge.isHorizontal: Boolean get() = this == HomeEdge.LEFT || this == HomeEdge.RIGHT
private val HomeEdge.isVertical: Boolean get() = this == HomeEdge.TOP || this == HomeEdge.BOTTOM

/** How the imagined content behaves on an axis: not scrolled, bounded (has an edge), or infinite (no edge). */
private enum class Scroll { NONE, BOUNDED, INFINITE }

/** Turns "what owns this axis" into the one-finger policy — the single rule behind the whole spec table. */
private fun Scroll.toSwipe(): OneFingerSwipe = when (this) {
    Scroll.NONE -> OneFingerSwipe.ALWAYS
    Scroll.BOUNDED -> OneFingerSwipe.AT_EDGE
    Scroll.INFINITE -> OneFingerSwipe.NEVER
}

/** HOME's three simulated layouts and the open policy each hands every edge. */
private enum class HomeKind(val label: String, private val horizontal: Scroll, private val vertical: Scroll) {
    PAGER_INFINITE("pager (∞)", Scroll.INFINITE, Scroll.NONE),
    PAGER_BOUNDED("pager", Scroll.BOUNDED, Scroll.NONE),
    LIST("vertical list", Scroll.NONE, Scroll.BOUNDED),
    ;

    /** Opening crosses on the edge's axis, so read HOME's scroll on that axis. */
    fun openSwipe(edge: HomeEdge): OneFingerSwipe =
        (if (edge.isHorizontal) horizontal else vertical).toSwipe()

    fun next(): HomeKind = entries[(ordinal + 1) % entries.size]
}

/** The six side-surface layouts from the spec, each as its scroll behaviour on the two axes. */
private enum class SideLayout(val label: String, private val horizontal: Scroll, private val vertical: Scroll) {
    PAGER_BOUNDED("Pager", Scroll.BOUNDED, Scroll.NONE),
    PAGER_INFINITE("Pager (∞)", Scroll.INFINITE, Scroll.NONE),
    PAGER_CATEGORY("Pager + category", Scroll.BOUNDED, Scroll.BOUNDED),
    PAGER_CATEGORY_INFINITE("Pager + category (∞)", Scroll.INFINITE, Scroll.BOUNDED),
    VERTICAL_GRID("Vertical grid", Scroll.NONE, Scroll.BOUNDED),
    VERTICAL_LIST("Vertical list", Scroll.NONE, Scroll.BOUNDED),
    ;

    /** Closing crosses on the edge's axis, so read this surface's scroll on that axis. */
    fun closeSwipe(edge: HomeEdge): OneFingerSwipe =
        (if (edge.isHorizontal) horizontal else vertical).toSwipe()

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
