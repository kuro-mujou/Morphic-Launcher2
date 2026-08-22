package inkspire.morphic.core.designsystem.component.slider

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.MorphicColors

/**
 * The shared monochrome slider thumb: a circle in the `thumb` color that springs larger on press / drag /
 * focus via the Expressive motion spring. Used for the single [MorphicSlider] and for each thumb of
 * [MorphicRangeSlider], so both feel identical.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MorphicSliderThumb(
    interactionSource: MutableInteractionSource,
    colors: MorphicColors,
    enabled: Boolean,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val diameter by animateDpAsState(
        targetValue = if ((pressed || dragged || focused) && enabled) 22.dp else 18.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "MorphicSliderThumb",
    )
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (enabled) colors.thumb else colors.contentDisabled),
    )
}
