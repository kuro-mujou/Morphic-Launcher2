package inkspire.morphic.launcher.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.pager.LauncherPager
import inkspire.morphic.core.designsystem.pager.rememberLauncherPagerState
import inkspire.morphic.core.designsystem.pager.launcherPagerSwipe
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import kotlin.math.abs

/**
 * Standalone test screen for the custom [LauncherPager] — separate from the drag harness. Swipe between pages;
 * toggle **infinite** wrap on/off (watch the edge behaviour change) and the per-page **transform** (a scale +
 * fade parallax). Confirms: only real pages exist (no Int.MAX), wrap works both directions, bounded stops at
 * the ends, and fling/settle land on a page.
 */
@Composable
fun PagerPlaygroundScreen(modifier: Modifier = Modifier) {
    LauncherTheme(darkTheme = true) {
        val colors = LocalMorphicColors.current
        var infinite by remember { mutableStateOf(true) }
        var transform by remember { mutableStateOf(true) }
        val pageCount = 5

        val state = rememberLauncherPagerState(
            pageCount = { pageCount },
            infiniteScroll = { infinite },
        )

        Column(
            modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Toggle("infinite: ${onOff(infinite)}", colors.contentMuted) { infinite = !infinite }
                Toggle("transform: ${onOff(transform)}", colors.contentMuted) { transform = !transform }
                Text("page ${state.currentPage + 1}/$pageCount", color = colors.content)
            }

            LauncherPager(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .launcherPagerSwipe(state),
                pageTransform = if (transform) {
                    {
                        val off = abs(it.pageOffset)
                        alpha = 1f - (off * 0.4f).coerceIn(0f, 0.6f)
                        val scale = 1f - (off * 0.15f).coerceIn(0f, 0.15f)
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    null
                },
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(PageColors[page % PageColors.size]),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Page ${page + 1}",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Page indicator dots.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(pageCount) { i ->
                    val active = i == state.currentPage
                    Box(
                        Modifier
                            .size(if (active) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.accent else colors.contentDisabled),
                    )
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, color: Color, onClick: () -> Unit) {
    Text(text = label, color = color, modifier = Modifier.clickable(onClick = onClick))
}

private fun onOff(value: Boolean): String = if (value) "on" else "off"

private val PageColors = listOf(
    Color(0xFF4F6D7A), Color(0xFF56A3A6), Color(0xFF6B8F71),
    Color(0xFF9A6FB0), Color(0xFFC08552),
)
