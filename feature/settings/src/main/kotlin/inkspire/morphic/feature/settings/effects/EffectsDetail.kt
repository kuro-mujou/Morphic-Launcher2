package inkspire.morphic.feature.settings.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.backdrop.washColor
import inkspire.morphic.core.designsystem.backdrop.wallpaperTone
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.component.color.MorphicColorPicker
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.BackdropTint
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.component.SettingsSliderRow
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

/**
 * An edit to one of the two effects: **what to change, and nothing about what everything else was.**
 *
 * Every control here hands one of these to two places — the store, where it is applied inside the write, and the live
 * preview, where it is applied to whatever the preview is already showing. That symmetry is the fix for a bug that took
 * two goes: a control that instead *computes* its next value from the effect it last saw is a read-modify-write across
 * an async round-trip, and two controls used in quick succession then overwrite each other's fields. See
 * `EffectsViewModel.editBlur` for the store half and `EffectsDetail`'s `previewBlur` for the preview half.
 */
private typealias BlurEdit = (BackdropEffect.Blur) -> BackdropEffect.Blur

/** [BlurEdit] for the lens. */
private typealias GlassEdit = (BackdropEffect.LiquidGlass) -> BackdropEffect.LiquidGlass

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
private val RowGap = 8.dp

/**
 * **Effects**: how frosted surfaces render over the wallpaper — the one global choice, and its parameters.
 *
 * The port of L1's `EffectsTab`, and structurally the same screen: a chooser, then the controls belonging to whatever is
 * chosen. Four differences, each following from a decision made before this:
 *
 * - **Two entries where there were five.** `Plain`, the two blurs and Material You blurred identically and differed only
 *   in the color of the wash, so the wash became a `BackdropTint` and they became one entry — see [EffectKind]. What is
 *   left to choose between is a blur and a lens, which is what a *segmented control* is for; five options were a
 *   `FlowRow` of chips, and two are not.
 * - **The controls come from the *variant*, not from a ten-field bag.** L1 held every parameter of every effect at once
 *   and used a `when` to decide which subset to draw; here the selected `BackdropEffect` carries only its own, so the
 *   `when` is over the sealed type and the compiler checks the mapping is total.
 * - **A live preview, first on the pane and pinned there** — see [BackdropPreview] for what it is and why the backdrop
 *   it samples is provided at the *pane*. It pins in a `stickyHeader`, the arrangement `SurfaceDetail` uses for the same
 *   reason: the controls are read *through* the picture, so scrolling to reach one must not scroll the picture away. That
 *   is also why this is a `LazyColumn`. **Nothing is titled** — not the pane, not the picture, not the chooser: the app
 *   bar names the section, and a frosted panel and two buttons reading "Blur" and "Liquid glass" say what they are
 *   without a word over them. L1 had no preview here at all.
 * - **The sliders are the icon studio's shape** — name, value and reset over a track flanked by a stepper each side (see
 *   [SettingsSliderRow]). A wash at 28% and one at 30% are hard to tell apart on a photograph, which makes a readout and
 *   a reset worth more here than on a control whose result is a number of columns.
 *
 * **Liquid glass is hidden rather than disabled below API 33.** An effect that silently comes out as a plain blur is
 * worse than one that is not offered, so the entry goes and the reason is stated — L1's wording, kept.
 */
