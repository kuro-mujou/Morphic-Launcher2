package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * Everything that decides what one app's icon looks like: the recipe that is **baked**, the plate drawn **live**
 * behind it, and how large the artwork sits inside its box.
 *
 * **This is the stored unit now, where [IconLayerSet] used to be** — in all three places a look is kept: the global
 * default (`data:settings`), one app's own row (`icon_override`), and a saved preset. The reason is one control: the
 * finalize screen offers *"save as preset"* underneath the plate switch, so a preset that carried only the layer set
 * would save half of what the user was looking at. Once a preset holds a plate, so must every store a preset can be
 * loaded into or saved from.
 *
 * ## Why the plate is not a layer
 *
 * A layer is **baked** — composited into one flat bitmap keyed by `IconId(component, layerSet, sizePx)`, with no
 * screen position in the key, which is exactly what makes that cache correct and shareable. The plate samples the
 * *wallpaper* through `Modifier.wallpaperBackdrop`, so what it draws depends on **where the icon is**: two cells
 * showing the same app must show different pixels. That cannot be baked at all, and it is why L1 kept its
 * "skin" as a separate live Compose layer rather than folding it into the icon.
 *
 * So the split here is not tidiness, it is the render boundary: [layerSet] is the bake's input and is unchanged by
 * this type existing, while [plate] and [zoom] are read by the *cell* that draws the icon.
 *
 * ## Why it is not `BackdropEffect`
 *
 * `BackdropEffect` (in `data:settings`) is the wallpaper's frost behind **panels** — the blur, the wash, the lens.
 * This is a shape of that glass behind an **icon**. Different scope, same material: the plate deliberately carries
 * no blur or tint of its own and renders with whatever effect the user chose in Settings → Effects, so a launcher
 * has one glass and not two that could drift.
 *
 * @property layerSet the baked recipe — the layers, their transforms, effects and the whole-icon shape.
 * @property plate the live glass behind the icon.
 * @property zoom the artwork's scale inside its own box, as a fraction. **Not the icon's size**, which is
 *   `IconSizing` and belongs to a surface: this is the icon's size *relative to its plate*, which is the thing no
 *   per-surface setting can express — an icon at 1f fills the box and so touches the plate's edge everywhere.
 *   A fraction rather than a dp for the reason every offset in the layer model is one: one recipe has to mean the
 *   same thing at every bake size.
 */
@Serializable
data class IconAppearance(
    val layerSet: IconLayerSet = IconLayerSet.Base,
    val plate: IconPlate = IconPlate(),
    val zoom: Float = 1f,
) {

    companion object {

        /** Plain app-default icons: parsed artwork, no plate, no scaling. What a store answers before anything is set. */
        val Base = IconAppearance()
    }
}

/**
 * The glass behind an icon: a silhouette of blurred wallpaper, sitting under the artwork.
 *
 * **Disabled by default and shaped by nothing**, which is the same division `ContentAnchor` records: the *model's*
 * default has to be what every stored recipe was written against — no plate, and a plate with no shape is the icon's
 * own square — while the *screen* that turns one on seeds a rounded square, because that is what someone asking for
 * glass behind their icons means. A control that switched on and showed a hard-edged square would read as broken.
 *
 * @property enabled whether it draws at all. **A field rather than a nullable [IconPlate]**, so switching the plate
 *   off keeps the shape it was set to: the same reason `BackdropTint` keeps `customTintArgb` while another tint is
 *   chosen. With `encodeDefaults = false` an untouched plate costs nothing on disk either way.
 * @property shape the silhouette, or null for the icon's own square — the convention a layer's own `shape` already
 *   uses, so "no shape" means one thing in this model rather than two.
 */
@Serializable
data class IconPlate(
    val enabled: Boolean = false,
    val shape: IconShape? = null,
)
