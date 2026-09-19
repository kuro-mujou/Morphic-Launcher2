package inkspire.morphic.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.FolderCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.model.GridItem

/**
 * Which apps on home have **just taken over a folder's cell** — the auto-dissolve that turns a folder of one into its
 * last app ([HomeViewModel]'s `leaveFolderChanges`) — so their cell can show the folder turning into them.
 *
 * The folder and the app are different items with different cells, so on screen the dissolve is one cell vanishing
 * and another appearing in its place in a single frame. Nothing records that the two are the same thing becoming
 * something else; this infers it from consecutive states: a folder that is gone, and one of its apps now standing at
 * exactly its placement, in its zone.
 *
 * **Observed during composition**, not in an effect, because the transition has to be what the app's cell draws on
 * the very frame it first appears — an effect would run after that frame had drawn the plain app, and the swap would
 * still flash once before animating.
 */
internal class FolderDissolves {
    private var last: List<HomeItem>? = null
    private val dissolved = mutableStateMapOf<GridItem, HomeItem.Folder>()

    /** Compares [items] with the previous state and records any folder that has just dissolved into its last app. */
    fun observe(items: List<HomeItem>) {
        val previous = last
        last = items
        if (previous == null || previous === items) return
        val folders = previous.filterIsInstance<HomeItem.Folder>()
        if (folders.isEmpty()) return
        val remaining = items.filterIsInstance<HomeItem.Folder>().mapTo(HashSet()) { it.folder.id }
        val apps = items.filterIsInstance<HomeItem.App>()
        folders.filterNot { it.folder.id in remaining }.forEach { folder ->
            apps.firstOrNull { it.succeeds(folder) }?.let { heir -> dissolved[heir.gridItem] = folder }
        }
    }

    /** This app stands exactly where [folder] stood and was one of its members: the folder dissolved into it. */
    private fun HomeItem.App.succeeds(folder: HomeItem.Folder): Boolean =
        placement == folder.placement && zone == folder.zone && folder.apps.any { it.componentKey == info.componentKey }

    /** The folder [app] has just replaced, while its transition is still to be shown. */
    fun from(app: GridItem): HomeItem.Folder? = dissolved[app]

    /** The transition for [app] has finished; its cell draws the plain app from here. */
    fun finished(app: GridItem) {
        dissolved.remove(app)
    }
}

/**
 * An app cell arriving where [folder] stood: the folder's tile fades and draws in while the app grows out of it — the
 * folder of one visibly *becoming* its last app, rather than one tile being swapped for another.
 *
 * On the theme's expressive spatial spring, so the app settles with the same give as everything else home moves; its
 * overshoot is let through on the scale and clamped off the alphas. The folder tile shows only [app], which is all it
 * held at the end.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderDissolveCell(
    folder: HomeItem.Folder,
    app: HomeItem.App,
    metrics: IconMetrics,
    modifier: Modifier,
    itemGestures: Modifier,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(progress) {
        progress.animateTo(1f, spec)
        onFinished()
    }
    Box(modifier) {
        FolderCell(
            label = folder.folder.label,
            apps = listOf(app.info),
            metrics = metrics,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    alpha = (1f - p).coerceIn(0f, 1f)
                    scaleX = 1f - p * 0.2f
                    scaleY = 1f - p * 0.2f
                },
        )
        AppCell(
            app = app.info,
            metrics = metrics,
            itemGestures = itemGestures,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.8f + p * 0.2f
                    scaleY = 0.8f + p * 0.2f
                },
        )
    }
}
