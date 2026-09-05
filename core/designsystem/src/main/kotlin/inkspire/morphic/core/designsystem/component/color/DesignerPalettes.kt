package inkspire.morphic.core.designsystem.component.color

/**
 * gart's hand-made palette sets — its `mix` bank, its `PalettesOf4` quads and its `CyanotypeColors` ramps
 * (BSD-2, © 2022 Igor Spasić) — the decorative half of what the study ships beside [coolPalettes].
 *
 * **Same register as the cool bank, different origin.** These were picked by eye to look good together, not derived
 * to map a number, which is why they sit *before* [colormapPalettes] in [ColorPalettes.all]: the picker's ribbon runs
 * decorative first and data-viz last, so scrolling it does not open on a wall of scientific ramps.
 *
 * Names carry the gart identifier they came from (`mix_7` → "Mix 7", `q13` → "Quad 13") rather than being invented
 * here — the picker shows the colors, so a name's only jobs are the `LazyRow` key and tracing a swatch back to the
 * study. Three of gart's sets are absent because they are byte-identical to another one it already ships.
 *
 * Built with [palette], `ColorPalettes`' own helper.
 */
internal val designerPalettes: List<ColorPalette> = listOf(
        palette(
            "Mix 1",
            0xFFFF0000, 0xFFFFFF00, 0xFF0000FF, 0xFF9400D3, 0xFF8B008B, 0xFFFF69B4, 0xFFFFD700, 0xFFFF8C00,
        ),
        palette(
            "Mix 2",
            0xFF1F240A, 0xFFEFAC28, 0xFFEF692F, 0xFF773421, 0xFF392A1C, 0xFF276468, 0xFF9B1A0A, 0xFF300F0A,
        ),
        palette(
            "Mix 3",
            0xFF2E222F, 0xFFFFFFFF, 0xFFF9C22B, 0xFFD5E04B, 0xFF547E64, 0xFF484A77, 0xFF753C54, 0xFFFDCBB0,
        ),
        palette(
            "Mix 4",
            0xFF1A1C2C, 0xFFB13E53, 0xFFFFCD75, 0xFF38B764, 0xFF3B5DC9, 0xFF73EFF7, 0xFF94B0C2, 0xFF333C57,
        ),
        palette(
            "Mix 5",
            0xFF000000, 0xFFC5CCB8, 0xFF666092, 0xFF7CA1C0, 0xFFBE955C, 0xFF6E6962, 0xFF9D9F7F, 0xFF433455,
        ),
        palette(
            "Mix 7",
            0xFF21181B, 0xFFFFEE83, 0xFF1B1F21, 0xFFF5FFE8, 0xFF2C354D, 0xFFFF5277, 0xFF4F1D4C, 0xFFFFAE70,
        ),
        palette(
            "Mix 8",
            0xFFA0DDD3, 0xFF3E3B66, 0xFF7B6268, 0xFFFCECD1, 0xFFEBC8A7, 0xFF6A3948, 0xFF7A3B4F, 0xFFF7CF91,
        ),
        palette(
            "Mix 9",
            0xFF3D3957, 0xFFF54F4F, 0xFF995C95, 0xFFDFEDED, 0xFF357985, 0xFFDFD3C3, 0xFF66333D, 0xFFFAFAC3,
        ),
        palette(
            "Mix 10",
            0xFF000000, 0xFFC1D9F2, 0xFF3F1F3C, 0xFF42BC7F, 0xFF354AB2, 0xFF5D2F8C, 0xFFA52639, 0xFF008782,
        ),
        palette(
            "Mix 11",
            0xFF636663, 0xFFEB9661, 0xFF593E47, 0xFFFDD179, 0xFF44702D, 0xFFA4C5AF, 0xFFBBC3D0, 0xFF303843,
        ),
        palette(
            "Mix 12",
            0xFF523C4E, 0xFF3E5442, 0xFF38607C, 0xFF101024, 0xFFD44E52, 0xFF80AC40, 0xFF8BD0BA, 0xFFFFF8C0,
        ),
        palette(
            "Mix 13",
            0xFF2B2821, 0xFFD9AC8B, 0xFFE3CFB4, 0xFF5D7275, 0xFF5C8B93, 0xFFB03A48, 0xFFD4804D, 0xFF3E6958,
        ),
        palette(
            "Mix 14",
            0xFFFFFFFF, 0xFF11ADC1, 0xFF393457, 0xFF5BB361, 0xFFF99252, 0xFF6A3771, 0xFFF48CB6, 0xFF9B9C82,
        ),
        palette(
            "Mix 15",
            0xFFD1B187, 0xFFAE5D40, 0xFF4B3D44, 0xFF927441, 0xFFB3A555, 0xFF8CABA1, 0xFF574852, 0xFFAB9B8E,
        ),
        palette(
            "Quad 1",
            0xFF33332D, 0xFFCAC4A2, 0xFFCA4D23, 0xFFCAA023,
        ),
        palette(
            "Quad 2",
            0xFF264653, 0xFF2A9D8F, 0xFFE9C46A, 0xFFF4A261,
        ),
        palette(
            "Quad 3",
            0xFFE63946, 0xFFF1FAEE, 0xFFA8DADC, 0xFF457B9D,
        ),
        palette(
            "Quad 4",
            0xFF8ECAE6, 0xFF219EBC, 0xFF023047, 0xFFFFB703,
        ),
        palette(
            "Quad 5",
            0xFF06D6A0, 0xFF118AB2, 0xFF073B4C, 0xFFFFD166,
        ),
        palette(
            "Quad 6",
            0xFFEF476F, 0xFFFFD166, 0xFF06D6A0, 0xFF118AB2,
        ),
        palette(
            "Quad 7",
            0xFF06AED5, 0xFF086788, 0xFFF0F3BD, 0xFFF4D35E,
        ),
        palette(
            "Quad 8",
            0xFFEF233C, 0xFFFFFFFF, 0xFF8D99AE, 0xFF2B2D42,
        ),
        palette(
            "Quad 9",
            0xFFFF9F1C, 0xFFFF4040, 0xFF2EC4B6, 0xFF011627,
        ),
        palette(
            "Quad 10",
            0xFFFFBF69, 0xFFD81159, 0xFF8F2D56, 0xFF218380,
        ),
        palette(
            "Quad 11",
            0xFFFFD6E0, 0xFFFA7E61, 0xFF2EC4B6, 0xFF011627,
        ),
        palette(
            "Quad 12",
            0xFFFFBE0B, 0xFFFB5607, 0xFFFF006E, 0xFF8338EC,
        ),
        palette(
            "Quad 13",
            0xFF003049, 0xFFD62828, 0xFFF77F00, 0xFFFCBF49,
        ),
        palette(
            "Quad 14",
            0xFF001524, 0xFF15616D, 0xFFFFECD1, 0xFFFF7D00,
        ),
        palette(
            "Quad 15",
            0xFF2B2D42, 0xFF8D99AE, 0xFFEDF2F4, 0xFFEF233C,
        ),
        palette(
            "Quad 16",
            0xFF26547C, 0xFFEF476F, 0xFFFFD166, 0xFF06D6A0,
        ),
        palette(
            "Quad 18",
            0xFF283D3B, 0xFF197278, 0xFFEDDDD4, 0xFFC44536,
        ),
        palette(
            "Quad 19",
            0xFF177E89, 0xFF084C61, 0xFFDB3A34, 0xFFFFC857,
        ),
        palette(
            "Cyanotype 1",
            0xFF0D1F2D, 0xFF1A3A4A, 0xFF1D5074, 0xFF2B6AA1, 0xFF4994C0, 0xFF8ECAE6, 0xFFC8E9F3, 0xFFEDF6FB,
        ),
        palette(
            "Cyanotype 2",
            0xFF0D1525, 0xFF112240, 0xFF1A3060, 0xFF204585, 0xFF3D84BE, 0xFF7AB8D4, 0xFFB8DDE9, 0xFFEFF7FA,
        ),)
