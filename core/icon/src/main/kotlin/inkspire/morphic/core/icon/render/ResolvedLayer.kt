package inkspire.morphic.core.icon.render

import inkspire.morphic.core.icon.layer.IconLayerSpec
import inkspire.morphic.core.icon.parse.ParsedLayer

/**
 * One layer after its [IconLayerSpec.source] has been resolved to concrete [content] (a colour or a drawable),
 * paired with the [spec] that says how to draw it. This is what the compositor consumes: the "what pixels"
 * is decided, leaving only the "how" (transform / shape / effects).
 *
 * Once resolved, [content] supersedes `spec.source` — the compositor reads pixels from [content] and uses
 * [spec] only for its render parameters.
 */
data class ResolvedLayer(
    val content: ParsedLayer,
    val spec: IconLayerSpec,
)
