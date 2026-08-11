package inkspire.morphic.core.icon.layer

import kotlinx.serialization.Serializable

/**
 * A visual effect applied to a single layer while compositing. Deliberately an open, extensible type: the
 * concrete variants (monochrome/tint filter, saturation, brightness, shadow, gradient, …) land in the effects
 * slice, so for now a layer's effect list is simply empty.
 *
 * Modelling effects as a list of these — rather than one fixed nullable field per effect — is a direct lesson
 * from L1, which hard-coded a column per effect and paid for it in repeated schema churn.
 */
@Serializable
sealed interface LayerEffect
