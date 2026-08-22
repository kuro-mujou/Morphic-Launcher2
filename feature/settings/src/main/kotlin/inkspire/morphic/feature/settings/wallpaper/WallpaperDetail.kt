package inkspire.morphic.feature.settings.wallpaper

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.wallpaper.WallpaperService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.data.wallpaper.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.text.Collator

/**
 * **Wallpaper**: the image the launcher owns, and where to put it.
 *
 * A two-page pager of *modes* over three horizontal browse shelves. Each mode page
 * is one anatomy — a header row carrying the mode's name, its status, and the control that applies it; a preview band;
 * and a row of the actions that change what that mode holds. Putting the modes side by side rather than stacked is what
 * makes them read as **alternatives** — only one of them is ever the wallpaper — which a vertical list of two groups
 * does not say.
 *
 * **The pieces L2 keeps, because they are behavior rather than look:**
 * - **A capture cannot be applied.** It is a picture *of* the wallpaper, taken for the effects to sample, so the
 *   repository declines it and the page says so on its status line instead of offering a dead button.
 * - **The rotating pair is applied by the *system's* chooser**, never silently — the platform insists the user confirm
 *   a live wallpaper — so its button opens that chooser and the section re-asks on resume whether ours ended up
 *   active — a refreshed read rather than a repaired copy.
 * - **Choosing an image opens [WallpaperCropScreen]**, which is what writes. This section reads the store and issues
 *   one command.
 *
 * **Two things worth knowing about the drawing:**
 * - **One button and a chevron, not a split button.** Applying always asks *where*, so both halves would open the same
 *   menu — a seam over a single action. The chevron stays, as the affordance that says "this opens something".
 * - **A preview keeps the screen's aspect ratio inside the band.** The stored file is already cropped to this screen,
 *   so stretching it across a landscape band would show a crop the device never displays. The band is full
 *   width, [PreviewHeight] tall, and the picture sits in it at the shape it will actually be seen at, which is also
 *   what makes the rotating page's two tiles legible as *portrait* and *landscape*.
 */
@Composable
internal fun WallpaperDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<WallpaperViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    // The **whole** window, insets included: a wallpaper sits under the system bars, so the preview is the shape of
    // what will actually be covered. Every other section measures the *usable* area instead, and the difference is
    // exactly that — those size things the user reaches, this sizes something they only look at.
    val windowSize = LocalWindowInfo.current.containerSize
    val screenRatio = if (windowSize.height > 0) {
        windowSize.width.toFloat() / windowSize.height.toFloat()
    } else {
        DEFAULT_SCREEN_RATIO
    }

    // A pick opens the **crop screen** rather than writing: the user frames the image there, and that screen saves.
    // Nothing here passes a size — the viewport the user frames against is what gets stored. Photo Picker rather
    // than a document-open intent: no storage permission.
    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) navigator.goTo(WallpaperCropRoute(uri.toString()))
    }
    val portraitPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) navigator.goTo(WallpaperCropRoute(uri.toString(), CropTarget.ROTATING_PORTRAIT))
    }
    val landscapePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) navigator.goTo(WallpaperCropRoute(uri.toString(), CropTarget.ROTATING_LANDSCAPE))
    }

    // Whether *our* live wallpaper is the active one is a system read, and the only way it changes is the user
    // confirming in the system's chooser — which happens while this screen is stopped. So it is re-asked on resume.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRotatingActive()
        onPauseOrDispose { }
    }

    // The shelf of *installed* live wallpapers is a package-manager query with drawable loading in it, so it is read
    // once, off the main thread, sorted with a locale-aware `Collator` rather than `lowercase()`, which compares
    // raw UTF-16 and files every accented label after `Z`. Our own service is named
    // rather than found, so the shelf can leave it out (see `loadInstalledLiveWallpapers`).
    val ownService = remember(viewModel) { viewModel.rotatingServiceComponent() }
    var liveWallpapers by remember { mutableStateOf<List<LiveWallpaperEntry>>(emptyList()) }
    LaunchedEffect(ownService) {
        liveWallpapers = withContext(Dispatchers.IO) { loadInstalledLiveWallpapers(context, exclude = ownService) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "modes") {
            WallpaperModePager(
                state = state,
                screenRatio = screenRatio,
                onApply = viewModel::apply,
                onApplyRotating = { openLiveWallpaperChooser(context, viewModel.rotatingServiceComponent()) },
                onChooseImage = { singlePicker.launch(imageRequest) },
                onCaptureScreen = { navigator.goTo(WallpaperCaptureRoute) },
                onPickPortrait = { portraitPicker.launch(imageRequest) },
                onPickLandscape = { landscapePicker.launch(imageRequest) },
            )
        }

        wallpaperShelf(title = "My wallpapers") {
            item { EmptyHint("Your own wallpapers will show here") }
        }

        wallpaperShelf(title = "Backdrops", trailing = "By Unsplash") {
            item { EmptyHint("Connect an online source to browse wallpapers") }
        }

        wallpaperShelf(title = "Live wallpapers") {
            if (liveWallpapers.isEmpty()) {
                item { EmptyHint("No live wallpapers installed") }
            } else {
                items(liveWallpapers, key = { it.component.flattenToString() }) { entry ->
                    LiveWallpaperCard(entry = entry, onClick = { openLiveWallpaperChooser(context, entry.component) })
                }
            }
        }
    }
}

