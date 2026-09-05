package inkspire.morphic.core.designsystem.component.color

/**
 * The scientific and cartographic colormaps gart indexes as `colormap001`–`colormap133` — CARTO, ColorBrewer, CET,
 * Matplotlib (viridis and family), cmocean's Ocean set, Moreland, Kindlmann, Karpov, ParaView, Plotly, Polychrome,
 * Tableau, D3 (BSD-2, © 2022 Igor Spasić) — carried over whole rather than left behind.
 *
 * **Each is thinned to at most eight stops, which is what makes a colormap usable here.** Most of these are
 * *continuous* ramps stored as 256 samples; a generator that indexes shapes into a palette (`colorAt(i)`) would paint
 * 256 near-identical neighbors and read as flat, and the picker's pill would be a smear. So a long map is resampled
 * to eight evenly spaced stops, **both ends kept**, which preserves the ramp's endpoints and its midpoint — the parts
 * a diverging map means — and puts it in the same shape as every other palette in the bank. The qualitative sets
 * (Tab20b, Polychrome, Set3) are thinned by the same rule; their order is arbitrary anyway, so no stop is special.
 *
 * **Green and blue are swapped back.** gart's own `Palette.of(vararg java.awt.Color)` builds each stop as
 * `rgb(it.red, it.blue, it.green)`, so every map it loads from float triples — all of Matplotlib, CET, Kindlmann,
 * Moreland, Karpov — is channel-swapped inside gart itself. These are the true colors (gart's viridis starts
 * `#445401`; real viridis, and this file, start `#440154`). Do not "fix" them back to match the study.
 *
 * **Last in [ColorPalettes.all], on purpose.** Built to map data rather than to decorate, several of them read garish
 * on a launcher surface, so they are offered after the [coolPalettes] and [designerPalettes] banks rather than mixed
 * through them. One of the 133 is absent, being byte-identical to another gart already ships.
 *
 * Built with [palette], `ColorPalettes`' own helper.
 */
