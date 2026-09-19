package inkspire.morphic.core.designsystem.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import inkspire.morphic.core.designsystem.drag.DragHandoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Carries a dropped item from where the proxy let go of it into the cell that now holds it.
 *
 * **It chases the cell's placement rather than a target fixed at the drop**, and that is the part that cannot be
 * simpler. A drop commits through the view model, and the new placement reaches the grid through a state flow a frame
 * or more later — so the cell is first laid out where it *was*, then moves. A glide aimed once, at the first
 * placement it saw, would head for the old cell and then jump. Here every placement retargets the same spring, so the
 * item keeps travelling smoothly toward wherever the cell has most recently been put.
 *
 * It owns the cell's motion while it runs, which is why the caller drops `animatePlacement` for that span: the late
 * placement change would otherwise be animated twice, once by each.
 */
@Stable
internal class LandingGlide(private val scope: CoroutineScope) {

    /** The handoff already taken, so a recomposition does not restart the glide from the drop point. */
    private var taken: DragHandoff? = null
    private var glide by mutableStateOf<Animatable<Offset, AnimationVector2D>?>(null)

    /** The cell's centre in root px as last placed. State, because the draw-time translation is measured from it. */
    private var placedCenter by mutableStateOf<Offset?>(null)

    val isRunning: Boolean get() = glide != null

    /**
     * Takes [landing] if it is fresh and not yet taken. Called from composition, which is the frame the proxy
     * disappears: the glide has to exist before that frame draws, or the cell flashes at its slot for one frame.
     */
    fun offer(landing: DragHandoff?) {
        if (landing == null || landing === taken || !landing.isFresh) return
        taken = landing
        glide = Animatable(landing.centerInRoot, Offset.VectorConverter)
        // A cell that is not re-placed by the drop (released back onto its own slot) never reaches [onPlaced] again,
        // so the target it already has is the one to head for.
        placedCenter?.let(::retarget)
    }

    fun onPlaced(coordinates: LayoutCoordinates) {
        val center = coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
        placedCenter = center
        retarget(center)
    }

    /** How far to draw the cell from where it is laid out, in px. */
    fun translation(): Offset {
        val g = glide ?: return Offset.Zero
        val placed = placedCenter ?: return Offset.Zero
        return g.value - placed
    }

    private fun retarget(to: Offset) {
        val g = glide ?: return
        if (g.targetValue == to && g.isRunning) return
        scope.launch {
            g.animateTo(to, spring(stiffness = Spring.StiffnessMediumLow))
            // Only the glide that finished may end itself — a retarget cancels its predecessor, which then never
            // reaches this line, and a newer landing may have replaced it meanwhile.
            if (glide === g) glide = null
        }
    }
}

@Composable
internal fun rememberLandingGlide(): LandingGlide {
    val scope = rememberCoroutineScope()
    return remember(scope) { LandingGlide(scope) }
}
