package inkspire.morphic.feature.settings.iconstudio

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.icon.compose.IconPreview
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.data.settings.IconPreset

/**
 * The studio's preset library: save what is being edited under a name, load one back, rename or delete one.
 *
 * **A preset is a copy, not a link.** Loading one is an ordinary edit — recorded in history, undoable, and not
 * saved until Save — and deleting one touches nothing it was ever applied to. That is what makes the library
 * safe to keep tidy: there is no way for removing a preset to change an icon.
 *
 * Saving is likewise **independent of Save**. Naming a recipe puts it in the library and commits it nowhere, so a
 * user can build a look, keep it, and back out without applying it to anything.
 *
 * **A grid of squares, each drawing its own recipe on the subject app**, which is the same shape the Icons pane's
 * library takes and for the same reason: a preset is a *look*, so two recipes differing in a bloom's angle read as
 * two identical rows of text. The tile is the real render path — `IconPreview`, the one every other preview on this
 * screen goes through — so a preset that cannot draw live previews from its bake here exactly as it will on a
 * surface.
 *
 * **A preset is *made* in the global studio and only *used* in an individual one**, which is why [onSave] and
 * [onRename] are nullable rather than controls that are always drawn — the same "absent rather than offered and
 * refused" shape as `onBrowsePack`, pointed the other way. The reason is what a recipe tuned against one app tends
 * to contain: a [inkspire.morphic.core.model.icon.LayerSource.CustomImage] is a picture of *that* app, and an icon
 * pack's `drawableName` is a drawable chosen *for* that app. Saved as a preset, both would be carried into every
 * other icon the look was later applied to. A global recipe has neither by construction, since it has to hold for
 * every app — which is also what the shuffle is for.
 *
 * So the individual studio's library is **select-to-apply and nothing else**: no name field, and no menu on a tile.
 * That is not a disabled menu but an absent one, because none of its verbs could ever become legal here.
 *
 * A section body: no surface and no title of its own — see [StudioToolPanel] for why those belong to the host.
 *
 * @param parsed the subject's parsed artwork, or null before it loads — a tile draws its plate alone until then.
 * @param onSave names the current recipe, or **null** in the individual studio, where the library is read-only.
 * @param onRename renames one, or **null** wherever [onSave] is.
 */
