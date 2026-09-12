package inkspire.morphic.feature.settings.wallpaperstudio

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.wallpaper.WallpaperDesign
import inkspire.morphic.feature.settings.iconstudio.StudioContentColor

/**
 * The studio's design chooser: every generator in the catalog, each drawn as *itself* in the recipe currently being
 * edited.
 *
 * **A picture of the design, not its name — which is the whole change from the chip row this replaces.** A wallpaper
 * design is a look, and "Truchet", "Vitrall" and "Impasto" are words that tell a user nothing about the look they
 * name; picking one from a row of labels was a matter of tapping each in turn and watching the screen. Thirty-two
 * designs make that a search rather than a choice.
 *
 * **The palette is what is held constant and the design is what varies**, which is the reference studio's arrangement
 * and a much better one than a catalog of stock sample images: every tile is painted from the recipe the user has
 * already built, so the grid answers "what would *my* wallpaper look like as each of these" rather than "what did
 * these look like to whoever shipped them". It also makes the color panel and this panel two views of one recipe.
 *
 * **A tile is what tapping it gives you, at a different size and shape.** Everything but the design is pinned —
 * palette, seed, knobs, filters — so the only honest difference between a tile and the wallpaper behind it is the
 * frame it was painted into. That difference is real and worth stating: a generator frames itself in fractions of
 * whatever size it is handed, so a 3:4 tile is a *correct* picture of the design at 3:4 and not a crop of the tall one
 * on screen. Some designs (the banded and columnar ones especially) therefore lay out differently in the tile than
 * they will full-screen. The alternative was tiles at the screen's own ~9:19.5, which on a three-column grid is a
 * sliver 100dp wide and 216dp tall — two rows to a panel, and the shape wins nothing back.
 *
 * **It fills in rather than appearing.** The catalog is painted one design at a time on a background dispatcher, so
 * tiles arrive over roughly a second and each fades in where it lands. An empty tile means "not painted yet", which is
 * why the placeholder is a plain wash rather than anything that could be mistaken for a design that renders blank.
 *
 * **It opens on the design that is showing, not at the top.** Thirty-two tiles are four screens of panel, so a grid
 * that always started at *Gradient* would open on a picture the user is not looking at and give them nothing to place
 * what they have against. Only on open, deliberately: re-running the scroll on every pick would pull the grid out from
 * under the finger that just tapped a tile in the bottom row.
 *
 * **The size is fixed by the first tile measured and every later one is ignored**, which is a different claim from
 * "they are all the same size" — and the difference is what made the grid blank out while it was being scrolled.
 * `LazyVerticalGrid` gives a `Fixed(3)` split's rounding remainder to the leading columns, so on a 420dpi phone
 * column 0 is 304px wide and the other two are 303. Every newly composed tile therefore reported a size the last one
 * disagreed with; the catalog's request compared unequal, and the whole map was cleared and repainted — during a
 * scroll, which is precisely when it is being looked at. A pixel is not a different picture, and what the render loop
 * needs from this number is that it hold still.
 *
 * @param thumbnails what has been painted so far, keyed by design — partial, and empty while a repaint is under way.
 * @param onTileSize the pixel size of a tile, reported once per opening. Deliberately not computed from the grid's own
 *   width: see `WallpaperStudioViewModel.previewDesigns`. A panel resized *while open* (a fold, not a rotation, which
 *   rebuilds the composition) keeps the size it opened at until it is closed and opened again.
 * @param onGone called when the grid leaves the composition, which is what stops the catalog being repainted behind a
 *   panel nobody is looking at.
 */
@Composable
internal fun WallpaperDesignGrid(
    selected: WallpaperDesign,
    thumbnails: Map<WallpaperDesign, Bitmap>,
    onTileSize: (Int, Int) -> Unit,
    onGone: () -> Unit,
    onPick: (WallpaperDesign) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Owned here rather than by the screen, so a caller cannot open the grid and forget to close it — the same reason
    // the shared studio surface carries its own hit target.
    DisposableEffect(Unit) { onDispose(onGone) }

    // Zero until the first tile lands, and never re-read after that — see the KDoc for what a second answer costs.
    var tile by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(tile) { if (tile != IntSize.Zero) onTileSize(tile.width, tile.height) }

    val gridState = rememberLazyGridState()
    // Keyed on Unit rather than on [selected]: this is where the grid *opens*, not something it does on every pick.
    // Landing it one row up leaves the selected design with context above it rather than pinned to the top edge.
    LaunchedEffect(Unit) {
        val row = WallpaperDesign.entries.indexOf(selected) / 3
        gridState.scrollToItem(((row - 1) * 3).coerceAtLeast(0))
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(WallpaperDesign.entries, key = { it.name }) { design ->
            DesignTile(
                design = design,
                thumbnail = thumbnails[design],
                selected = design == selected,
                onTileSize = { width, height -> if (tile == IntSize.Zero) tile = IntSize(width, height) },
                onPick = { onPick(design) },
            )
        }
    }
}