@Composable
internal fun EffectsDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<EffectsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current

    // Which way the device is held, for the rotating pair's sake — the pane is where the window is.
    val windowSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(windowSize) {
        viewModel.setOrientation(
            if (windowSize.width > windowSize.height) Orientation.LANDSCAPE else Orientation.PORTRAIT,
        )
    }

    // **The dragged effect, which is what the preview draws.** Null means "nothing is being dragged", so the preview
    // follows the stored value; a slider's `onPreview` fills it per frame and its `onCommit` writes the real thing, after
    // which the stored value catches up and this can be dropped. Every parameter but the blur is a draw-time read, so
    // this is the whole of what makes them preview live.
    var dragged by remember(state.effect) { mutableStateOf<BackdropEffect?>(null) }
    val previewed = dragged ?: state.effect

    // A commit landing is the end of the gesture, so stop reporting a dragged strength and let the live blur follow the
    // store again. Keyed on the same value that clears `dragged` above, because they are the same event.
    LaunchedEffect(state.effect) { viewModel.previewStrength(null) }

    // **Applying an edit to what is already previewed, rather than to what the pane last saw.** This is the preview half
    // of the lost update `EffectsViewModel.editBlur` describes, and it is the half a first fix missed on the grounds that
    // a stale preview is discarded next frame. True of a *drag*, false of a *tap*: a stepper's preview stands until its
    // write comes back round, so stepping the tint and then the blur built the blur's preview from an effect whose tint
    // amount was still the old one — and repeated taps never caught up. Reading `dragged` here is a snapshot-state read,
    // so it is always the current one, and successive edits to different fields accumulate the way the store's do.
    fun previewBlur(edit: BlurEdit) {
        val base = dragged ?: state.effect
        if (base !is BackdropEffect.Blur) return
        val next = edit(base)
        dragged = next
        viewModel.previewStrength(next.strength)
    }

    fun previewGlass(edit: GlassEdit) {
        val base = dragged ?: state.effect
        if (base !is BackdropEffect.LiquidGlass) return
        val next = edit(base)
        dragged = next
        viewModel.previewStrength(next.blur)
    }

    // **Which picture the card samples, and the one thing the pane knows that the state holder does not.** A dragged
    // *blur* needs the quarter-size picture that can be re-blurred per frame; a dragged tint or lens parameter is a
    // draw-time read of the same picture, so dropping to the smaller one would cost resolution for nothing. Comparing
    // the strengths is what tells the two apart, and it settles back the moment the commit lands.
    val draggingBlur = dragged != null && dragged?.blurStrength != state.effect.blurStrength
    val previewImage = if (draggingBlur) state.draggingImage else state.backdropImage

    LazyColumn(modifier = modifier.fillMaxSize()) {
        // **The preview comes first and carries no heading.** The app bar already reads "Effects" and a picture of a
        // frosted panel does not need to be told it is a preview — what it is is self-evident, and a word above it costs
        // a row of the one thing this pane is for. It is pinned, so the controls scroll *under* it: they are read
        // through the picture, which is `SurfaceDetail`'s reason for the same arrangement.
        //
        // Opaque behind the padding so scrolling content does not show through it. The picture itself punches to the
        // wallpaper inside its own box, which is why that background does not defeat it.
        stickyHeader(key = "preview") { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(top = RowGap),
            ) {
                BackdropPreview(
                    effect = previewed,
                    image = previewImage,
                    accent = state.backdropAccent,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }

        item(key = "controls") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                // **The chooser sits under the preview, unlabeled**, which is the order the eye wants: the picture is
                // what you came to look at, and this is the first thing you reach for to change it. A "Effect" heading
                // over two buttons reading "Blur" and "Liquid glass" names the category they are already named by.
                //
                // Below API 33 there is one entry, and a segmented control of one is a label — so the `if` is what
                // keeps it from drawing as a single dead-looking button.
                if (state.liquidGlassAvailable) {
                    MorphicSegmentedButtons(
                        options = EffectKind.entries.map { it.label },
                        selectedIndex = state.effect.kind.ordinal,
                        onSelect = { viewModel.select(EffectKind.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "Liquid glass is only available on Android 13 and above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentMuted,
                    )
                }

                // Exhaustive over the sealed type rather than over the chooser, so a new variant fails to compile here
                // until it has controls — the same rule `AppsScreen` follows for an unbuilt layout.
                when (val effect = state.effect) {
                    is BackdropEffect.Blur -> BlurControls(
                        effect = effect,
                        // The swatches resolve their colors the way the renderer does, which needs the wallpaper's
                        // accent — and they sit outside the preview, so it is handed to them rather than read.
                        tone = wallpaperTone(state.backdropAccent?.let(::Color)),
                        onEdit = viewModel::editBlur,
                        onPreview = ::previewBlur,
                    )
                    is BackdropEffect.LiquidGlass -> GlassControls(
                        effect = effect,
                        onEdit = viewModel::editGlass,
                        onPreview = ::previewGlass,
                    )
                }
            }
        }
    }
}

