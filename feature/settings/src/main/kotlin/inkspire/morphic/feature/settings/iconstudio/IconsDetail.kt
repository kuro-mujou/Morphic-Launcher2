package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.data.settings.IconPreset
import org.koin.androidx.compose.koinViewModel

/**
 * The **Icons** section: a hub, not an editor.
 *
 * L1's conclusion, and it is worth stating because the alternative is what it started with. Its icon settings were
 * the editor itself, hosted in the settings detail pane and built out of settings-list vocabulary; its own docs
 * conclude that this was the whole problem. So the pane became a place to choose *what* to edit, and the editing
 * moved to a full-screen destination — which is right for a second reason here, that a settings pane shares the
 * screen with the section list on a tablet, and a creative workspace cannot have half a screen.
 *
 * **Adaptive, as L1's dashboard was**: in portrait the two actions sit side by side above the presets; in landscape
 * they stack in a narrow column on the left with the presets filling the rest, so the short height is not spent on
 * two cards' worth of empty space.
 */
@Composable
internal fun IconsDetail(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    // The same navigation shape as `WallpaperDetail`, and for the same reason: the destination belongs to *this*
    // feature, so the module already knows it exists and there is nothing for `app` to be told. Contrast the
    // launcher shell, which takes `onOpenSettings` as an action precisely because settings is not its business.
    val editAll = { navigator.goTo(IconStudioRoute.Global()) }
    val editOne = { navigator.goTo(IconStudioRoute.App()) }

    if (currentDeviceConfiguration().isLandscape) {
        Row(modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(0.4f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardAction("Edit all icons", AllSubtitle, Icons.Outlined.Palette, Modifier.weight(1f), editAll)
                DashboardAction("Edit specific apps", OneSubtitle, Icons.Outlined.Apps, Modifier.weight(1f), editOne)
            }
            PresetsGrid(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardAction("Edit all icons", AllSubtitle, Icons.Outlined.Palette, Modifier.weight(1f), editAll)
                DashboardAction("Edit specific apps", OneSubtitle, Icons.Outlined.Apps, Modifier.weight(1f), editOne)
            }
            PresetsGrid(Modifier.fillMaxWidth())
        }
    }
}

/**
 * Subtitles that say what each choice *does to the device*, not what screen it opens.
 *
 * The distinction matters here more than usual: the two lead to the same editor, and what separates them is only
 * what it will be editing — a recipe every app inherits, or one app's own. A user who picks wrong finds out after
 * changing every icon they own.
 */
private const val AllSubtitle = "One recipe every app inherits"
private const val OneSubtitle = "Give one app a look of its own"

/** One of the two primary actions: a squarish card, tappable whole. */
@Composable
private fun DashboardAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.content)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    }
}

/**
 * The saved-recipe library, and the one place a preset is *applied*.
 *
 * **A grid of squares, each drawing its own recipe on a real app.** A preset is a *look*, so a list of words was the
 * one thing this could not be: two recipes differing in a bloom's angle read as two identical rows. Rendering one
 * costs almost nothing here — `AppIcon` takes an explicit appearance and bakes through `IconRenderManager`, so a
 * tile is one bake, cached on the same key every icon on the device already uses, and it draws the preset's plate
 * as well as its recipe.
 *
 * **The tile is the square and the name sits under it**, which is the category card's rule and holds for its reason: a
 * title inside the fill eats into the picture the tile exists to show, and reads as a header bar rather than a label.
 *
 * **Tapping applies, at once and with no confirm.** That reverses what this pane used to do — a tap opened the studio
 * loaded with the preset, on the reasoning that restyling every icon deserves a look first — and the tile's own
 * preview is what pays for the reversal: the look is on screen before the finger lands, so looking first happens by
 * reading rather than by navigating. The studio is still one tap away, as **Edit** in the tile's menu, by the same
 * route the old tap took.
 *
 * **Plain rows, not a lazy grid**, for the effect grid's reason: a library is a handful of tiles, so laziness buys
 * nothing and costs a scroller nested inside the pane's own.
 */
