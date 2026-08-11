package inkspire.morphic.core.icon

import kotlinx.serialization.Serializable

/**
 * The outline a foreground or background layer can be masked to. Custom (inserted) image layers are **not**
 * shaped — they carry their own alpha — so shape only applies to fg/bg layers; a layer with no shape is a
 * `null` shape on its spec, not a value here.
 *
 * A shape is defined by a **vector drawable** prepared as a resource, referenced by this stable [id]. Adding
 * a new shape means dropping in a vector drawable and pointing an id at it — no per-shape path math in code.
 * Turning the id into a drawable and building the clip mask from the vector's silhouette are rendering
 * concerns, added later once the shape drawables exist.
 *
 * It is a value class so it is a zero-cost, type-safe wrapper over the id, and `@Serializable` persists it as
 * just that id string inside the layer set (matching the "shape-by-id" on-disk form). Treat [id] as a stable
 * on-disk contract.
 */
@Serializable
@JvmInline
value class IconShape(val id: String)
