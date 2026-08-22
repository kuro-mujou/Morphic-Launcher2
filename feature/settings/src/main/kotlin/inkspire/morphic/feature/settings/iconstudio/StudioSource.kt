package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.core.model.icon.LayerRole
import inkspire.morphic.core.model.icon.LayerSource
import inkspire.morphic.data.icons.InstalledIconPack

/**
 * Where the layer's content comes from.
 *
 * **Which options are offered turns on two things, and they are different in kind.** Most of it is the layer's
 * [LayerRole], because the model says so: [LayerSource.AppDefault] is meaningless on a custom layer (there is no "the
 * app's custom layer" to resolve), and [LayerSource.AppDefaultMonochrome] is the foreground's alternate artwork and
 * nowhere else's. Offering either where it resolves to nothing would be a control that silently does nothing — which
 * this codebase treats as worse than a missing one.
 *
 * The rest is **which studio this is**, and that is a rule about what a global edit should be *allowed* to do rather
 * than about what resolves: [allowsFixedSource] and [onBrowsePack] each gate a source that would hand one specific
 * picture or color to every app on the device. They differ in reach — a fixed source is refused on the **foreground**
 * alone, that being the layer which identifies the app, while a *named* pack drawable is refused everywhere but the
 * individual studio — and both arrive as a decision made elsewhere rather than as a test performed here, since the
 * ViewModel refuses behind each of them.
 *
 * **Two ranks of control, which is what the layout says.** The tiles are the *providers* — whose artwork this is — and
 * beneath them sit refinements of whichever is chosen: monochrome under the app's own artwork, a named drawable under
 * a pack. Neither refinement changes the provider, so neither is a tile.
 *
 * @param allowsFixedSource whether this layer may take a source that is the same for every app — a solid color or a
 *   custom image; see `IconStudioState.canUseFixedSource`.
 * @param onToggleNormalize turns [IconLayerSpec.normalize] on or off — whether the app's artwork is resized so
 *   every icon covers about the same amount of its box. Beside monochrome because both refine the app's own artwork.
 * @param onToggleMonochrome switches the app's own artwork between its normal and monochrome forms. A command rather
 *   than an [onUpdate] written here, so the edit records itself in history — see `IconStudioViewModel`.
 * @param onPickAppDefault chooses the app's own artwork, in whichever form this layer was last showing it. A command
 *   for a second reason on top of that one: the form is remembered by the ViewModel, so this panel cannot write it.
 * @param onPickSolidFill fills the layer with a flat color, returning to the one it last held. A command for
 *   [onPickAppDefault]'s reason exactly, pointed at a value instead of a form.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceControls(
    spec: IconLayerSpec,
    packs: List<InstalledIconPack>,
    allowsFixedSource: Boolean,
    onUpdate: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    onCommit: () -> Unit,
    onPickImage: () -> Unit,
    onPickAppDefault: () -> Unit,
    onPickSolidFill: () -> Unit,
    onToggleMonochrome: () -> Unit,
    onToggleNormalize: () -> Unit,
    onPickPack: (String) -> Unit,
    onBrowsePack: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // **No label over the tiles, because the panel header already carries one.** `StudioToolPanel` names the open
        // section *and* the layer it is pointed at, so a `LabeledControl("Source")` here said the same word a second
        // time directly beneath it — and it was naming the section rather than a control within it, which is what that
        // helper is for. A section names its parts, never itself: `Fill` below is a part and keeps its name.
        //
        // **A flow row of tiles rather than a column of rows**, because the choices are *pictures*: an icon pack is
        // recognized by its own artwork long before its name is read, so labeled text was asking the user to read
        // a list where they could have looked at one. Flowing rather than scrolling sideways, so a device with six
        // packs installed shows all six instead of hiding the last of them past an edge.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Absent on a custom layer, where it resolves to nothing: there is no "the app's custom layer".
            if (spec.role != LayerRole.CUSTOM) {
                SourceTile(
                    label = "System default",
                    // **`AppDefaultMonochrome` reads as selected here too, and that is not a special case.**
                    // Monochrome is a *refinement of* this source rather than a peer of it — the app's own
                    // artwork either way — so the tile is genuinely the chosen one, and the row beneath is what
                    // says which form of it.
                    selected = spec.source == LayerSource.AppDefault ||
                        spec.source == LayerSource.AppDefaultMonochrome,
                    // **Which is also why the tile does not write a source itself.** Coming back from a pack or an
                    // image has to land on the form the layer was left in, and only the ViewModel remembers that —
                    // see `IconStudioViewModel.pickAppDefault`. Writing `AppDefault` here would drop the refinement
                    // the row beneath controls, with the tile looking identical before and after the press.
                    onClick = onPickAppDefault,
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = StudioContentColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // Acts on every press rather than only when unselected, because pressing it again re-picks.
            if (allowsFixedSource) {
                SourceTile(
                    label = "Custom image",
                    selected = spec.source is LayerSource.CustomImage,
                    onClick = onPickImage,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = StudioContentColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // **One tile per pack, drawn as the pack's own launcher icon** — which `InstalledIconPack.preview`
            // already carries, for exactly the reason its KDoc gives: packs are recognized by their artwork rather
            // than by their name. An empty list is the ordinary state on a device with none, and it is also what a
            // missing `<queries>` declaration looks like — see `IconPackManager`.
            packs.forEach { pack ->
                SourceTile(
                    label = pack.label,
                    selected = (spec.source as? LayerSource.IconPack)?.packPackage == pack.packageName,
                    onClick = { onPickPack(pack.packageName) },
                ) {
                    val preview = pack.preview
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    } else {
                        // A pack whose own icon could not be read still has to be pickable; its label is beneath
                        // the tile either way.
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = StudioContentColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

        // **The global foreground's missing tiles are left unexplained**, deliberately. The copy that used to sit here
        // named the alternative (add a custom layer) at the moment it was wanted, which is the argument for a note —
        // but it was four lines of prose in a panel of tiles and sliders, and it appeared on the layer a user opens the
        // global studio on, so it was the first thing on the busiest section. `IconStudioState.canUseFixedSource` is
        // still the rule; what is gone is stating it here.
        //
        // **A refinement of the chosen source, not a tile of its own** — which is the whole reason it sits here rather
        // than among them. The tiles answer "whose artwork is this?" and monochrome does not change the answer: it is
        // still the app's. As a fourth tile it would read as a peer of a pack and an image, and it would appear on one
        // layer only, so the row would change length as the selection moved.
        //
        // The shape is the pack-browse row's directly beneath: a refinement shown only while the source it refines is
        // chosen. Foreground-only, because the platform ships one silhouette and it is for that slot — there is no
        // "the app's monochrome background". Absent rather than disabled elsewhere, per the usual rule.
        //
        // **Offered whether or not this app ships a themed layer**, and it has to be: `IconLayerResolver` decides
        // which of the two monochromes an app gets, and in the global studio that is not one answer. Draining a layer
        // that is *not* app artwork — a pack, an image — is Saturation's job in Effects, not a second meaning here.
        if (spec.role == LayerRole.FOREGROUND &&
            (spec.source == LayerSource.AppDefault || spec.source == LayerSource.AppDefaultMonochrome)
        ) {
            ChoiceRow(
                label = "Monochrome",
                selected = spec.source == LayerSource.AppDefaultMonochrome,
                onClick = onToggleMonochrome,
            )

            // **The other refinement of the app's own artwork, so it sits here rather than under Transform.** It
            // ends up multiplying the layer's zoom, but what it decides is *how to read the artwork this source just
            // chose* — a question only this panel is asking. Under Transform it would sit beside a zoom slider it
            // silently scales, and the two would read as rivals.
            //
            // Foreground-only, matching where `normalized` applies: the background is deliberately left alone, and a
            // pack, an image or a fill was placed by somebody on purpose.
            ChoiceRow(
                label = "Normalize size",
                selected = spec.normalize,
                onClick = onToggleNormalize,
            )
        }

        // **Only when a pack is already chosen, and only for a single app.** Browsing offers a *named* drawable, which
        // the global default would hand to every app — so `onBrowsePack` is null there and the row is absent rather
        // than disabled.
        val chosen = spec.source as? LayerSource.IconPack
        if (chosen != null && onBrowsePack != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChoiceChip(
                    label = chosen.drawableName?.let { "Icon: $it — change" } ?: "Choose a different icon",
                    selected = chosen.drawableName != null,
                    modifier = Modifier.weight(1f),
                ) { onBrowsePack(chosen.packPackage) }

                // **The way back out of a named drawable, and it needs to be a control rather than a trick.** Clearing
                // the name lets the pack's own `appfilter.xml` decide again — which re-picking the pack tile also does,
                // as a side effect of `pickPack` writing a name-less source. That is not something a user can be
                // expected to work out: nothing about a tile that is already selected suggests pressing it undoes
                // something else.
                //
                // **A chip, not an icon button**, because this row is made of chips and everything else in the section
                // is text on the same wash — a lone glyph at the end of a text row reads as chrome rather than as one
                // of the choices. It is also the honest form here: "reset" is a word, where the arrow-in-a-circle that
                // usually means it is one of the least specific glyphs there is.
                //
                // Present only once there is a name to clear, so it is never a button that does nothing — and beside
                // the row it undoes rather than somewhere in the section, since what it reverts is *that* choice.
                if (chosen.drawableName != null) {
                    ChoiceChip(label = "Reset", selected = false) {
                        onUpdate { it.copy(source = LayerSource.IconPack(chosen.packPackage)) }
                        onCommit()
                    }
                }
            }
        }

        // **Parked here pending a home, and deliberately not a tile.** A solid fill is not artwork *from* anywhere, so
        // it does not belong among the source kinds — the flow row answers "where do this layer's pixels come from",
        // and a flat color answers a different question. It stays reachable meanwhile, because a colored plate beneath
        // an icon is what a layer added empty most often becomes.
        //
        // TODO: move to whichever section ends up owning a layer's appearance.
        if (allowsFixedSource) {
            LabeledControl("Fill") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // **A command, for the "System default" tile's two reasons at once**: pressing it while a fill is
                    // already chosen must not throw away the color underneath, and returning to a fill must land on
                    // the color this layer last held — which only the ViewModel remembers. See `pickSolidFill`.
                    ChoiceRow("Solid color", spec.source is LayerSource.SolidFill, onPickSolidFill)
                    // Gated with the row that chooses a fill, not shown whenever one happens to be set: a layer that
                    // may not *take* a solid color must not offer to recolor one either.
                    (spec.source as? LayerSource.SolidFill)?.let { fill ->
                        ColorField(argb = fill.argb) { argb ->
                            onUpdate { it.copy(source = LayerSource.SolidFill(argb)) }
                            onCommit()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The tile's rounded rect, stated once because a tile asks for it twice — the clip that rounds its fill, and the
 * outline drawn over it — and it records which way round they go in the chain: **the outline above the clip**, or a
 * boundary with no antialiasing strips the arc it runs along. Same fix as the effect section's swatches and the
 * layer rail's tiles.
 */