@Composable
private fun PresetsGrid(modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current
    val navigator = LocalNavigator.current
    val viewModel: IconsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applied = state.appliedPreset

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Presets", style = MaterialTheme.typography.titleSmall, color = colors.content)

        if (state.presets.isEmpty()) {
            // Says how to make one rather than that there are none — an empty library is the starting state, and the
            // only thing a reader needs from it is the next step.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Save a look from the icon studio and it appears here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.contentMuted,
                )
            }
        }

        BoxWithConstraints {
            // Capped, so the extra width on a tablet goes to the gaps between tiles rather than making four huge
            // squares of a four-preset library — the effect grid's own arrangement and its reason.
            val cell = ((maxWidth - PresetGridSpacing * (PresetColumns - 1)) / PresetColumns)
                .coerceAtMost(PresetTileMax)

            Column(verticalArrangement = Arrangement.spacedBy(PresetGridSpacing)) {
                state.presets.chunked(PresetColumns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PresetGridSpacing)) {
                        row.forEach { preset ->
                            // The cell takes the share, the tile a bounded slice of it — so a short last row is
                            // spread like the others rather than stretched, which is what the spacers are for.
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                PresetTile(
                                    preset = preset,
                                    sample = state.sample,
                                    applied = preset.name == applied,
                                    onApply = { viewModel.apply(preset) },
                                    onEdit = { navigator.goTo(IconStudioRoute.Global(preset.name)) },
                                    onDelete = { viewModel.delete(preset.name) },
                                    modifier = Modifier.widthIn(max = cell),
                                )
                            }
                        }
                        repeat(PresetColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * One saved look: the recipe drawn on a real app, its name beneath, and a menu for the two things that are not
 * applying it.
 *
 * **The menu opens two ways, and both are load-bearing.** The three dots are there because the tap is spent on
 * applying, so without a visible affordance Edit and Delete would be reachable only by a gesture this pane teaches
 * nowhere — nothing else in it is long-pressable, so nobody would try. Long-press works as well because it is what
 * every other menu in this launcher uses (the layer rail, an item on the home screen, a surface), and a gesture that
 * is right everywhere else must not be wrong here. One menu, one verb list, two ways in.
 *
 * **[applied] is a ring above the clip, never a border under it.** Both are the same rounded rect, and a rounded clip
 * is a hardware outline clip with no antialiasing — inside one, a ring loses the corners it traces. The same rule as
 * the studio's swatches and its layer tiles.
 *
 * That ring is also what makes an immediate apply legible: without it, a tap changes every icon on the device and
 * this pane shows nothing at all.
 */
@Composable
private fun PresetTile(
    preset: IconPreset,
    sample: ComponentKey?,
    applied: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    var menu by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(if (applied) Modifier.border(PresetRing, colors.accent, PresetTileShape) else Modifier)
                .clip(PresetTileShape)
                .background(colors.surfaceElevated)
                .combinedClickable(onClick = onApply, onLongClick = { menu = true }),
        ) {
            // Null only until the app cache answers, so the tile is its plate alone for a frame rather than a
            // placeholder icon that would flash and be replaced.
            sample?.let { component ->
                // The whole appearance, so a tile shows the plate a preset carries as well as its recipe —
                // which is what "save as preset" saves. With no wallpaper provided in this pane the plate draws its
                // scrim, the same fallback every frosted surface has: it says a plate is there without pretending
                // to be glass.
                AppIcon(
                    component = component,
                    contentDescription = null,
                    sizePx = PresetIconPx,
                    appearance = preset.appearance,
                    modifier = Modifier.align(Alignment.Center).fillMaxSize(PresetIconFraction),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(PresetMenuButton)
                    .clip(RoundedCornerShape(50))
                    .clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options for ${preset.name}",
                    tint = colors.contentMuted,
                    modifier = Modifier.size(16.dp),
                )
            }

            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                // Opens the studio *loaded with* this preset and unsaved, which is the route this pane's tap used to
                // take — so editing a preset and applying one are the same screen reached by different verbs.
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menu = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }

        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (applied) colors.content else colors.contentMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Three across: at a pane's width that is a ~100dp square, which is a picture rather than a button. */
private const val PresetColumns = 3

/** Between tiles on both axes. */
private val PresetGridSpacing = 12.dp

/** How wide a tile may get, whatever share of the pane its cell was handed. */
private val PresetTileMax = 132.dp

/** The plate a preset is drawn on, and the shape its applied ring traces. One value, since the two must agree. */
private val PresetTileShape = RoundedCornerShape(20.dp)

/** The ring marking the preset currently in force. */
private val PresetRing = 2.dp

/** The icon's share of the tile — the tile is the ground it is read against, so it keeps a visible margin. */
private const val PresetIconFraction = 0.6f

/** What a tile bakes at: a library is a handful of icons, so one size for every tile is one bake each. */
private const val PresetIconPx = 192

/** The menu target, kept small enough not to sit over the icon it shares a corner with. */
private val PresetMenuButton = 28.dp
