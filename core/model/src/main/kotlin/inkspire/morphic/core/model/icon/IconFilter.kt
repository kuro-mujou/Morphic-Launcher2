package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * A named color look applied to a layer, referenced by a stable [id].
 *
 * **Deliberately `IconShape`'s exact shape, because it is the same kind of thing: a fixed vocabulary.** The
 * decision worth recording is that this is *engineering* rather than *content*, which is the opposite of the call
 * made for icon presets — and the difference is what each one is. A preset is a whole recipe, open-ended, and
 * whether it is any good depends on the artwork it lands on, so curating presets is design work with no end. A
 * filter is one 4×5 color matrix: bounded, self-contained, and doing the same thing to every icon. Choosing
 * twenty numbers and a name is the same act as adding a value to [LayerBlend].
 *
 * **The id is all that lives here; what it resolves to does not.** The id → matrix table is `IconFilters` in
 * `core:icon`, beside the renderer that applies it. Unlike `IconShapes` it needs no `R.drawable`, so it *could*
 * sit in this module — it does not, because a matrix is a **look**, and `core:model` holds what an icon is rather
 * than what it looks like.
 *
 * An **unknown id renders unfiltered** rather than failing, matching `IconShapes.drawableResOrNull` returning null:
 * a recipe written by a later build degrades on an earlier one instead of breaking it. Treat [id] as a stable
 * on-disk contract.
 */
@Serializable
@JvmInline
value class IconFilter(val id: String)