/**
 * One design in the grid: its render, ringed when it is the one showing, over its name.
 *
 * **The name stays, under the picture rather than instead of it.** A thumbnail says what a design looks like and a
 * name says which one it is; a user who has found the look they want still needs the second to describe it, to come
 * back to it, or to match it against anything written down.
 *
 * **Every tile offers its size and only the first offer is taken**, rather than one designated tile reporting. A
 * designated one would have to be the first item, which scrolls out of composition and then has nothing left to say;
 * taking every offer is what blanked the grid, since the columns differ by a pixel. See the panel's KDoc.
 */
@Composable
private fun DesignTile(
    design: WallpaperDesign,
    thumbnail: Bitmap?,
    selected: Boolean,
    onTileSize: (Int, Int) -> Unit,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.clickable(onClickLabel = design.label, onClick = onPick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Taller than wide, toward the screen it stands for without becoming the sliver a true 9:19.5 tile
                // would be — see the panel's KDoc for what that costs and why it is paid.
                .aspectRatio(0.78f)
                .onSizeChanged { onTileSize(it.width, it.height) }
                .clip(shape)
                // Under the render rather than behind nothing: a tile is an empty wash until its render lands, and
                // this is what the render fades in over.
                .background(StudioContentColor.copy(alpha = 0.10f))
                .then(if (selected) Modifier.border(2.dp, StudioContentColor, shape) else Modifier),
        ) {
            // A fade because a tile arriving is a picture arriving, which is the studio's own rule for the preview —
            // and thirty-two of them snapping in one at a time reads as the panel flickering rather than as it
            // loading. Keyed on the bitmap, so a repaint in a new palette dissolves rather than cutting.
            Crossfade(targetState = thumbnail, label = "designThumbnail") { shown ->
                if (shown != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = null,
                        // Painted at exactly this size, so the scale only matters for the frame between a resize and
                        // the render that answers it — where filling is the lesser lie of the two.
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Text(
            text = design.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) StudioContentColor else StudioContentColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/**
 * A short, human name for the tile — the enum name is a code identifier, not a label.
 *
 * Kept short deliberately: it sits under a tile a third of a phone's width, where a second line of text would cost
 * more grid than the word is worth.
 */
private val WallpaperDesign.label: String
    get() = when (this) {
        WallpaperDesign.LINEAR_GRADIENT -> "Gradient"
        WallpaperDesign.MESH_GRADIENT -> "Mesh"
        WallpaperDesign.FLOW_FIELD -> "Flow"
        WallpaperDesign.TRIANGULAR_FACETS -> "Facets"
        WallpaperDesign.VORONOI -> "Voronoi"
        WallpaperDesign.PLASMA -> "Plasma"
        WallpaperDesign.CONTOUR -> "Contour"
        WallpaperDesign.WAVES -> "Waves"
        WallpaperDesign.BAUHAUS -> "Bauhaus"
        WallpaperDesign.MONDRIAN -> "Mondrian"
        WallpaperDesign.CONFETTI -> "Confetti"
        WallpaperDesign.TRUCHET -> "Truchet"
        WallpaperDesign.METABALLS -> "Blobs"
        WallpaperDesign.RIBBONS -> "Ribbons"
        WallpaperDesign.DOT_GRID -> "Dot Grid"
        WallpaperDesign.HALFTONE -> "Halftone"
        WallpaperDesign.FLOW_LINES -> "Flow Lines"
        WallpaperDesign.RIBBON_FLOW -> "Ribbon Flow"
        WallpaperDesign.POLYGON_CASCADE -> "Cascade"
        WallpaperDesign.DIAGONAL_BANDS -> "Bands"
        WallpaperDesign.GRADIENT_COLUMNS -> "Columns"
        WallpaperDesign.LOUVERS -> "Louvers"
        WallpaperDesign.SOFT_OVERLAPS -> "Overlaps"
        WallpaperDesign.WAVE_DIVIDERS -> "Wave Dividers"
        WallpaperDesign.RIBBED_GLASS -> "Ribbed Glass"
        WallpaperDesign.VITRALL -> "Vitrall"
        WallpaperDesign.MODERN_MOSAIC -> "Mosaic"
        WallpaperDesign.ROUNDED_TILES -> "Bars"
        WallpaperDesign.IMPASTO -> "Impasto"
        WallpaperDesign.SPRAY -> "Spray"
        WallpaperDesign.PLANET -> "Planet"
        WallpaperDesign.MARBLE -> "Marble"
    }