private val SourceTileShape = RoundedCornerShape(14.dp)

/**
 * One choice in the source row: a rounded square with something drawn in the middle of it, and its name beneath.
 *
 * **Labeled despite being a picture**, which is the one place this departs from "a tile is recognized by its artwork":
 * two of the three kinds have no artwork, only a glyph, and an unlabeled glyph is the thing this studio's own notes
 * call worse than a wordy button. A label is also how two packs with similar icons are told apart.
 *
 * The label sits **outside** the tile and is constrained to its width, so a long pack name wraps beneath the square
 * rather than stretching it — every tile stays one size, which is what makes the row read as a set.
 */
@Composable
private fun SourceTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                // **Above the clip, and the clip is the same rounded rect** — see [SourceTileShape]. A rounded clip
                // is a hardware outline clip with no antialiasing, so a ring drawn *inside* one whose boundary runs
                // along its own outer arc loses whole pixels of it: full width along the straight sides, thin and
                // stepped at the corners. It still draws over the fill and the ripple, both of which are inside.
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = StudioContentColor.copy(alpha = if (selected) 1f else 0.2f),
                    shape = SourceTileShape,
                )
                .clip(SourceTileShape)
                .background(Color.White.copy(alpha = if (selected) 0.22f else 0.08f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
            content = content,
        )
        Text(
            text = label,
            color = StudioContentColor.copy(alpha = if (selected) 1f else 0.7f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
