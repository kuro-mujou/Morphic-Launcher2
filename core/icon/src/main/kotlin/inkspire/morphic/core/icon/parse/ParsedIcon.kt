package inkspire.morphic.core.icon.parse

/**
 * The structured result of parsing a platform icon drawable into our layer model.
 *
 * @property foreground Always present — the icon's main content (a modern adaptive foreground, or the whole
 *   bitmap of a legacy icon). We never split a legacy raster into glyph-vs-background; the whole thing is fg.
 * @property background The source background, or `null` for a legacy (non-adaptive) icon. The always-present
 *   *empty background layer* the editor shows is a layer-set concept built on top of this — at parse time a
 *   missing background is honestly `null`.
 * @property monochrome The OS themed-icon layer, present only for adaptive icons on Android 13+. Per the icon
 *   design it is stashed here as an *alternate foreground source* the user can switch to, not a third layer.
 */
data class ParsedIcon(
    val foreground: ParsedLayer,
    val background: ParsedLayer? = null,
    val monochrome: ParsedLayer? = null,
) {
    /** True when there is no source background — i.e. a legacy (non-adaptive) icon. */
    val isLegacy: Boolean get() = background == null

    /** True when the source adaptive icon exposed a monochrome (themed-icon) layer. */
    val hasMonochrome: Boolean get() = monochrome != null
}