@Composable
internal fun PresetsControls(
    presets: List<IconPreset>,
    parsed: ParsedIcon?,
    customImage: (path: String) -> Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> Drawable?,
    onSave: ((String) -> Unit)?,
    onLoad: (IconPreset) -> Unit,
    onDelete: (String) -> Unit,
    onRename: ((String, String) -> Unit)?,
) {
    // Which preset the name row is renaming, or null when it is naming the current recipe. Held here rather than in
    // the row because it is what the row *is* — a tile's menu sets it, and the row reads it.
    var renaming by remember { mutableStateOf<String?>(null) }

    // **Cleared when the library stops containing it**, which is not hypothetical: deleting the preset being renamed
    // would otherwise leave the row committing a rename of something that is gone. `renamed` is a no-op on a missing
    // name, so nothing would break — it would just silently do nothing, which is worse than the row closing.
    LaunchedEffect(presets) {
        if (renaming != null && presets.none { it.name == renaming }) renaming = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        onSave?.let { save ->
            PresetNameRow(
                renaming = renaming,
                onCommit = { name ->
                    val from = renaming
                    if (from == null) save(name) else onRename?.invoke(from, name)
                    renaming = null
                },
                onCancel = { renaming = null },
            )
        }

        if (presets.isEmpty()) {
            Text(
                // Two different absences: with saving offered, the library is empty and this says where a preset
                // would show up; without it, the library is empty *and* it cannot be filled from here, so the line
                // has to say where it can.
                text = if (onSave != null) {
                    "Saved looks appear here, and in Settings → Icons."
                } else {
                    "Looks saved while editing all icons appear here, ready to apply to this app."
                },
                color = StudioContentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PresetGrid(
            presets = presets,
            spacing = PresetGridSpacing,
            tileMax = PresetTileMax,
            modifier = Modifier
                .heightIn(max = PresetGridMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) { preset, cell ->
            PresetTile(
                preset = preset,
                parsed = parsed,
                customImage = customImage,
                packImage = packImage,
                renaming = preset.name == renaming,
                onLoad = { onLoad(preset) },
                // Null is what makes the individual studio's tiles menu-less, and it is one null rather than two
                // because rename and delete are offered together or not at all — they are the same permission over
                // the same library.
                onMenu = onRename?.let { { renaming = preset.name } },
                onDelete = { onDelete(preset.name) },
                modifier = Modifier.widthIn(max = cell),
            )
        }
    }
}

/**
 * One saved look: the recipe drawn on the subject app, its name beneath.
 *
 * **Tap loads it into the editor; long-press opens the menu** — the layer rail's exact division, and deliberately so,
 * since this is the second grid of tiles on this screen and a gesture that means one thing on the rail must not mean
 * another here. [onMenu] being null is how the individual studio has no menu at all.
 *
 * **The menu is two rows drawn in the tile, not a popup.** A `Popup` is a separate platform window, and this one
 * would open inside a panel that is itself floating glass — so it would be a window over a window, sampling neither.
 * Two verbs do not need a positioner: the rail's own menu exists because it has six and has to flip about an edge.
 *
 * **[renaming] is shown on the tile because the field that renames it is elsewhere** — up at the top of the panel,
 * which is the one place a text field can go without the grid reflowing under the finger that opened it. Without the
 * ring, the row would be editing a name with nothing on screen saying whose.
 */
@Composable
private fun PresetTile(
    preset: IconPreset,
    parsed: ParsedIcon?,
    customImage: (path: String) -> Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> Drawable?,
    renaming: Boolean,
    onLoad: () -> Unit,
    onMenu: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // The ring above the clip, as everywhere else in this studio: both are the same rounded rect, and a
                // rounded clip is a hardware outline clip with no antialiasing, so from inside one a ring loses the
                // corners it traces.
                .then(if (renaming) Modifier.border(2.dp, StudioContentColor, PresetTileShape) else Modifier)
                .clip(PresetTileShape)
                .background(Color.White.copy(alpha = 0.06f))
                .combinedClickable(
                    onClick = { if (menu) menu = false else onLoad() },
                    onLongClick = onMenu?.let { { menu = true } } ?: {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            parsed?.let {
                IconPreview(
                    icon = it,
                    layerSet = preset.appearance.layerSet,
                    modifier = Modifier.fillMaxSize(PresetIconFraction),
                    customImage = customImage,
                    packImage = packImage,
                )
            }

            if (menu) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(PresetTileShape)
                        .background(Color.Black.copy(alpha = 0.72f)),
                    verticalArrangement = Arrangement.Center,
                ) {
                    PresetMenuRow(
                        icon = Icons.Default.DriveFileRenameOutline,
                        label = "Rename",
                        onClick = {
                            menu = false
                            onMenu?.invoke()
                        },
                    )
                    PresetMenuRow(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Text(
            text = preset.name,
            color = StudioContentColor.copy(alpha = if (renaming) 1f else 0.75f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One verb of a tile's menu: a glyph and a word, the whole row tappable. */
@Composable
private fun PresetMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = StudioContentColor,
            modifier = Modifier.size(PresetMenuGlyph),
        )
        Text(
            text = label,
            color = StudioContentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Naming the current recipe and putting it in the library — or, when [renaming] is set, giving a saved one a new name.
 *
 * **One row for both, because they are the same act on the same field.** A second field would be a second place to
 * type a preset's name, and the panel would have to hold one of them empty and inert.
 *
 * **Its own composable so the text field's state lives with the control that uses it**, which is what makes the field
 * genuinely absent in the individual studio rather than merely undrawn — with the state hoisted into
 * [PresetsControls] there would be a buffer allocated for a field that never appears. It also scopes a half-typed name
 * to the row being on screen, which is the behavior a user expects when they close the panel.
 */
@Composable
private fun PresetNameRow(renaming: String?, onCommit: (String) -> Unit, onCancel: () -> Unit) {
    val nameState = rememberTextFieldState()
    val name by remember { derivedName(nameState) }

    // Seeds the field with the old name when a rename starts, and empties it when one ends — so the row always shows
    // what it is about to write. Keyed on the preset rather than on `Unit`, since the menu can move from one tile to
    // another without passing through null.
    LaunchedEffect(renaming) {
        nameState.setTextAndPlaceCursorAtEnd(renaming ?: "")
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorphicTextField(
            state = nameState,
            placeholder = if (renaming == null) "Name this look" else "Rename",
            modifier = Modifier.fillMaxWidth(if (renaming == null) 0.7f else 0.55f),
        )
        // Disabled until there is a name, because an unnamed preset is one nothing could tell from another.
        Text(
            text = if (renaming == null) "save" else "rename",
            color = StudioContentColor.copy(alpha = if (name.isEmpty()) 0.35f else 1f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(enabled = name.isNotEmpty()) {
                    onCommit(name)
                    nameState.setTextAndPlaceCursorAtEnd("")
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        if (renaming != null) {
            Text(
                text = "cancel",
                color = StudioContentColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/** The trimmed name currently typed. Its own derivation so the save control reads one value, not the raw buffer. */
private fun derivedName(state: androidx.compose.foundation.text.input.TextFieldState) =
    androidx.compose.runtime.derivedStateOf { state.text.toString().trim() }

/** Three across, which at panel width is a tile big enough to read an icon in. */

/** Between tiles on both axes. */
private val PresetGridSpacing = 8.dp

/** How wide a tile may get, whatever share of the panel its cell was handed. */
private val PresetTileMax = 96.dp

/** As tall as the grid gets before it scrolls — the panel is glass over a canvas, not a page. */
private val PresetGridMaxHeight = 240.dp

/** The plate a preset is drawn on, and the shape the renaming ring traces. One value, since the two must agree. */
private val PresetTileShape = RoundedCornerShape(12.dp)

/** The icon's share of a tile — the plate is the ground it is read against, so it keeps a margin. */
private const val PresetIconFraction = 0.72f

/** A menu row's glyph, sized against the row's own word rather than the tile. */
private val PresetMenuGlyph = 14.dp
