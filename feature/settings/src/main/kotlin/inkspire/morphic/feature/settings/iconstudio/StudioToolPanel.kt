package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import inkspire.morphic.core.model.icon.IconLayerSpec
import inkspire.morphic.data.settings.IconPreset

/** How tall a panel may grow before its contents scroll. The rest of the screen is the work, and it stays visible. */
private val PanelMaxHeight = 300.dp

/**
 * Every command the studio's panels can issue, in one value.
 *
 * **A holder rather than fifteen parameters threaded through the host into a section.** The host does not use any of
 * these itself — it hands subsets down — so as separate parameters they would be fifteen names restated in two
 * signatures, and adding one command would reshape both. Grouping them also says the true thing about them: they are
 * one surface, the studio's, and a section takes the part of it that it drives.
 *
 * `@Immutable` because it is exactly that once built: the lambdas are the ViewModel's methods and the ViewModel does
 * not change for the life of the screen. Build it in a `remember` so it is one instance rather than one per frame.
 */
@Immutable
data class StudioActions(
    val selectLayer: (Int) -> Unit,
    val update: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    val commit: () -> Unit,
    val toggleVisible: () -> Unit,
    val move: (up: Boolean) -> Unit,
    val addLayer: () -> Unit,
    val removeLayer: () -> Unit,
    val pickImage: () -> Unit,
    val pickPack: (String) -> Unit,
    /** Null in the global studio, where a *named* pack drawable would be inherited by every app. */
    val browsePack: ((String) -> Unit)?,
    /**
     * Null in the individual studio, where the library is read-only — the same nullable-means-absent shape as
     * [browsePack], pointed the other way. See `PresetsControls` for why a look is made globally and only used here.
     */
    val savePreset: ((String) -> Unit)?,
    val loadPreset: (IconPreset) -> Unit,
    val deletePreset: (String) -> Unit,
    val reset: () -> Unit,
)

/**
 * The panel behind whichever [StudioTool] is selected: one sheet of the shared glass, a header, and that section's
 * controls.
 *
 * **One host for every section, so a section is only its controls.** Each used to bring its own surface, title and
 * scroll — which is how two panels end up different heights with different corners — and all three are settled here
 * instead. It is also the piece a landscape arrangement re-points at a side rail without touching a single section.
 *
 * **The header names the selected *layer*, not just the tool**, and that is the one thing the bar cost us. While the
 * stack was permanently on screen, "which layer am I editing?" was answered by looking at it; now the stack is behind
 * its own entry, so every per-layer section has to say what it is acting on or a slider becomes a guess.
 *
 * **Capped and scrolling, not sized to fit.** A section's length varies by an order of magnitude — Shape is a grid of
 * chips, Effects is nine controls — and a panel that grew to whatever its contents wanted would bury the icon at
 * exactly the moment the user was colouring it.
 */
@Composable
fun StudioToolPanel(
    tool: StudioTool,
    state: IconStudioState,
    actions: StudioActions,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .studioSurface(hazeState, shape = RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PanelHeader(tool = tool, spec = state.selectedLayer.takeIf { tool.actsOnLayer })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PanelMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Exhaustive over the bar, so a new entry cannot be added without a panel to open — the same reason
            // `AppsScreen` lists its unbuilt layouts individually rather than behind an `else`.
            when (tool) {
                StudioTool.LAYERS -> LayerStackRows(
                    state = state,
                    onSelectLayer = actions.selectLayer,
                    onToggleVisible = actions.toggleVisible,
                    onMove = actions.move,
                    onAdd = actions.addLayer,
                    onRemove = actions.removeLayer,
                )

                // The four per-layer sections. `selectedLayer` is null only if the set were empty, which
                // `IconLayerSet`'s own `init` forbids — so this is a guard rather than a state to design for.
                StudioTool.SOURCE -> state.selectedLayer?.let { spec ->
                    SourceControls(
                        spec = spec,
                        packs = state.packs,
                        onUpdate = actions.update,
                        onPickImage = actions.pickImage,
                        onPickPack = actions.pickPack,
                        onBrowsePack = actions.browsePack,
                    )
                }

                StudioTool.TRANSFORM -> state.selectedLayer?.let { spec ->
                    TransformControls(spec, actions.update, actions.commit)
                }

                StudioTool.SHAPE -> state.selectedLayer?.let { spec ->
                    ShapeControls(spec, actions.update)
                }

                StudioTool.EFFECTS -> state.selectedLayer?.let { spec ->
                    EffectsControls(spec, actions.update, actions.commit)
                }

                StudioTool.PRESETS -> PresetsControls(
                    presets = state.presets,
                    onSave = actions.savePreset,
                    onLoad = actions.loadPreset,
                    onDelete = actions.deletePreset,
                )

                StudioTool.MORE -> MoreControls(subject = state.subject, onReset = actions.reset)
            }
        }
    }
}

/** The section's name, and — for a per-layer section — which layer it is pointed at. */
@Composable
private fun PanelHeader(tool: StudioTool, spec: IconLayerSpec?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tool.label, color = StudioContentColor, style = MaterialTheme.typography.titleSmall)
        spec?.let {
            Text(
                text = "${it.role.label} · ${it.source.label}",
                color = StudioContentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * The leftovers section: Reset, and where the session-level extras will land.
 *
 * **Reset says what it will do to *this* subject**, because it is the same verb pointed at two different things — an
 * app stops having its own recipe and inherits the global default again; the global default goes back to plain app
 * icons. A row labelled "Reset" alone would be asking the user to guess which.
 */
@Composable
private fun MoreControls(subject: StudioSubject, onReset: () -> Unit) {
    Text(
        text = when (subject) {
            is StudioSubject.App -> "Reset drops this app's own icon and follows the global default again."
            is StudioSubject.Global -> "Reset returns every icon to the app's own artwork."
            StudioSubject.Unchosen -> "Choose an app first."
        },
        color = StudioContentColor.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall,
    )
    ChoiceRow(
        label = "Reset",
        selected = false,
        onClick = { if (subject !is StudioSubject.Unchosen) onReset() },
    )
}

/**
 * Whether this section edits the selected layer, which is what decides if the panel header names one.
 *
 * An extension here rather than a field on [StudioTool] because it is a fact about how the *panel* is drawn, not about
 * what the section is — the enum's job is the bar.
 */
private val StudioTool.actsOnLayer: Boolean
    get() = when (this) {
        StudioTool.SOURCE, StudioTool.TRANSFORM, StudioTool.SHAPE, StudioTool.EFFECTS -> true
        StudioTool.LAYERS, StudioTool.PRESETS, StudioTool.MORE -> false
    }