/**
 * The two wallpaper **modes**, side by side — the single image, and the rotating pair.
 *
 * A pager rather than two stacked groups because the modes are alternatives: whichever one is applied *is* the
 * wallpaper, and the other is a saved configuration waiting. Swiping between them says that; two headings do not. It
 * opens on whichever is active.
 */
@Composable
private fun WallpaperModePager(
    state: WallpaperSectionState,
    screenRatio: Float,
    onApply: (WallpaperTarget) -> Unit,
    onApplyRotating: () -> Unit,
    onChooseImage: () -> Unit,
    onCaptureScreen: () -> Unit,
    onPickPortrait: () -> Unit,
    onPickLandscape: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = if (state.rotatingActive) RotatingPage else SinglePage) {
        PageCount
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.height(200.dp + 128.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top,
    ) { page ->
        if (page == SinglePage) {
            WallpaperModePage(
                title = "Single wallpaper",
                status = when {
                    state.busy -> "Working…"
                    state.image != null && !state.applicable ->
                        "A capture isn't applied — it's there for the effects to sample."
                    state.applied -> "Active"
                    else -> null
                },
                applyControl = {
                    if (state.image == null || state.applicable) {
                        ApplyButton(
                            label = if (state.applied) "Re-apply" else "Apply",
                            enabled = state.applicable && !state.busy,
                            onSelect = onApply,
                        )
                    }
                },
                preview = {
                    PreviewTile(
                        bitmap = state.preview,
                        emptyLabel = if (state.image == null) "No wallpaper set" else "Image unavailable",
                        ratio = screenRatio,
                    )
                },
                actions = {
                    PageActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.PhotoLibrary,
                        label = "Choose image",
                        enabled = !state.busy,
                        onClick = onChooseImage
                    )
                    PageActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Screenshot,
                        label = "Capture screen",
                        enabled = !state.busy,
                        onClick = onCaptureScreen
                    )
                },
            )
        } else {
            WallpaperModePage(
                title = "Wallpaper rotate",
                status = when {
                    state.busy -> "Working…"
                    !state.hasRotating -> "Add at least one orientation to apply it."
                    state.rotatingActive -> "Active. Changing either picture updates it without re-applying."
                    else -> "Not active yet — the system asks you to confirm a live wallpaper."
                },
                applyControl = {
                    MorphicButton(
                        onClick = onApplyRotating,
                        enabled = state.hasRotating && !state.busy,
                    ) {
                        Text(if (state.rotatingActive) "Re-open" else "Apply")
                    }
                },
                preview = {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RotateSlot(
                            bitmap = state.rotatingPortrait,
                            label = "Portrait",
                            ratio = minOf(screenRatio, 1f / screenRatio),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = onPickPortrait,
                        )
                        RotateSlot(
                            bitmap = state.rotatingLandscape,
                            label = "Landscape",
                            ratio = maxOf(screenRatio, 1f / screenRatio),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = onPickLandscape,
                        )
                    }
                },
                actions = {
                    PageActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Add,
                        label = "Add portrait",
                        enabled = !state.busy,
                        onClick = onPickPortrait
                    )
                    PageActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Add,
                        label = "Add landscape",
                        enabled = !state.busy,
                        onClick = onPickLandscape
                    )
                },
            )
        }
    }
}

