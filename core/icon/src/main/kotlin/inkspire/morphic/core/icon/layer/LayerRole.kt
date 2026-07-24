package inkspire.morphic.core.icon.layer

import kotlinx.serialization.Serializable

/**
 * Which of the three kinds a layer in an [IconLayerSet] is. A set always has exactly one [FOREGROUND] and one
 * [BACKGROUND] (both permanent, non-deletable); [CUSTOM] layers are user-inserted and freely orderable. The
 * only ordering rule is that the foreground sits above the background — enforced by [IconLayerSet].
 */
@Serializable
enum class LayerRole { FOREGROUND, BACKGROUND, CUSTOM }
