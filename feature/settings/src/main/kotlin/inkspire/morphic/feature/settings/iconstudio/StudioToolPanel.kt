package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.animation.animateContentSize
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

/**
 * How tall a panel may grow. The rest of the screen is the work, and it stays visible.
 *
 * **The cap is on the whole panel, not on its scrolling part** — header, scroll and pinned footer together — which is
 * what makes a footer free: pinning a row takes space from the scroll rather than adding it to the panel, so a section
 * that grows one cannot push the canvas up behind it.
 */
private val PanelMaxHeight = 320.dp

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
    val pickAppDefault: () -> Unit,
    val pickSolidFill: () -> Unit,
    val toggleMonochrome: () -> Unit,
    val toggleNormalize: () -> Unit,
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
 * **The header names the selected *layer*, not just the tool.** This was the answer to the bar having swallowed the
 * layer list, and it survives the rail bringing that list back: the rail says which tile is lit, the header says what
 * that layer *is* — its role and where its pixels come from — which is the thing a 44dp thumbnail cannot. Between
 * them a slider is never a guess about which layer it moves.
 *
 * **Capped and scrolling, not sized to fit.** A section's length varies by an order of magnitude — Shape is a grid of
 * chips, Effects is nine controls — and a panel that grew to whatever its contents wanted would bury the icon at
 * exactly the moment the user was coloring it.
 *
 * **Three bands: header, scroll, footer.** The outer two are measured first and the scroll takes what is left of
 * [PanelMaxHeight], which is what lets a section pin a row ([SectionFooter]) without the panel growing to fit it.
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
            // **The panel's own height is what animates, and it is the only thing that does.** Every reason it changes
            // arrives here: switching to a section with more controls, adding or removing a layer, a section growing a
            // row of its own. Animating it in one place is why the layer list no longer animates its own size — nested
            // size animations mean the outer one chases a target that is itself still moving, which reads as lag.
            //
            // **Placed after `studioSurface` so the glass is drawn at the animated size**, not at the target: a
            // modifier earlier in the chain wraps the ones after it, so the blur and the clip see what this reports.
            // And before `heightIn`, so what is animated *towards* is already capped.
            //
            // **Anchored `BottomStart`, which is the half that matters for a panel at the bottom of the screen.** The
            // default top-anchors the content, so a growing panel would hold the header still and clip the footer —
            // hiding the pinned buttons for the length of the animation. Anchored to the bottom, the footer stays put
            // and the panel opens upward from behind it.
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                alignment = Alignment.BottomStart,
            )
            .heightIn(max = PanelMaxHeight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PanelHeader(tool = tool, spec = state.selectedLayer.takeIf { tool.actsOnLayer })

        // **`weight(1f, fill = false)` is what makes the header and the footer win the space they need.** The header
        // and footer are measured first and the scroll takes what is left, so a pinned row is subtracted from the
        // scrollable area rather than added to the panel. `fill = false` is the half that is easy to miss: with it
        // filling, a two-control section would stretch its scroll to the full cap and every panel would be the same
        // height whatever it held.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Exhaustive over the bar, so a new entry cannot be added without a panel to open — the same reason
            // `AppsScreen` lists its unbuilt layouts individually rather than behind an `else`.
            when (tool) {
                // The per-layer sections. `selectedLayer` is null only if the set were empty, which
                // `IconLayerSet`'s own `init` forbids — so this is a guard rather than a state to design for.
                StudioTool.SOURCE -> state.selectedLayer?.let { spec ->
                    SourceControls(
                        spec = spec,
                        packs = state.packs,
                        // The state's, not the actions': it depends on the *selected layer* as well as on the studio,
                        // so it changes as the selection moves and cannot be fixed when the actions are built.
                        allowsFixedSource = state.canUseFixedSource,
                        onUpdate = actions.update,
                        // Every source change is discrete — there is no gesture to punctuate — so each records at once
                        // and undo steps over it. `onUpdate` alone is the *live* path, which deliberately records
                        // nothing; a section that only ever makes discrete edits needs both.
                        onCommit = actions.commit,
                        onPickImage = actions.pickImage,
                        onPickAppDefault = actions.pickAppDefault,
                        onPickSolidFill = actions.pickSolidFill,
                        onToggleMonochrome = actions.toggleMonochrome,
                        onToggleNormalize = actions.toggleNormalize,
                        onPickPack = actions.pickPack,
                        onBrowsePack = actions.browsePack,
                    )
                }

                StudioTool.TRANSFORM -> state.selectedLayer?.let { spec ->
                    TransformControls(spec, actions.update, actions.commit)
                }

                StudioTool.SHAPE -> state.selectedLayer?.let { spec ->
                    ShapeControls(spec, actions.update, actions.commit)
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

        SectionFooter(tool = tool, state = state, actions = actions)
    }
}

/**
 * The row a section keeps at the bottom of the panel, below the scroll — or nothing, for the sections that have none.
 *
 * **A footer is for controls that must not scroll away because they act on what is scrolling.** The layer stack is the
 * case and today the only one: its buttons operate on the *selected* layer, so a deep stack would put them below the
 * fold exactly when they are most needed, and adding a layer would push "add a layer" further out of reach.
 *
 * **Exhaustive rather than an `if`**, so a new section has to decide whether it has one instead of silently not — the
 * same reason the body's `when` lists every value. Sections without a footer say `Unit` and cost nothing.
 */
@Composable
private fun SectionFooter(tool: StudioTool, state: IconStudioState, actions: StudioActions) {
    when (tool) {
        StudioTool.SOURCE,
        StudioTool.TRANSFORM,
        StudioTool.SHAPE,
        StudioTool.EFFECTS,
        StudioTool.PRESETS,
        StudioTool.MORE,
        -> Unit
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
 * icons. A row labeled "Reset" alone would be asking the user to guess which.
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
        StudioTool.PRESETS, StudioTool.MORE -> false
    }
