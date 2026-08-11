package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * The outline a foreground or background layer can be masked to. Custom (inserted) image layers are **not**
 * shaped — they carry their own alpha — so shape only applies to fg/bg layers; a layer with no shape is a
 * `null` shape on its spec, not a value here.
 *
 * A shape is defined by a **vector drawable** prepared as a resource, referenced by this stable [id]. Adding
 * a new shape means dropping in a vector drawable and pointing an id at it — no per-shape path math in code.
 *
 * **The id is all that lives here; what it resolves to does not.** The id → `R.drawable` mapping is
 * `IconShapes` in `core:icon`, beside the renderer that builds the clip mask from the vector's silhouette,
 * because a resource id is an Android concept and this module has none. That is also what lets a drawable be
 * renamed without touching a single persisted blob.
 *
 * It is a value class so it is a zero-cost, type-safe wrapper over the id, and `@Serializable` persists it as
 * just that id string inside the layer set (matching the "shape-by-id" on-disk form). Treat [id] as a stable
 * on-disk contract.
 */
@Serializable
@JvmInline
value class IconShape(val id: String)