/**
 * The blur's controls: how far it softens, and what is washed over it.
 *
 * **The wash is two controls and they are not interchangeable** — a color, chosen from five swatches, and an amount. The
 * color is a discrete choice, so it commits on the tap; the amount is a drag, so it previews per frame and commits on
 * release. Splitting them is what lets the amount slider carry no name of its own: the swatch row directly above it *is*
 * the label, and a row headed "Tint" containing a control also headed "Tint" says one thing twice.
 */
@Composable
private fun ColumnScope.BlurControls(
    effect: BackdropEffect.Blur,
    tone: Color,
    onEdit: (BlurEdit) -> Unit,
    onPreview: (BlurEdit) -> Unit,
) {
    SettingsSliderRow(
        label = "Blur",
        what = "blur",
        value = effect.strength,
        valueRange = 0f..1f,
        default = BlurDefaults.strength,
        valueLabel = ::percent,
        // One transform, sent to both: the store applies it inside the write, the preview applies it to what it is
        // already showing. Neither is computed from a snapshot of the whole effect — see [BlurEdit].
        onCommit = { value -> onEdit { it.copy(strength = value) } },
        onPreview = { value -> onPreview { it.copy(strength = value) } },
    )

    SettingsSectionHeader("Tint")
    TintSwatches(
        selected = effect.tint,
        customArgb = effect.customTintArgb,
        tone = tone,
        onSelect = { tint -> onEdit { it.copy(tint = tint) } },
    )

    if (effect.tint == BackdropTint.CUSTOM) {
        CustomTintPicker(
            argb = effect.customTintArgb,
            onPreview = { argb -> onPreview { it.copy(customTintArgb = argb) } },
            onCommit = { argb -> onEdit { it.copy(customTintArgb = argb) } },
        )
    }

    // **Absent rather than disabled**, which is this section's rule where the gate is a discrete choice made elsewhere: a
    // tint of NONE has no wash for an amount to describe, and the layout settles on the tap that chose it rather than
    // under a finger already on a slider. (The studio's grain angle is the documented exception, and its gate is a
    // *continuous* control directly above it.)
    if (effect.tint != BackdropTint.NONE) {
        SettingsSliderRow(
            what = "tint",
            value = effect.tintAmount,
            valueRange = 0f..MaxTintAmount,
            default = BlurDefaults.tintAmount,
            valueLabel = ::percent,
            onCommit = { value -> onEdit { it.copy(tintAmount = value) } },
            onPreview = { value -> onPreview { it.copy(tintAmount = value) } },
        )
    }
}

/**
 * The five washes, as the colors they are.
 *
 * **Swatches rather than chips, because these *are* colors** — a row of five words asks the reader to remember what
 * "Wallpaper" resolves to on this one, where a filled circle simply shows them. The colors come from
 * [washColor], the same resolution the renderer uses, so a swatch cannot advertise a wash the surface does not paint.
 *
 * They are drawn **opaque**, at full strength, while the surface paints them at `tintAmount`. That is deliberate: the
 * swatch answers "which color?" and the slider below answers "how much?", and a swatch dimmed to the current amount
 * would answer neither well — at a low amount all five would look like the same pale nothing.
 *
 * `NONE` has no color to show, so it is drawn as an outlined empty circle: the absence of a wash, rather than a wash that
 * happens to be invisible.
 */
