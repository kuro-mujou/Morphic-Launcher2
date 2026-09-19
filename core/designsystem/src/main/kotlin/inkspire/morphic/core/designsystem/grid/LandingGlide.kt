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
import inkspire.morphic.core.designsystem.drag.HandoffFreshMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
 *
 * **The cell the item was lifted out of does not glide when the drop took the item away** ([DragHandoff.leftSource]
 * — a merge, a removal, another zone). That cell is disposed once the commit arrives, a frame or more later; gliding
 * it meanwhile would carry the item back toward its old slot and then drop it out of existence, which is the flash
 * a merge used to show. It stays hidden instead, and a cell that newly holds the item is the one that glides in.
 */
@Stable
internal class LandingGlide(private val scope: CoroutineScope) {

    /** The handoff already taken, so a recomposition does not restart the glide from the drop point. */
    private var taken: DragHandoff? = null
    private var glide by mutableStateOf<Animatable<Offset, AnimationVector2D>?>(null)

    /** This cell has been the lifted one since its last landing — the one a drop that took the item away leaves. */
    private var lifted = false

    /** Held invisible after a drop took its item away, until the cell is disposed or the handoff goes stale. */
    private var vacated by mutableStateOf(false)

    /** The cell's centre in root px as last placed. State, because the draw-time translation is measured from it. */
    private var placedCenter by mutableStateOf<Offset?>(null)

    val isRunning: Boolean get() = glide != null

    /** The cell's alpha: none while its item is being carried or has just been carried away, full otherwise. */
    fun alpha(isDragged: Boolean): Float = if (isDragged || vacated) 0f else 1f

    /**
     * Follows the drag and takes [landing] if it is fresh and not yet taken. Called from composition, which is the
     * frame the proxy disappears: the glide (or the hiding) has to exist before that frame draws, or the cell flashes
     * at its slot for one frame.
     */
    fun update(isDragged: Boolean, landing: DragHandoff?) {
        if (isDragged) lifted = true
        if (landing == null || landing === taken || !landing.isFresh) return
        taken = landing
        val wasLifted = lifted
        lifted = false
        if (landing.leftSource && wasLifted) {
            vacated = true
            // A commit that never arrives (refused downstream) must not leave the item invisible for good.
            scope.launch {
                delay(HandoffFreshMs.milliseconds)
                vacated = false
            }
            return
        }
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
