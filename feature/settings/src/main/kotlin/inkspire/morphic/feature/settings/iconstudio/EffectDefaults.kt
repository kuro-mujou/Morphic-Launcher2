package inkspire.morphic.feature.settings.iconstudio

import inkspire.morphic.core.icon.IconPatterns
import inkspire.morphic.core.model.icon.LayerEffect

// The value each effect is seeded at when its entry is first opened, and the floor a slider that must not reach
// zero stops on. Read by the seeding in `EffectSlice` and by the controls themselves, which is why they live in
// neither.

/**
 * The smallest **value** a slider that must not reach zero will take. Deliberately not the stepper's increment: the
 * two are unrelated questions that happen to sit at a similar number.
 *
 * Four sliders bound their floor to it: a bloom's radius, a pixelate's fill, a grain's size and an extrude's depth.
 * Each is a quantity whose zero *is* the effect's identity, so the floor is what keeps dragging to the bottom of the
 * track a very small effect rather than a silently absent one — the switch in the header being where "off" is said.
 * It stays at the value it has always had; only the stepper got finer.
 */
internal const val UnitFloor = 0.05f

/**
 * **Each effect as it arrives** — held once, so the value the studio *seeds* and the value a slider's **reset**
 * returns to are the same object's fields rather than two numbers that happen to agree.
 *
 * They did not agree, and the symptom was a panel that lied. Every `Strength` reset was pinned to `0`, on the
 * reading that reset means "neutral" — so opening a fresh effect lit **every** reset button, telling the user they
 * had changed things they had not touched, and pressing one took the effect to invisible rather than back to what
 * they had just been shown. The row is supposed to double as the answer to *"have I changed this?"*, and against a
 * seeded default only one reading makes that true: reset goes to **the value the effect arrives at**.
 *
 * Which is also why these are read rather than restated. A default is tuned in `LayerEffect` — that is where the
 * effect says what it looks like — and a reset target copied by hand into a call site is one edit away from
 * disagreeing with it, silently, in the direction of the bug above.
 *
 * The adjustments need no entry: an unseeded effect arrives at its identity, so `LayerEffect.Color()`'s own neutral
 * *is* both answers, and the sliders that read `1f` and `0f` for hue, saturation and brightness were right all along.
 */
internal val DuotoneDefaults = LayerEffect.Duotone()
internal val BloomDefaults = LayerEffect.Bloom()
internal val GlossDefaults = LayerEffect.Gloss()
internal val VignetteDefaults = LayerEffect.Vignette()
internal val BevelDefaults = LayerEffect.Bevel()

/**
 * The one addition with no all-default constructor: a pattern has to *be* one, and there is no neutral tile. Dots for
 * the reason `PatternControls` picks it as its own fallback — the most legible of the set at icon size.
 */
internal val PatternDefaults = LayerEffect.Pattern(pattern = IconPatterns.Dots)
internal val ExtrudeDefaults = LayerEffect.Extrude()
internal val ChromaticDefaults = LayerEffect.ChromaticSplit()
internal val OutlineDefaults = LayerEffect.Outline()
internal val GlowDefaults = LayerEffect.Glow()
internal val ShadowDefaults = LayerEffect.Shadow()
internal val InnerShadowDefaults = LayerEffect.InnerShadow()
internal val InnerGlowDefaults = LayerEffect.InnerGlow()
internal val RippleDefaults = LayerEffect.Ripple()
internal val GrainDefaults = LayerEffect.Grain()
internal val PixelateDefaults = LayerEffect.Pixelate()
internal val ProgressiveBlurDefaults = LayerEffect.ProgressiveBlur()
internal val GlassDefaults = LayerEffect.Glass()
internal val DitherDefaults = LayerEffect.Dither()