@Composable
private fun TintSwatches(
    selected: BackdropTint,
    customArgb: Int,
    tone: Color,
    onSelect: (BackdropTint) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        BackdropTint.entries.forEach { tint ->
            TintSwatch(
                color = tint.washColor(tone, customArgb),
                label = tint.label,
                selected = tint == selected,
                onClick = { onSelect(tint) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One swatch: a filled circle over its name, ringed when it is the selected one.
 *
 * The ring is drawn *outside* the fill rather than over it, so it never changes the color being shown — which a border
 * on the circle itself would, at the edge where a dark wash meets a dark ring.
 */
@Composable
private fun TintSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(SwatchRing)
                .clip(CircleShape)
                .border(
                    width = if (selected) SelectedRing else UnselectedRing,
                    color = if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(SwatchFill)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.content else colors.contentMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * The color picker for [BackdropTint.CUSTOM], expanded in place under the swatches.
 *
 * **Inline rather than in a dialog**, and that is not a style choice: a `Popup` is a separate platform window, so it
 * would sit *over* the preview this pane exists to show — and the punch-through the preview draws through belongs to this
 * pane's own offscreen layer, which a second window is not part of. Mixing a color you cannot see applied is the one
 * thing a color picker here must not ask of anyone.
 *
 * **Live while dragging, written when accepted.** `MorphicColorPicker` reports every change and has no notion of a
 * gesture ending, so every change previews (the card re-washes under the finger) and the button below is what commits.
 * The alternative — writing per change — is a JSON encode and a file write per frame, which is exactly what
 * `SettingsCommitSlider` exists to avoid. Leaving without accepting keeps the stored color, and the preview snapping back
 * is what says so.
 */
@Composable
private fun CustomTintPicker(argb: Int, onPreview: (Int) -> Unit, onCommit: (Int) -> Unit) {
    var picked by remember(argb) { mutableStateOf(argb) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        MorphicColorPicker(
            argb = picked,
            onArgbChange = {
                picked = it
                onPreview(it)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        MorphicButton(
            onClick = { onCommit(picked) },
            style = MorphicButtonStyle.Tonal,
            enabled = picked != argb,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Use this color")
        }
    }
}

/** The lens's six controls: what it is made of, then how it catches the light. */
@Composable
private fun ColumnScope.GlassControls(
    effect: BackdropEffect.LiquidGlass,
    onEdit: (GlassEdit) -> Unit,
    onPreview: (GlassEdit) -> Unit,
) {
    SettingsSectionHeader("Lens")
    SettingsSliderRow(
        label = "Blur",
        what = "blur",
        value = effect.blur,
        valueRange = 0f..1f,
        default = GlassDefaults.blur,
        valueLabel = ::percent,
        onCommit = { value -> onEdit { it.copy(blur = value) } },
        onPreview = { value -> onPreview { it.copy(blur = value) } },
    )
    SettingsSliderRow(
        label = "Refraction",
        what = "refraction",
        value = effect.refraction,
        valueRange = 0f..1f,
        default = GlassDefaults.refraction,
        valueLabel = ::percent,
        onCommit = { value -> onEdit { it.copy(refraction = value) } },
        onPreview = { value -> onPreview { it.copy(refraction = value) } },
    )
    SettingsSliderRow(
        label = "Depth",
        what = "depth",
        value = effect.depth,
        valueRange = 0f..1f,
        default = GlassDefaults.depth,
        valueLabel = ::percent,
        onCommit = { value -> onEdit { it.copy(depth = value) } },
        onPreview = { value -> onPreview { it.copy(depth = value) } },
    )

    SettingsSectionHeader("Light")
    SettingsSliderRow(
        label = "Vibrancy",
        what = "vibrancy",
        value = effect.vibrancy,
        valueRange = 0f..1f,
        default = GlassDefaults.vibrancy,
        valueLabel = ::percent,
        onCommit = { value -> onEdit { it.copy(vibrancy = value) } },
        onPreview = { value -> onPreview { it.copy(vibrancy = value) } },
    )
    SettingsSliderRow(
        label = "Sheen",
        what = "sheen",
        value = effect.sheen,
        valueRange = 0f..1f,
        default = GlassDefaults.sheen,
        valueLabel = ::percent,
        onCommit = { value -> onEdit { it.copy(sheen = value) } },
        onPreview = { value -> onPreview { it.copy(sheen = value) } },
    )
    SettingsSliderRow(
        label = "Dispersion",
        what = "dispersion",
        value = effect.dispersion,
        valueRange = 0f..1f,
        default = GlassDefaults.dispersion,
        valueLabel = ::percent,
        onCommit = { value -> onEdit { it.copy(dispersion = value) } },
        onPreview = { value -> onPreview { it.copy(dispersion = value) } },
    )
}

/** The chooser's labels. Kept beside their use rather than on the enum — a label is this screen's, not the value's. */
private val EffectKind.label: String
    get() = when (this) {
        EffectKind.BLUR -> "Blur"
        EffectKind.GLASS -> "Liquid glass"
    }

/**
 * The swatches' labels.
 *
 * **"Wallpaper", not "Material You"** — and not "Themed" either, though that was the suggestion. Material You is
 * Google's name for the *OS* palette, which is exactly what this wash is not: `LauncherTheme` bridges a monochrome
 * `ColorScheme`, so `colorScheme.primary` here is gray, and the color comes from `WallpaperRepository.accentColor`
 * reading the wallpaper itself. "Themed" points at the same wrong thing in a shorter word. Naming the *source* says
 * what the swatch will actually be, matches the model's own `WALLPAPER`, and keeps the launcher's vocabulary free of a
 * borrowed trademark — the rule `IconFilters` states for filter names, now applied to a wash.
 *
 * It also fits one line, which "Material You" did not: five labels across a phone left that one wrapping and hanging
 * below the other four.
 *
 * "None" rather than "Transparent" — what is absent is the wash, and the blur behind it is not transparent at all.
 */
private val BackdropTint.label: String
    get() = when (this) {
        BackdropTint.NONE -> "None"
        BackdropTint.LIGHT -> "Light"
        BackdropTint.DARK -> "Dark"
        BackdropTint.WALLPAPER -> "Wallpaper"
        BackdropTint.CUSTOM -> "Custom"
    }

/**
 * Every value here is a `0f..1f` strength, so every one of them reads as a percentage.
 *
 * **Rounded, not truncated, and that is a bug fix rather than a nicety.** A stepper moves the value by exactly one
 * hundredth (`finestStep`), but a hundredth is not representable in binary: 0.29f is 0.28999999…, so `toInt()` floored
 * it to 28 — the value had moved and the number had not, and the *next* press then read as a jump of two. Presses
 * appeared to land at random. The studio's own readout never had this because `"%.2f"` rounds, which is what this now
 * does.
 */
private fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

/**
 * Where each reset goes, read from the model rather than restated.
 *
 * The studio's own lesson: a reset pinned to a number typed at the call site drifts from the value the effect actually
 * arrives at, and then lights up on a control nobody has touched. Constructing the defaults is the cheapest way to be
 * sure — the constructor is the only place those numbers live.
 */
private val BlurDefaults = BackdropEffect.Blur()
private val GlassDefaults = BackdropEffect.LiquidGlass()

/**
 * The most a wash may cover, `0f..0.9f`.
 *
 * **One ceiling where there were two.** The light and dark washes stopped at 0.6 on the reasoning that past ~60% a wash
 * is an opaque sheet rather than a frost, while Material You went to 0.9 because its hue *is* the effect. Merged, the
 * option cannot decide it — a custom color may be anything, and a pale one at 60% covers less than black at 40%. So the
 * bound is the higher of the two and the **preview** is what tells a user they have gone too far, which is a thing it can
 * now do.
 */
private const val MaxTintAmount = 0.9f

/** How large the swatches are: the ring, and the fill inside it. */
private val SwatchRing = 40.dp
private val SwatchFill = 30.dp
private val SelectedRing = 2.dp
private val UnselectedRing = 1.dp
