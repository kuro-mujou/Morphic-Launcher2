package inkspire.morphic.core.designsystem.cell

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
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * A folder as a surface lays it out, for [FolderDissolves] to compare across states.
 *
 * @property position where the folder stands, in the surface's own terms — whatever makes "the same place" true on
 *   that surface (home: zone and placement; the APPS pager: page and slot). Compared by equality only.
 */
data class FolderSpot<P>(val id: Long, val label: String, val position: P, val members: Set<ComponentKey>)

/**
 * Which apps have **just taken over a folder's place** — the auto-dissolve that turns a folder of one into its last
 * app — so their cell can show the folder turning into them.
 *
 * The folder and the app are different entries with different cells, so on screen the dissolve is one cell vanishing
 * and another appearing in its place in a single frame. Nothing records that the two are the same thing becoming
 * something else; this infers it from consecutive states: a folder that is gone, and one of its members now standing
 * at exactly its [FolderSpot.position].
 *
 * **Observed during composition**, not in an effect, because the transition has to be what the app's cell draws on
 * the very frame it first appears — an effect would run after that frame had drawn the plain app, and the swap would
 * still flash once before animating. One per surface: home and the APPS pager each dissolve their own folders.
 */
class FolderDissolves<P> {
    private var source: Any? = null
    private var folders: Map<Long, FolderSpot<P>>? = null
    private val dissolved = mutableStateMapOf<ComponentKey, String>()

    /**
     * Compares the surface's state with the previous one and records any folder that has just dissolved. The lists
     * are computed only when [source] — the state they are read from — is a different instance, so the recompositions
     * a drag makes on every move cost nothing here.
     */
    fun observe(source: Any, folders: () -> List<FolderSpot<P>>, apps: () -> List<Pair<ComponentKey, P>>) {
        if (source === this.source) return
        this.source = source
        val previous = this.folders
        val now = folders().associateBy { it.id }
        this.folders = now
        if (previous == null) return
        val gone = previous.values.filter { it.id !in now }
        if (gone.isEmpty()) return
        val placed = apps()
        gone.forEach { folder ->
            placed.firstOrNull { (app, position) -> position == folder.position && app in folder.members }
                ?.let { (app, _) -> dissolved[app] = folder.label }
        }
    }

    /** The label of the folder [app] has just replaced, while its transition is still to be shown. */
    fun from(app: ComponentKey): String? = dissolved[app]

    /** The transition for [app] has finished; its cell draws the plain app from here. */
    fun finished(app: ComponentKey) {
        dissolved.remove(app)
    }
}

/** A [FolderDissolves] for one surface. */
@Composable
fun <P> rememberFolderDissolves(): FolderDissolves<P> = remember { FolderDissolves() }

/**
 * An app cell arriving where a folder stood: the folder's tile fades and draws in while the app grows out of it — the
 * folder of one visibly *becoming* its last app, rather than one tile being swapped for another.
 *
 * On the theme's expressive spatial spring, so the app settles with the same give as everything else that moves; its
 * overshoot is let through on the scale and clamped off the alphas. The folder tile shows only [app], which is all it
 * held at the end.
 *
 * @param folderLabel the dissolved folder's label, from [FolderDissolves.from].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderDissolveCell(
    folderLabel: String,
    app: AppInfo,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    itemGestures: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(progress) {
        progress.animateTo(1f, spec)
        onFinished()
    }
    Box(modifier) {
        FolderCell(
            label = folderLabel,
            apps = listOf(app),
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
            app = app,
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
