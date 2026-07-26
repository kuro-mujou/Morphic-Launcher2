package inkspire.morphic.core.designsystem.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/**
 * Animates a child to its new position whenever its placement within the parent changes — the single mechanism
 * behind both free-grid **push** (a drop shifts occupants to new cells) and MovingGap **gap migration** (items
 * slide as the insertion gap moves). Applied to a [LauncherGrid] child, it turns an instant `GridPlacement`
 * change into a spring glide, replacing the harness's old per-tile `animateIntOffsetAsState`.
 *
 * **How** — and why no `LookaheadScope` here. Position-only movement doesn't need lookahead measurement:
 * [onPlaced] reports the child's parent-assigned target each layout (it sits *before* [offset] in the chain, so
 * it never sees the draw-time shift and can't feed back), and the [offset] holds the child at its previous spot,
 * springing the delta to zero so it visually catches up. `LookaheadScope`/`approachLayout` would only be needed
 * to also animate *size* or shared-element handoffs — out of scope for tiles that just move between cells.
 *
 * The `Animatable` is seeded from the **first** real position (inside [onPlaced]), so a freshly-placed child
 * snaps in instead of flying from the origin. That same property is why the caller drops this modifier while a
 * tile is being dragged: on release it mounts fresh and lands at the committed cell without a spurious glide,
 * while the *pushed* tiles (which kept the modifier throughout) animate into their new cells.
 *
 * @param stiffness the settling spring's stiffness (default [Spring.StiffnessMediumLow]).
 */
fun Modifier.animatePlacement(stiffness: Float = Spring.StiffnessMediumLow): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    var animatable by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { coordinates ->
            val placed = coordinates.positionInParent().round()
            target = placed
            val anim = animatable
            when {
                anim == null -> animatable = Animatable(placed, IntOffset.VectorConverter) // seed: no first glide
                anim.targetValue != placed -> scope.launch { anim.animateTo(placed, spring(stiffness = stiffness)) }
            }
        }
        .offset {
            val anim = animatable
            val t = target
            if (anim == null || t == null) IntOffset.Zero else anim.value - t
        }
}
