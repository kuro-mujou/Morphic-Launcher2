package inkspire.morphic.feature.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.surface.SurfacePagerState
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.HomeEdge
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel

/**
 * **The one hint that follows first-run setup**: a pill at the edge the chosen look bound, saying how to reach the apps.
 *
 * One hint, never a tour. It goes for good on the first crossing to any side surface — the gesture it teaches, done —
 * or when tapped, and it shows only while HOME is at rest, since over an open surface it would point at the way back.
 *
 * **Announced as it appears**, as a polite live region: a swipe is not something a screen-reader user finds by
 * exploring, and those are the users with no other way to learn where the apps are.
 *
 * Placed by the shell above the pager and below the menu and prompts, and only on a launcher that is running rather than
 * being previewed.
 */
@Composable
internal fun EdgeHintOverlay(pagerState: SurfacePagerState) {
    val viewModel = koinViewModel<EdgeHintViewModel>()
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val current = hint

    // The gesture it teaches ends the hint: the first time any side surface opens, however it was opened.
    LaunchedEffect(current != null) {
        if (current == null) return@LaunchedEffect
        snapshotFlow { pagerState.openEdge }.first { it != null }
        viewModel.dismiss()
    }

    AnimatedVisibility(
        visible = current != null && pagerState.openEdge == null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (current != null) EdgeHintPill(hint = current, onDismiss = viewModel::dismiss)
    }
}

@Composable
private fun EdgeHintPill(hint: EdgeHint, onDismiss: () -> Unit) {
    val colors = LocalMorphicColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical)
            .padding(24.dp),
        contentAlignment = hint.edge.alignment,
    ) {
        Text(
            text = hint.text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.content,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceElevated)
                .clickable(onClickLabel = "Dismiss", onClick = onDismiss)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Where on HOME the pill sits: against the edge it names. */
private val HomeEdge.alignment: Alignment
    get() = when (this) {
        HomeEdge.TOP -> Alignment.TopCenter
        HomeEdge.BOTTOM -> Alignment.BottomCenter
        HomeEdge.LEFT -> Alignment.CenterStart
        HomeEdge.RIGHT -> Alignment.CenterEnd
    }

/**
 * What the pill says: the direction the finger travels — away from the edge it starts at — and the finger count when it
 * is not the ordinary one.
 */
private val EdgeHint.text: String
    get() {
        val arrow = when (edge) {
            HomeEdge.BOTTOM -> "↑ Swipe up"
            HomeEdge.TOP -> "↓ Swipe down"
            HomeEdge.LEFT -> "→ Swipe right"
            HomeEdge.RIGHT -> "← Swipe left"
        }
        return if (twoFingers) "$arrow with two fingers for your apps" else "$arrow for your apps"
    }