/**
 * One mode's page: what it is called and how it stands on the left, what applies it on the right, its preview, and the
 * actions that change what it holds.
 *
 * The anatomy is shared so the two modes cannot drift into looking like different features — which is the same reason
 * one `GridEditor` serves home and the dock.
 *
 * @param status the mode's one line of standing — "Active", why it cannot be applied, or what is missing. Null when
 *   there is nothing to say, which keeps the header a single line rather than reserving space for silence.
 */
@Composable
private fun WallpaperModePage(
    title: String,
    status: String?,
    applyControl: @Composable () -> Unit,
    preview: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = LocalMorphicColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.content)
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            applyControl()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { preview() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = actions,
        )
    }
}

/**
 * The single mode's picture, at the screen's shape, centered in the page's preview band.
 *
 * [ratio] rather than filling the band, because the stored file is *already* cropped to this screen
 * (`WallpaperRepository.setImage`) — so a band-shaped preview would
 * show a crop the device never displays. Same argument `GridEditor`'s preview makes for taking the window's ratio.
 */
@Composable
private fun PreviewTile(
    bitmap: Bitmap?,
    emptyLabel: String,
    ratio: Float,
) {
    val colors = LocalMorphicColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fitAspect(ratio)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = "Current wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.contentMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * One orientation of the rotating pair: its picture if it has one, a "+" if it does not, and a tap to replace it.
 *
 * Shaped like the orientation it stands for, which is what tells the two apart at a glance without a label.
 * Tapping a filled slot re-picks rather than opening a menu: there are two things one could do to a
 * slot, and "clear" is not worth a menu when choosing another image is the common one and clearing leaves the pair
 * half-configured anyway.
 */
@Composable
private fun RotateSlot(
    bitmap: Bitmap?,
    label: String,
    ratio: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fitAspect(ratio)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add $label image",
                    tint = colors.contentMuted
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.content,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
    }
}

/**
 * Sizes a tile to [ratio] inside a bounded box, filling whichever axis leaves it fitting.
 *
 * `aspectRatio` alone derives one dimension from the other and will happily overflow the box it is in — a 2.2∶1
 * landscape tile told to fill a 200dp height asks for 444dp of width. Choosing the axis by which side of square the
 * ratio falls on is the whole fix, and it is why the portrait and landscape slots can share one composable.
 */
private fun Modifier.fitAspect(ratio: Float): Modifier =
    if (ratio > 1f) fillMaxWidth().aspectRatio(ratio) else fillMaxHeight().aspectRatio(ratio)

/** A page's action: an icon and a label, at the tonal emphasis that puts it below the page's apply control. */
@Composable
private fun PageActionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MorphicButton(onClick = onClick, modifier = modifier, style = MorphicButtonStyle.Tonal, enabled = enabled) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Apply, and *where* — the button and the three-item menu it opens.
 *
 * The menu is the whole control rather than a secondary affordance: applying always asks where, so there is no plain
 * "apply" to run without it. That is why the button opens the menu instead of acting, and why it is one button with a
 * chevron rather than a split.
 */
@Composable
private fun ApplyButton(
    label: String,
    enabled: Boolean,
    onSelect: (WallpaperTarget) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MorphicButton(onClick = { expanded = true }, enabled = enabled) {
            Text(label)
            Spacer(Modifier.width(8.dp / 2))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Apply options", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val select = { target: WallpaperTarget ->
                expanded = false
                onSelect(target)
            }
            DropdownMenuItem(text = { Text("Home screen") }, onClick = { select(WallpaperTarget.HOME) })
            DropdownMenuItem(text = { Text("Lock screen") }, onClick = { select(WallpaperTarget.LOCK) })
            DropdownMenuItem(text = { Text("Both") }, onClick = { select(WallpaperTarget.BOTH) })
        }
    }
}

/**
 * A titled horizontal shelf — the shape any future wallpaper *source* takes.
 *
 * A `LazyListScope` extension rather than a composable because the header and the row are two items of the outer list:
 * a shelf whose row is long must scroll sideways while the page scrolls down, and nesting a `LazyRow` inside a single
 * tall item would measure every card up front.
 */
private fun LazyListScope.wallpaperShelf(
    title: String,
    trailing: String? = null,
    rowContent: LazyListScope.() -> Unit,
) {
    item(key = "header-$title") {
        val colors = LocalMorphicColors.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(text = trailing, style = MaterialTheme.typography.labelLarge, color = colors.contentMuted)
            }
        }
    }
    item(key = "row-$title") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = rowContent,
        )
    }
}

