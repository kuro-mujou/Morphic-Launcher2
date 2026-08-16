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
import inkspire.morphic.core.model.icon.IconShape
import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.data.settings.IconPreset

/**
 * How tall a panel may grow. The rest of the screen is the work, and it stays visible.
 *
 * **The cap is on the whole panel, not on its scrolling part** — the pinned header and the scroll together — which is
 * what makes pinning free: a header takes space *from* the scroll rather than adding it to the panel, so the section
 * with the tallest one cannot push the canvas up behind it.
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
    val selectTarget: (StudioTarget) -> Unit,
    val update: ((IconLayerSpec) -> IconLayerSpec) -> Unit,
    /** Writes whichever effect list the target owns — the selected layer's, or the whole icon's. */
    val updateEffects: ((List<LayerEffect>) -> List<LayerEffect>) -> Unit,
    /** Writes whichever silhouette the target owns, on the same terms. See `IconStudioViewModel.pickShape`. */
    val pickShape: (IconShape?) -> Unit,
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
 * **Two bands: a pinned header and a scroll.** The header is measured first and the scroll takes what is left of
 * [PanelMaxHeight], which is what lets the header stay put without the panel growing to fit it.
 *
 * There was a third — a footer, for controls that must not scroll away because they act on what is scrolling. It was
 * added for the layer stack's reorder buttons, which then moved to `StudioLayerRail`, and it spent the time since as
 * a `when` returning `Unit` for all six sections. Removed rather than kept for a future consumer: the shape is two
 * lines to bring back, and an empty band is a claim about the layout that is not true. The thing that *did* need
 * pinning — an open effect entry's header — turned out to belong at the top, where the way back out is.
 */
@Composable
fun StudioToolPanel(
    tool: StudioTool,
    state: IconStudioState,
    actions: StudioActions,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    // **Built here rather than in the `when` below, because two bands need it**: the pinned header, when an effect
    // entry is open, and the body that draws that entry's controls. Deriving it twice is two answers to "which
    // effects am I editing?" that a selection change could separate.
    val effectTarget = state.selectedLayer?.let(EffectTarget::Layer)
        ?: EffectTarget.Composite(state.editing.effects)
    val effectEntry = rememberEffectEntryState()

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
            // default top-anchors the content, so a growing panel would hold its top edge still and extend downward
            // over the tool bar — the opposite of coming out from behind it. Anchored to the bottom, the lower edge
            // stays against the bar and the panel opens upward.
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                alignment = Alignment.BottomStart,
            )
            .heightIn(max = PanelMaxHeight)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // **The header band, and an open effect entry takes it over.** A section's own title is the panel's job —
        // that is what this host settles — and an entry's header is a title with a way back and a switch beside it.
        // It was the first thing inside the scroll, so on a long section the way *out* scrolled off the top; a
        // control for leaving a place has to stay where the place is.
        //
        // This is the one section the host knows anything about, and the reason is that it is the only one with a
        // second level to be inside of. What it knows is exactly that — [EffectEntryState] holds which entry is
        // open, and the rendering of the header stays in `StudioEffects` beside the entries it names.
        val openEntry = effectEntry.open?.takeIf { tool == StudioTool.EFFECTS && it in effectTarget.slices }
        if (openEntry != null) {
            EffectHeader(
                slice = openEntry,
                target = effectTarget,
                onBack = { effectEntry.open(null) },
                onEffects = actions.updateEffects,
                onCommit = actions.commit,
            )
        } else {
            PanelHeader(
                tool = tool,
                spec = state.selectedLayer.takeIf { tool.actsOnLayer },
                // Named only where the distinction is live: a section that is not about a layer would otherwise say
                // "Whole icon" on the composite and nothing on a layer, which reads as a state rather than a scope.
                composite = state.editingComposite && tool.actsOnLayer,
            )
        }

        // **`weight(1f, fill = false)` is what makes the header win the space it needs.** It is measured first and
        // the scroll takes what is left, so a pinned row is subtracted from the scrollable area rather than added to
        // the panel. `fill = false` is the half that is easy to miss: with it filling, a two-control section would
        // stretch its scroll to the full cap and every panel would be the same height whatever it held.
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

                // **The second section both targets reach.** Read through `selectedLayer` rather than through a
                // second nullable, so "a layer, or else the whole icon" is asked once — an `?:` here would fall
                // through to the composite's shape for a *layer* that simply has none.
                StudioTool.SHAPE -> when (val spec = state.selectedLayer) {
                    null -> ShapeControls(
                        shape = state.editing.shape,
                        // No anchor on the composite — see `IconLayerSet.shape`. `update` is still handed over
                        // rather than being made nullable: it is what the absent control would have driven, and on
                        // the composite it is already a no-op, since there is no selected layer to transform.
                        anchor = null,
                        onPick = actions.pickShape,
                        onUpdate = actions.update,
                        onCommit = actions.commit,
                    )

                    else -> ShapeControls(
                        shape = spec.shape,
                        anchor = spec.shapeAnchor,
                        onPick = actions.pickShape,
                        onUpdate = actions.update,
                        onCommit = actions.commit,
                    )
                }

                // **The one section both targets reach**, which is what the composite exists for. The target is
                // resolved above rather than passed down as a nullable spec, so the panel's own `when` cannot be
                // handed a layer's opacity control with no layer behind it.
                StudioTool.EFFECTS -> EffectsControls(
                    target = effectTarget,
                    entry = effectEntry,
                    onEffects = actions.updateEffects,
                    onLayer = actions.update,
                    onCommit = actions.commit,
                )

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

/** The section's name, and — for a per-layer section — what it is pointed at. */
@Composable
private fun PanelHeader(tool: StudioTool, spec: IconLayerSpec?, composite: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tool.label, color = StudioContentColor, style = MaterialTheme.typography.titleSmall)
        if (composite) {
            Text(
                text = "Whole icon",
                color = StudioContentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
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
