package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * A repeating texture laid over a layer, referenced by a stable [id].
 *
 * **`IconShape`'s exact shape, and deliberately *not* its library.** The two look interchangeable — both are a
 * vector drawable behind an id — and sharing one catalog was considered and rejected, because what each thing *is*
 * differs on both questions an entry would have to answer. A shape is a silhouette whose **alpha is a mask**,
 * stretched once to fill the box; a pattern is artwork whose **marks are drawn**, tiled at a scale and an angle.
 * Half of each list would be nonsense in the other role: a teardrop tiles into wallpaper, and a dot grid trims
 * nothing.
 *
 * What they do share is the *pipeline*, which is the part worth copying: drop a drawable in, add an id, the id is
 * the on-disk contract, and an **unknown id draws nothing** rather than failing — so a recipe written by a later
 * build degrades on an earlier one instead of breaking it.
 *
 * **The drawable is a stencil, not a picture.** Its marks are authored opaque and its ground transparent; what
 * color they come out is `LayerEffect.Pattern.argb`, so one asset serves every color rather than the library
 * needing a copy per shade. The id → `R.drawable` mapping is `IconPatterns` in `core:icon`, for `IconShape`'s
 * reason: a resource id is an Android concept and this module has none.
 */
@Serializable
@JvmInline
value class IconPattern(val id: String)