internal val colormapPalettes: List<ColorPalette> = listOf(
        palette(
            "Carto Antique",
            0xFF855C75, 0xFFAF6458, 0xFF736F4C, 0xFF625377, 0xFF68855C, 0xFFA06177, 0xFF8C785D, 0xFF7C7C7C,
        ),
        palette(
            "Carto ArmyRose",
            0xFF798234, 0xFFA3AD62, 0xFFD0D3A2, 0xFFFDFBE4, 0xFFF0C6C3, 0xFFDF91A3, 0xFFD46780,
        ),
        palette(
            "Carto BluGrn",
            0xFFC4E6C3, 0xFF96D2A4, 0xFF6DBC90, 0xFF4DA284, 0xFF36877A, 0xFF266B6E, 0xFF1D4F60,
        ),
        palette(
            "Carto BluYl",
            0xFFF7FEAE, 0xFFB7E6A5, 0xFF7CCBA2, 0xFF46AEA0, 0xFF089099, 0xFF00718B, 0xFF045275,
        ),
        palette(
            "Carto Bold",
            0xFF7F3C8D, 0xFF3969AC, 0xFFF2B701, 0xFF80BA5A, 0xFFE68310, 0xFFCF1C90, 0xFFF97B72, 0xFFA5AA99,
        ),
        palette(
            "Carto BrwnYl",
            0xFFEDE5CF, 0xFFE0C2A2, 0xFFD39C83, 0xFFC1766F, 0xFFA65461, 0xFF813753, 0xFF541F3F,
        ),
        palette(
            "Carto Burg",
            0xFFFFC6C4, 0xFFF4A3A8, 0xFFE38191, 0xFFCC607D, 0xFFAD466C, 0xFF8B3058, 0xFF672044,
        ),
        palette(
            "Carto BurgYl",
            0xFFFBE6C5, 0xFFF5BA98, 0xFFEE8A82, 0xFFDC7176, 0xFFC8586C, 0xFF9C3F5D, 0xFF70284A,
        ),
        palette(
            "Carto DarkMint",
            0xFFD2FBD4, 0xFFA5DBC2, 0xFF7BBCB0, 0xFF559C9E, 0xFF3A7C89, 0xFF235D72, 0xFF123F5A,
        ),
        palette(
            "Carto Earth",
            0xFFA16928, 0xFFBD925A, 0xFFD6BD8D, 0xFFEDEAC2, 0xFFB5C8B8, 0xFF79A7AC, 0xFF2887A1,
        ),
        palette(
            "Carto Emrld",
            0xFFD3F2A3, 0xFF97E196, 0xFF6CC08B, 0xFF4C9B82, 0xFF217A79, 0xFF105965, 0xFF074050,
        ),
        palette(
            "Carto Fall",
            0xFF3D5941, 0xFF778868, 0xFFB5B991, 0xFFF6EDBD, 0xFFEDBB8A, 0xFFDE8A5A, 0xFFCA562C,
        ),
        palette(
            "Carto Geyser",
            0xFF008080, 0xFF70A494, 0xFFB4C8A8, 0xFFF6EDBD, 0xFFEDBB8A, 0xFFDE8A5A, 0xFFCA562C,
        ),
        palette(
            "Carto Magenta",
            0xFFF3CBD3, 0xFFEAA9BD, 0xFFDD88AC, 0xFFCA699D, 0xFFB14D8E, 0xFF91357D, 0xFF6C2167,
        ),
        palette(
            "Carto Mint",
            0xFFE4F1E1, 0xFFB4D9CC, 0xFF89C0B6, 0xFF63A6A0, 0xFF448C8A, 0xFF287274, 0xFF0D585F,
        ),
        palette(
            "Carto OrYel",
            0xFFECDA9A, 0xFFEFC47E, 0xFFF3AD6A, 0xFFF7945D, 0xFFF97B57, 0xFFF66356, 0xFFEE4D5A,
        ),
        palette(
            "Carto Pastel",
            0xFF66C5CC, 0xFFF89C74, 0xFFDCB0F2, 0xFF9EB9F3, 0xFFFE88B1, 0xFF8BE0A4, 0xFFB497E7, 0xFFB3B3B3,
        ),
        palette(
            "Carto Peach",
            0xFFFDE0C5, 0xFFFACBA6, 0xFFF8B58B, 0xFFF59E72, 0xFFF2855D, 0xFFEF6A4C, 0xFFEB4A40,
        ),
        palette(
            "Carto PinkYl",
            0xFFFEF6B5, 0xFFFFDD9A, 0xFFFFC285, 0xFFFFA679, 0xFFFA8A76, 0xFFF16D7A, 0xFFE15383,
        ),
        palette(
            "Carto Prism",
            0xFF5F4690, 0xFF38A6A5, 0xFF0F8554, 0xFFEDAD08, 0xFFE17C05, 0xFF94346E, 0xFF6F4070, 0xFF666666,
        ),
        palette(
            "Carto Purp",
            0xFFF3E0F7, 0xFFE4C7F1, 0xFFD1AFE8, 0xFFB998DD, 0xFF9F82CE, 0xFF826DBA, 0xFF63589F,
        ),
        palette(
            "Carto PurpOr",
            0xFFF9DDDA, 0xFFF2B9C4, 0xFFE597B9, 0xFFCE78B3, 0xFFAD5FAD, 0xFF834BA0, 0xFF573B88,
        ),
        palette(
            "Carto RedOr",
            0xFFF6D2A9, 0xFFF5B78E, 0xFFF19C7C, 0xFFEA8171, 0xFFDD686C, 0xFFCA5268, 0xFFB13F64,
        ),
        palette(
            "Carto Safe",
            0xFF88CCEE, 0xFFDDCC77, 0xFF117733, 0xFFAA4499, 0xFF44AA99, 0xFF882255, 0xFF661100, 0xFF888888,
        ),
        palette(
            "Carto Sunset",
            0xFFF3E79B, 0xFFFAC484, 0xFFF8A07E, 0xFFEB7F86, 0xFFCE6693, 0xFFA059A0, 0xFF5C53A5,
        ),
        palette(
            "Carto SunsetDark",
            0xFFFCDE9C, 0xFFFAA476, 0xFFF0746E, 0xFFE34F6F, 0xFFDC3977, 0xFFB9257A, 0xFF7C1D6F,
        ),
        palette(
            "Carto Teal",
            0xFFD1EEEA, 0xFFA8DBD9, 0xFF85C4C9, 0xFF68ABB8, 0xFF4F90A6, 0xFF3B738F, 0xFF2A5674,
        ),
        palette(
            "Carto TealGrn",
            0xFFB0F2BC, 0xFF89E8AC, 0xFF67DBA5, 0xFF4CC8A3, 0xFF38B2A3, 0xFF2C98A0, 0xFF257D98,
        ),
        palette(
            "Carto TealRose",
            0xFF009392, 0xFF72AAA1, 0xFFB1C7B3, 0xFFF1EAC8, 0xFFE5B9AD, 0xFFD98994, 0xFFD0587E,
        ),
        palette(
            "Carto Temps",
            0xFF009392, 0xFF39B185, 0xFF9CCB86, 0xFFE9E29C, 0xFFEEB479, 0xFFE88471, 0xFFCF597E,
        ),
        palette(
            "Carto Tropic",
            0xFF009B9E, 0xFF42B7B9, 0xFFA7D3D4, 0xFFF1F1F1, 0xFFE4C1D9, 0xFFD691C1, 0xFFC75DAB,
        ),
        palette(
            "Carto Vivid",
            0xFFE58606, 0xFF52BCA3, 0xFF99C945, 0xFF24796C, 0xFFDAA51B, 0xFF764E9F, 0xFFED645A, 0xFFA5AA99,
        ),
        palette(
            "CET KovesiBGYW",
            0xFF000000, 0xFF1B20C1, 0xFF2152CF, 0xFF3A886C, 0xFF66AC26, 0xFFAFC61C, 0xFFEBE14F, 0xFF000101,
        ),
        palette(
            "CET KovesiKRYW",
            0xFF111111, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF010000, 0xFF010000, 0xFF010000, 0xFF000101,
        ),
        palette(
            "CET MRYBM",
            0xFFF884F7, 0xFFEA4388, 0xFFBD4304, 0xFFD58F04, 0xFFA19F62, 0xFF2269C4, 0xFF956BFA, 0xFFF884F7,
        ),
        palette(
            "CET MYGBM",
            0xFFEF55F1, 0xFFFBAFA1, 0xFFC6E516, 0xFF61C10B, 0xFF439064, 0xFF284EC8, 0xFF9139FA, 0xFFEF55F1,
        ),
        palette(
            "ColorBrewer Accent",
            0xFF7FC97F, 0xFFBEAED4, 0xFFFDC086, 0xFFFFFF99, 0xFF386CB0, 0xFFF0027F, 0xFFBF5B17, 0xFF666666,
        ),
        palette(
            "ColorBrewer Blues",
            0xFFF7FBFF, 0xFFDEEBF7, 0xFFC6DBEF, 0xFF9ECAE1, 0xFF4292C6, 0xFF2171B5, 0xFF08519C, 0xFF08306B,
        ),
        palette(
            "ColorBrewer BrBG",
            0xFF543005, 0xFF8C510A, 0xFFDFC27D, 0xFFF6E8C3, 0xFFC7EAE5, 0xFF80CDC1, 0xFF01665E, 0xFF003C30,
        ),
        palette(
            "ColorBrewer BuGn",
            0xFFF7FCFD, 0xFFE5F5F9, 0xFFCCECE6, 0xFF99D8C9, 0xFF41AE76, 0xFF238B45, 0xFF006D2C, 0xFF00441B,
        ),
        palette(
            "ColorBrewer BuPu",
            0xFFF7FCFD, 0xFFE0ECF4, 0xFFBFD3E6, 0xFF9EBCDA, 0xFF8C6BB1, 0xFF88419D, 0xFF810F7C, 0xFF4D004B,
        ),
        palette(
            "ColorBrewer Dark2",
            0xFF1B9E77, 0xFFD95F02, 0xFF7570B3, 0xFFE7298A, 0xFF66A61E, 0xFFE6AB02, 0xFFA6761D, 0xFF666666,
        ),
        palette(
            "ColorBrewer GnBu",
            0xFFF7FCF0, 0xFFE0F3DB, 0xFFCCEBC5, 0xFFA8DDB5, 0xFF4EB3D3, 0xFF2B8CBE, 0xFF0868AC, 0xFF084081,
        ),
        palette(
            "ColorBrewer Greens",
            0xFFF7FCF5, 0xFFE5F5E0, 0xFFC7E9C0, 0xFFA1D99B, 0xFF41AB5D, 0xFF238B45, 0xFF006D2C, 0xFF00441B,
        ),
        palette(
            "ColorBrewer Greys",
            0xFFF0F0F0, 0xFFD9D9D9, 0xFFBDBDBD, 0xFF969696, 0xFF737373, 0xFF525252, 0xFF252525,
        ),
        palette(
            "ColorBrewer Oranges",
            0xFFFFF5EB, 0xFFFEE6CE, 0xFFFDD0A2, 0xFFFDAE6B, 0xFFF16913, 0xFFD94801, 0xFFA63603, 0xFF7F2704,
        ),
        palette(
            "ColorBrewer OrRd",
            0xFFFFF7EC, 0xFFFEE8C8, 0xFFFDD49E, 0xFFFDBB84, 0xFFEF6548, 0xFFD7301F, 0xFFB30000, 0xFF7F0000,
        ),
        palette(
            "ColorBrewer Paired",
            0xFFA6CEE3, 0xFFB2DF8A, 0xFF33A02C, 0xFFE31A1C, 0xFFFDBF6F, 0xFFCAB2D6, 0xFF6A3D9A, 0xFFB15928,
        ),
        palette(
            "ColorBrewer Pastel1",
            0xFFFBB4AE, 0xFFB3CDE3, 0xFFCCEBC5, 0xFFDECBE4, 0xFFFFFFCC, 0xFFE5D8BD, 0xFFFDDAEC, 0xFFF2F2F2,
        ),
        palette(
            "ColorBrewer Pastel2",
            0xFFB3E2CD, 0xFFFDCDAC, 0xFFCBD5E8, 0xFFF4CAE4, 0xFFE6F5C9, 0xFFFFF2AE, 0xFFF1E2CC, 0xFFCCCCCC,
        ),
        palette(
            "ColorBrewer PiYG",
            0xFF8E0152, 0xFFC51B7D, 0xFFF1B6DA, 0xFFFDE0EF, 0xFFE6F5D0, 0xFFB8E186, 0xFF4D9221, 0xFF276419,
        ),
        palette(
            "ColorBrewer PRGn",
            0xFF40004B, 0xFF762A83, 0xFFC2A5CF, 0xFFE7D4E8, 0xFFD9F0D3, 0xFFA6DBA0, 0xFF1B7837, 0xFF00441B,
        ),
        palette(
            "ColorBrewer PuBu",
            0xFFFFF7FB, 0xFFECE7F2, 0xFFD0D1E6, 0xFFA6BDDB, 0xFF3690C0, 0xFF0570B0, 0xFF045A8D, 0xFF023858,
        ),
        palette(
            "ColorBrewer PuBuGn",
            0xFFFFF7FB, 0xFFECE2F0, 0xFFD0D1E6, 0xFFA6BDDB, 0xFF3690C0, 0xFF02818A, 0xFF016C59, 0xFF014636,
        ),
        palette(
            "ColorBrewer PuOr",
            0xFF7F3B08, 0xFFB35806, 0xFFFDB863, 0xFFFEE0B6, 0xFFD8DAEB, 0xFFB2ABD2, 0xFF542788, 0xFF2D004B,
        ),
        palette(
            "ColorBrewer PuRd",
            0xFFF7F4F9, 0xFFE7E1EF, 0xFFD4B9DA, 0xFFC994C7, 0xFFE7298A, 0xFFCE1256, 0xFF980043, 0xFF67001F,
        ),
        palette(
            "ColorBrewer Purples",
            0xFFFCFBFD, 0xFFEFEDF5, 0xFFDADAEB, 0xFFBCBDDC, 0xFF807DBA, 0xFF6A51A3, 0xFF54278F, 0xFF3F007D,
        ),
        palette(
            "ColorBrewer RdBu",
            0xFF67001F, 0xFFB2182B, 0xFFF4A582, 0xFFFDDBC7, 0xFFD1E5F0, 0xFF92C5DE, 0xFF2166AC, 0xFF053061,
        ),
        palette(
            "ColorBrewer RdGy",
            0xFF67001F, 0xFFB2182B, 0xFFF4A582, 0xFFFDDBC7, 0xFFE0E0E0, 0xFFBABABA, 0xFF4D4D4D, 0xFF1A1A1A,
        ),
        palette(
            "ColorBrewer RdPu",
            0xFFFFF7F3, 0xFFFDE0DD, 0xFFFCC5C0, 0xFFFA9FB5, 0xFFDD3497, 0xFFAE017E, 0xFF7A0177, 0xFF49006A,
        ),
        palette(
            "ColorBrewer RdYlBu",
            0xFFA50026, 0xFFD73027, 0xFFFDAE61, 0xFFFEE090, 0xFFE0F3F8, 0xFFABD9E9, 0xFF4575B4, 0xFF313695,
        ),
        palette(
            "ColorBrewer RdYlGn",
            0xFFA50026, 0xFFD73027, 0xFFFDAE61, 0xFFFEE08B, 0xFFD9EF8B, 0xFFA6D96A, 0xFF1A9850, 0xFF006837,
        ),
        palette(
            "ColorBrewer Reds",
            0xFFFFF5F0, 0xFFFEE0D2, 0xFFFCBBA1, 0xFFFC9272, 0xFFEF3B2C, 0xFFCB181D, 0xFFA50F15, 0xFF67000D,
        ),
        palette(
            "ColorBrewer Set1",
            0xFFE41A1C, 0xFF377EB8, 0xFF4DAF4A, 0xFF984EA3, 0xFFFFFF33, 0xFFA65628, 0xFFF781BF, 0xFF999999,
        ),
        palette(
            "ColorBrewer Set2",
            0xFF66C2A5, 0xFFFC8D62, 0xFF8DA0CB, 0xFFE78AC3, 0xFFA6D854, 0xFFFFD92F, 0xFFE5C494, 0xFFB3B3B3,
        ),
        palette(
            "ColorBrewer Set3",
            0xFF8DD3C7, 0xFFBEBADA, 0xFFFB8072, 0xFFFDB462, 0xFFB3DE69, 0xFFD9D9D9, 0xFFBC80BD, 0xFFFFED6F,
        ),
        palette(
            "ColorBrewer Spectral",
            0xFF9E0142, 0xFFD53E4F, 0xFFFDAE61, 0xFFFEE08B, 0xFFE6F598, 0xFFABDDA4, 0xFF3288BD, 0xFF5E4FA2,
        ),
        palette(
            "ColorBrewer YlGn",
            0xFFFFFFE5, 0xFFF7FCB9, 0xFFD9F0A3, 0xFFADDD8E, 0xFF41AB5D, 0xFF238443, 0xFF006837, 0xFF004529,
        ),
        palette(
            "ColorBrewer YlGnBu",
            0xFFFFFFD9, 0xFFEDF8B1, 0xFFC7E9B4, 0xFF7FCDBB, 0xFF1D91C0, 0xFF225EA8, 0xFF253494, 0xFF081D58,
        ),
        palette(
            "ColorBrewer YlOrBr",
            0xFFFFFFE5, 0xFFFFF7BC, 0xFFFEE391, 0xFFFEC44F, 0xFFEC7014, 0xFFCC4C02, 0xFF993404, 0xFF662506,
        ),
        palette(
            "ColorBrewer YlOrRd",
            0xFFFFFFCC, 0xFFFFEDA0, 0xFFFED976, 0xFFFEB24C, 0xFFFC4E2A, 0xFFE31A1C, 0xFFBD0026, 0xFF800026,
        ),
        palette(
            "D3",
            0xFF1F77B4, 0xFFFF7F0E, 0xFFD62728, 0xFF9467BD, 0xFF8C564B, 0xFFE377C2, 0xFFBCBD22, 0xFF17BECF,
        ),
        palette(
            "Google Turbo",
            0xFF30123B, 0xFF4776EE, 0xFF1BD0D5, 0xFF61FC6C, 0xFFD2E935, 0xFFFE9B2D, 0xFFDA3907, 0xFF7A0403,
        ),
        palette(
            "HSV",
            0xFFFFA700, 0xFFAFFF00, 0xFF08FF00, 0xFF00FF9F, 0xFF00B7FF, 0xFF0010FF, 0xFF9700FF, 0xFFFF00BF,
        ),
        palette(
            "Jet",
            0xFF000083, 0xFF003CAA, 0xFF05FFFF, 0xFFFA0000, 0xFF800000,
        ),
        palette(
            "Moreland BentCoolWarm",
            0xFF564AC2, 0xFF757CD6, 0xFFA0AEE4, 0xFFD5DCEE, 0xFFEAD6CB, 0xFFDB9F86, 0xFFC8624E, 0xFFB10127,
        ),
        palette(
            "Moreland BlackBody",
            0xFF030101, 0xFF411712, 0xFF801F1C, 0xFFB93321, 0xFFDB660A, 0xFFE4A103, 0xFFE9D83C, 0xFFFFFFFF,
        ),
        palette(
            "Moreland BlackBodyExtended",
            0xFF000000, 0xFF2B0F6A, 0xFF000000, 0xFF000000, 0xFFEC543C, 0xFFF59730, 0xFFE9D83C, 0xFFFFFFFF,
        ),
        palette(
            "Moreland SmoothCoolWarm",
            0xFF564AC2, 0xFF7E87EF, 0xFF000001, 0xFFCDD8EF, 0xFFEBD1C2, 0xFFF3A889, 0xFFDE6953, 0xFFB10127,
        ),
        palette(
            "Kindlmann",
            0xFF050004, 0xFF35056D, 0xFF083CAF, 0xFF067460, 0xFF08A022, 0xFF64C509, 0xFFF9D087, 0xFFFFFFFF,
        ),
        palette(
            "Kindlmann Extended",
            0xFF050004, 0xFF29067B, 0xFF044F33, 0xFF2B7506, 0xFFDF620B, 0xFFF989C9, 0xFFE5CEFD, 0xFFFFFFFF,
        ),
        palette(
            "Matplotlib Cividis",
            0xFF00224E, 0xFF213B6E, 0xFF4C556C, 0xFF6C6E72, 0xFF8E8978, 0xFFB1A570, 0xFFD9C55C, 0xFFFEE838,
        ),
        palette(
            "Matplotlib Inferno",
            0xFF000004, 0xFF280B53, 0xFF65156E, 0xFF9F2A63, 0xFFD44842, 0xFFF57D15, 0xFFFAC228, 0xFFFCFFA4,
        ),
        palette(
            "Matplotlib Magma",
            0xFF000004, 0xFF221150, 0xFF5F187F, 0xFF982D80, 0xFFD3436E, 0xFFF8765C, 0xFFFEBB81, 0xFFFCFDBF,
        ),
        palette(
            "Matplotlib Plasma",
            0xFF0D0887, 0xFF5302A3, 0xFF8B0AA5, 0xFFB83289, 0xFFDB5C68, 0xFFF48849, 0xFFFEBD2A, 0xFFF0F921,
        ),
        palette(
            "Matplotlib Twilight",
            0xFFE2D9E2, 0xFF88ACC4, 0xFF5F61B4, 0xFF491564, 0xFF501444, 0xFFA54350, 0xFFC9977B, 0xFFE2D9E2,
        ),
        palette(
            "Matplotlib Viridis",
            0xFF440154, 0xFF46327E, 0xFF365C8D, 0xFF277F8E, 0xFF1FA187, 0xFF4AC16D, 0xFFA0DA39, 0xFFFDE725,
        ),
        palette(
            "MyCarta CubeYF",
            0xFF7B0290, 0xFF803EDF, 0xFF6577FB, 0xFF45A7D0, 0xFF44C991, 0xFF59E051, 0xFF9AEC54, 0xFFD1EB5B,
        ),
        palette(
            "Ocean Algae",
            0xFFD7F9D0, 0xFFA4D698, 0xFF80C176, 0xFF39A553, 0xFF07824C, 0xFF185E3D, 0xFF1A472E, 0xFF122414,
        ),
        palette(
            "Ocean Amp",
            0xFFF1EDEC, 0xFFDFBEB3, 0xFFD69F8C, 0xFFC87155, 0xFFB63F29, 0xFF931228, 0xFF700E27, 0xFF3C0912,
        ),
        palette(
            "Ocean Balance",
            0xFF181C43, 0xFF1059BE, 0xFF408DBA, 0xFFB9CAD0, 0xFFDFBDB2, 0xFFC36143, 0xFFA11D25, 0xFF3C0912,
        ),
        palette(
            "Ocean Curl",
            0xFF151D44, 0xFF166971, 0xFF389981, 0xFFC6D3BC, 0xFFEAC5B3, 0xFFC86162, 0xFF992D61, 0xFF340D35,
        ),
        palette(
            "Ocean Deep",
            0xFFFDFECC, 0xFFAAE0A8, 0xFF73CBA3, 0xFF50A3A2, 0xFF427A99, 0xFF3F508E, 0xFF3F3869, 0xFF281A2C,
        ),
        palette(
            "Ocean Delta",
            0xFF112040, 0xFF1B639F, 0xFF3896AB, 0xFFBED7CD, 0xFFE3CF77, 0xFF6C9607, 0xFF136F29, 0xFF172313,
        ),
        palette(
            "Ocean Dense",
            0xFFE6F1F1, 0xFFA5CFE2, 0xFF83B6E3, 0xFF748AE2, 0xFF795AC1, 0xFF70318A, 0xFF601C5E, 0xFF360E24,
        ),
        palette(
            "Ocean Diff",
            0xFF082340, 0xFF3A5E76, 0xFF8797A3, 0xFFD6D8DB, 0xFFDBD5CA, 0xFF9C9172, 0xFF615625, 0xFF1C2207,
        ),
        palette(
            "Ocean Gray",
            0xFF060606, 0xFF2E2E2E, 0xFF474646, 0xFF6B6B6A, 0xFF858484, 0xFFAEADAD, 0xFFCCCCCB, 0xFFFFFFFD,
        ),
        palette(
            "Ocean Haline",
            0xFF2A186C, 0xFF16409D, 0xFF115E8F, 0xFF308189, 0xFF47A583, 0xFF73C86A, 0xFFAFD95D, 0xFFFDEF9A,
        ),
        palette(
            "Ocean Ice",
            0xFF040613, 0xFF28264E, 0xFF3A3B7A, 0xFF3E63AD, 0xFF4E90BF, 0xFF75BBCF, 0xFFA0D6DC, 0xFFEAFDFD,
        ),
        palette(
            "Ocean Matter",
            0xFFFEEDB0, 0xFFF8B67E, 0xFFF19164, 0xFFDF5B53, 0xFFB9325D, 0xFF881D63, 0xFF63185B, 0xFF2F0F3E,
        ),
        palette(
            "Ocean Oxy",
            0xFF400505, 0xFF7D050E, 0xFF646463, 0xFF878786, 0xFFAEAEAD, 0xFFD9D9D8, 0xFFEAEA3B, 0xFFDDAF19,
        ),
        palette(
            "Ocean Phase",
            0xFFA8780D, 0xFFD54B53, 0xFFDE289D, 0xFFAC54F1, 0xFF4788D5, 0xFF0E9884, 0xFF3C993C, 0xFFA8780D,
        ),
        palette(
            "Ocean Rain",
            0xFFEEEDF3, 0xFFD8C7B4, 0xFFA8AE85, 0xFF679A72, 0xFF09786E, 0xFF155766, 0xFF25354C, 0xFF221B38,
        ),
        palette(
            "Ocean Solar",
            0xFF331418, 0xFF652324, 0xFF852E21, 0xFFA84F16, 0xFFC17A14, 0xFFD3A721, 0xFFDBC831, 0xFFE1FD4B,
        ),
        palette(
            "Ocean Speed",
            0xFFFFFDCD, 0xFFE3CF77, 0xFFC5B83F, 0xFF859F07, 0xFF3B841C, 0xFF0B632C, 0xFF15492A, 0xFF172313,
        ),
        palette(
            "Ocean Tarn",
            0xFF101E4F, 0xFF17536B, 0xFF549185, 0xFFC1C8A6, 0xFFE9CDB8, 0xFFC17F3C, 0xFF5A5918, 0xFF17230E,
        ),
        palette(
            "Ocean Tempo",
            0xFFFFF6F4, 0xFFC6D3BC, 0xFF9CBF9F, 0xFF54A484, 0xFF12827B, 0xFF1A5C6A, 0xFF1C435A, 0xFF151D44,
        ),
        palette(
            "Ocean Thermal",
            0xFF042333, 0xFF273491, 0xFF5B3D9B, 0xFF92558B, 0xFFCE6971, 0xFFF88E44, 0xFFFBB73D, 0xFFE8FA5B,
        ),
        palette(
            "Ocean Topo",
            0xFF281A2C, 0xFF3E5691, 0xFF4FA1A2, 0xFFB0E2A9, 0xFF1D451D, 0xFF76753E, 0xFFCDAB65, 0xFFF9FDE4,
        ),
        palette(
            "Ocean Turbid",
            0xFFE9F6AB, 0xFFD4C873, 0xFFC8AA55, 0xFFB1823E, 0xFF8F613A, 0xFF664835, 0xFF4A382C, 0xFF221F1B,
        ),
        palette(
            "ParaView Edge",
            0xFF313131, 0xFF3810DC, 0xFF2ADEF6, 0xFFAEFDFF, 0xFFFFFDA9, 0xFFF7DA29, 0xFFD90D39, 0xFF313131,
        ),
        palette(
            "ParaView IceFire",
            0xFF001F4D, 0xFF0E58A8, 0xFF30A4CA, 0xFF9BE4EF, 0xFFF3D573, 0xFFDA8200, 0xFFAC2301, 0xFF4C0000,
        ),
        palette(
            "Karpov Hesperia",
            0xFF000000, 0xFF2C0B67, 0xFF642388, 0xFF9C438C, 0xFFCF6B7C, 0xFFEF9A6B, 0xFFFCD082, 0xFFFFFFFF,
        ),
        palette(
            "Karpov Lacerta",
            0xFF000000, 0xFF161C5C, 0xFF1F466A, 0xFF2F6F66, 0xFF4E9959, 0xFF81C045, 0xFFD5DE74, 0xFFFFFFFF,
        ),
        palette(
            "Karpov Laguna",
            0xFF000000, 0xFF34144D, 0xFF433492, 0xFF3A61BB, 0xFF2F93BE, 0xFF4EC1AF, 0xFF9FE6BD, 0xFFFFFFFF,
        ),
        palette(
            "Karpov PlasmaModified",
            0xFF000000, 0xFF340167, 0xFF740889, 0xFFB03083, 0xFFDD626D, 0xFFF59861, 0xFFFED16B, 0xFFFFFFFF,
        ),
        palette(
            "Plotly AgGrnYl",
            0xFF245668, 0xFF0F7279, 0xFF0D8F81, 0xFF39AB7E, 0xFF6EC574, 0xFFA9DC67, 0xFFEDEF5D,
        ),
        palette(
            "Plotly AgSunset",
            0xFF4B2991, 0xFF872CA2, 0xFFC0369D, 0xFFEA4F88, 0xFFFA7876, 0xFFF6A97A, 0xFFEDD9A3,
        ),
        palette(
            "Plotly BlackbodyAlt",
            0xFFE60000, 0xFFE6D200, 0xFFA0C8FF,
        ),
        palette(
            "Plotly Electric",
            0xFF1E0064, 0xFF780064, 0xFFA05A00, 0xFFE6C800, 0xFFFFFADC,
        ),
        palette(
            "Plotly G10",
            0xFF3366CC, 0xFFDC3912, 0xFF109618, 0xFF990099, 0xFF0099C6, 0xFFDD4477, 0xFFB82E2E, 0xFF316395,
        ),
        palette(
            "Plotly Hot",
            0xFFE60000, 0xFFFFD200,
        ),
        palette(
            "Plotly Picnic",
            0xFF3399FF, 0xFF66CCFF, 0xFF99CCFF, 0xFFCCCCFF, 0xFFFFCCFF, 0xFFFF99FF, 0xFFFF66CC, 0xFFFF6666,
        ),
        palette(
            "Plotly",
            0xFF636EFA, 0xFFEF553B, 0xFFAB63FA, 0xFFFFA15A, 0xFF19D3F3, 0xFFFF6692, 0xFFFF97FF, 0xFFFECB52,
        ),
        palette(
            "Plotly 3",
            0xFF0508B8, 0xFF3C19F0, 0xFF6B1CFB, 0xFFBF1CFD, 0xFFF246FE, 0xFFFE88FC, 0xFFFEA5FD, 0xFFFEC3FE,
        ),
        palette(
            "Plotly Portland",
            0xFF0C3383, 0xFF0A88BA, 0xFFF2D338, 0xFFF28F38, 0xFFD91E1E,
        ),
        palette(
            "Plotly T10",
            0xFF4C78A8, 0xFFF58518, 0xFF72B7B2, 0xFF54A24B, 0xFFEECA3B, 0xFFB279A2, 0xFF9D755D, 0xFFBAB0AC,
        ),
        palette(
            "Polychrome Alphabet",
            0xFFAA0DFE, 0xFF565656, 0xFFF7E1A0, 0xFFDEA0FD, 0xFFFEAF16, 0xFF1CFFCE, 0xFFC075A6, 0xFFFA0087,
        ),
        palette(
            "Polychrome Dark24",
            0xFF2E91E5, 0xFFFB0D0D, 0xFF750D86, 0xFF00A08B, 0xFFB2828D, 0xFF862A16, 0xFFDA60CA, 0xFFAF0038,
        ),
        palette(
            "Polychrome Light24",
            0xFFFD3216, 0xFFFED4C4, 0xFFFF9616, 0xFFDC587D, 0xFF00B5F7, 0xFFFF0092, 0xFFBC7196, 0xFFE48F72,
        ),
        palette(
            "Tableau Tab20b",
            0xFF393B79, 0xFF9C9EDE, 0xFF8CA252, 0xFF8C6D31, 0xFFE7BA52, 0xFFAD494A, 0xFFE7969C, 0xFFCE6DBD,
        ),
        palette(
            "Tableau Tab20c",
            0xFF3182BD, 0xFFC6DBEF, 0xFFFD8D3C, 0xFF31A354, 0xFFA1D99B, 0xFF9E9AC8, 0xFFDADAEB, 0xFFBDBDBD,
        ),)
