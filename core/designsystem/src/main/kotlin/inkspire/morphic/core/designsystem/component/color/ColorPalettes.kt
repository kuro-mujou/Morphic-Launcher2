package inkspire.morphic.core.designsystem.component.color

/**
 * A named set of colors that go together — the unit the color picker offers as a "palette".
 *
 * **Small and cohesive, not a colormap.** These are for *theming an icon* — a plate, a tint, a duotone's two ends —
 * so each is a handful of colors chosen to sit well beside each other, ordered light to dark. That is a different
 * thing from the scientific colormaps (viridis and the like) that dominate the gart study's palette files, which are
 * built for mapping data and read as garish on a launcher surface; those are deliberately not ported.
 *
 * @property name what the picker labels it — short, since it sits under a row of swatches.
 * @property colors the swatches, **opaque ARGB**, light to dark. Any length; the picker lays each out as one pill.
 */
data class ColorPalette(val name: String, val colors: List<Int>)

/**
 * The curated palettes the studio's color picker offers below the hue bar — the "palettes" harvested from the gart
 * study, reworked for icon theming rather than ported wholesale.
 *
 * **A dozen moods, not three hundred colors.** gart ships ~300 palettes, most of them data colormaps or flat dumps
 * of a few hundred named colors (its `NipponColors` alone is 250). The value here is a *short, opinionated* set a
 * user can scan in one pass, so this is a hand-picked spread across warm/cool/earth/jewel/neutral, six colors each.
 *
 * **Four are seeded from gart's genuinely theming-oriented files** — `MidCenturyColors`, `RetroColors`,
 * `CyanotypeColors`, and the pink family of `NipponColors` (all BSD-2, © 2022 Igor Spasić) — trimmed to six and
 * re-ordered light-to-dark. The rest are curated here.
 *
 * The colors are all in the `all` list's own property initializer, which is why the literals below do not each need
 * a name: they are arrangement, read at a glance, exactly the case the dp-literal rule is about one layer over.
 */
object ColorPalettes {

    private fun palette(name: String, vararg colors: Long): ColorPalette =
        ColorPalette(name, colors.map { it.toInt() })

    val all: List<ColorPalette> = listOf(
        // Grayscale first — the launcher's own register, and the neutrals a mono icon is built from.
        palette("Mono", 0xFFFFFFFF, 0xFFC7C7C7, 0xFF8F8F8F, 0xFF5A5A5A, 0xFF2E2E2E, 0xFF000000),
        // gart · MidCenturyColors.
        palette("Mid-century", 0xFFF6F0E2, 0xFFE5AC59, 0xFFB65D3B, 0xFF3E6565, 0xFF3D5A80, 0xFF292929),
        // gart · RetroColors.
        palette("Retro", 0xFFCAC4A2, 0xFFCAA023, 0xFFCA4D23, 0xFF4D7C23, 0xFF235A7C, 0xFF33332D),
        // gart · CyanotypeColors.
        palette("Cyanotype", 0xFFEDF6FB, 0xFF8ECAE6, 0xFF4994C0, 0xFF2B6AA1, 0xFF1D5074, 0xFF0D1F2D),
        // gart · NipponColors, the pink family.
        palette("Sakura", 0xFFFEDFE1, 0xFFF4A7B9, 0xFFE16B8C, 0xFFDB4D6D, 0xFFCB1B45, 0xFF8E354A),
        palette("Ember", 0xFFFFE7C2, 0xFFFFB347, 0xFFE8743B, 0xFFC0392B, 0xFF7B241C, 0xFF3D0F0A),
        palette("Forest", 0xFFEAF4D3, 0xFFA8C66C, 0xFF6B8E23, 0xFF3E6B36, 0xFF24503A, 0xFF10251D),
        palette("Dusk", 0xFFF2E2C4, 0xFFE6A15C, 0xFFC9603E, 0xFF2C6E6B, 0xFF1F3A4D, 0xFF121E2B),
        palette("Jewel", 0xFFF1A208, 0xFFE63946, 0xFF9B2F61, 0xFF2A9D8F, 0xFF264653, 0xFF1B1035),
        palette("Pastel", 0xFFFDE2E4, 0xFFFFF1C1, 0xFFD8F3DC, 0xFFBEE1E6, 0xFFDFE7FD, 0xFFE7C6FF),
        palette("Synth", 0xFFFEE440, 0xFF00F5D4, 0xFF00BBF9, 0xFF9B5DE5, 0xFFF15BB5, 0xFF0A0A12),
        palette("Desert", 0xFFF4E9CD, 0xFFE4C590, 0xFFD9A566, 0xFFB97A56, 0xFF8C5A3C, 0xFF5A3A28),
    )
}