/** A shelf with nothing in it yet, saying so at the height its cards would occupy so the page does not jump later. */
@Composable
private fun LazyItemScope.EmptyHint(text: String) {
    val colors = LocalMorphicColors.current
    Box(
        modifier = Modifier
            .fillParentMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** One installed live wallpaper, as the system describes it: what to call it, what it looks like, what to start. */
private data class LiveWallpaperEntry(
    val label: String,
    val thumb: ImageBitmap?,
    val component: ComponentName,
)

/** A shelf card for an installed live wallpaper. Tapping it opens the system's preview, which is what applies it. */
@Composable
private fun LiveWallpaperCard(entry: LiveWallpaperEntry, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Column(modifier = Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .clickable(onClick = onClick),
        ) {
            if (entry.thumb != null) {
                Image(
                    bitmap = entry.thumb,
                    contentDescription = entry.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.label.ifEmpty { "Live wallpaper" },
            style = MaterialTheme.typography.labelMedium,
            color = colors.contentMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp),
        )
    }
}

/**
 * Opens the system's live-wallpaper preview for [component], falling back to its generic chooser.
 *
 * **A live wallpaper cannot be set silently** — the platform hands the user a preview with its own confirm button — so
 * this is the whole of "apply" for the rotating pair, and the section learns the outcome by asking on resume rather
 * than from a result. The direct intent is optional, and a device without it still has the chooser.
 */
private fun openLiveWallpaperChooser(context: Context, component: ComponentName): Boolean {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    }
    if (runCatching { context.startActivity(direct) }.isSuccess) return true
    val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
    return runCatching { context.startActivity(chooser) }.isSuccess
}

/**
 * Every live wallpaper the device has installed **except [exclude]**, as the package manager reports them.
 *
 * Blocking — a query plus a drawable load per result — so callers run it off the main thread. Each entry is wrapped in
 * `runCatching` because `WallpaperInfo` parses another app's metadata, and one malformed service must not empty the
 * shelf.
 *
 * **[exclude] is the launcher's own rotating service, and leaving it in was the bug.** It genuinely *is* an installed
 * live wallpaper, so the query returns it — but the rotate page above already owns it, and a shelf card beside three
 * other apps' wallpapers reads as a fourth peer rather than as the thing that page configures. Worse, the card is the
 * one route with no guard: the page's Apply is disabled until at least one orientation exists, where a card would hand
 * the user a chooser for a wallpaper with nothing to draw. This shelf is other apps' wallpapers; ours is not one of
 * them. Named by the caller from `WallpaperRepository.rotatingServiceComponent` rather than matched on our package,
 * because the component is a fact the data layer states and a package name is a guess that would also swallow any
 * other service this app ever ships.
 */
private fun loadInstalledLiveWallpapers(context: Context, exclude: ComponentName): List<LiveWallpaperEntry> {
    val pm = context.packageManager
    val resolved = pm.queryIntentServices(
        Intent(WallpaperService.SERVICE_INTERFACE),
        PackageManager.GET_META_DATA,
    )
    // A locale-aware collation, not `sortedBy { it.label.lowercase() }` — that compares raw UTF-16, so every
    // accented label sorts after `Z`. Same correction the APPS surface made to its own ordering.
    val collator = Collator.getInstance()
    return resolved.mapNotNull { info ->
        val component = ComponentName(info.serviceInfo.packageName, info.serviceInfo.name)
        // Filtered before the `WallpaperInfo` parse and the thumbnail load, not after: there is no point rasterizing
        // a preview for a card that will not be drawn.
        if (component == exclude) return@mapNotNull null
        runCatching {
            val wallpaperInfo = WallpaperInfo(context, info)
            LiveWallpaperEntry(
                label = wallpaperInfo.loadLabel(pm)?.toString().orEmpty(),
                thumb = wallpaperInfo.loadThumbnail(pm)?.toImageBitmap(),
                component = component,
            )
        }.getOrNull()
    }.sortedWith { a, b -> collator.compare(a.label, b.label) }
}

/** Rasterizes a thumbnail that is not already a bitmap — a vector or shape drawable shipped as the preview. */
private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable) {
        bitmap?.let { return it.asImageBitmap() }
    }
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    setBounds(0, 0, width, height)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

/** The two modes, in the order they are paged through. */
private const val SinglePage = 0
private const val RotatingPage = 1
private const val PageCount = 2

/** Stands in for a window that has not reported a size yet; only ever used for one frame. */
private const val DEFAULT_SCREEN_RATIO = 0.5f
