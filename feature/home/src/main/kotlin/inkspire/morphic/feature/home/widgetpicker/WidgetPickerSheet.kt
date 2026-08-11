package inkspire.morphic.feature.home.widgetpicker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.backdrop.wallpaperBackdrop
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.surface.LockSurfaceGesture
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.data.layout.WidgetSpan
import inkspire.morphic.data.widgets.WidgetProvider
import inkspire.morphic.data.widgets.WidgetProviderGroup
import org.koin.androidx.compose.koinViewModel

/** How much of the screen the sheet takes. L1's fraction. */
private const val SheetHeightFraction = 0.7f

/** The sheet's own shape — rounded at the top only, since the bottom is the screen edge. */
private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * **The widget picker** — a bottom sheet listing every installed widget, grouped by the app that publishes it, with
 * one app's widgets browsed as a pager of previews.
 *
 * A port of L1's `WidgetPickerSheet`, keeping its two-pane shape: a list of apps that slides left to a detail pane
 * for the one chosen, and back again. Three things are L2's rather than L1's, and all three are the house rules
 * rather than taste:
 * - **Colours come from the theme.** L1 hardcoded `Color.White` throughout, which over a bright wallpaper is the
 *   one surface ignoring the brightness signal the whole launcher theme is built on.
 * - **It is a frosted panel** (`wallpaperBackdrop`), the second after the context menu, so it reads the user's
 *   chosen backdrop effect like everything else that floats over the wallpaper.
 * - **Insets are `uiInsets`**, one expression, where L1 stacked `statusBarsPadding` and `navigationBarsPadding` —
 *   which between them miss a landscape cutout.
 *
 * **L1's "Components" section is here**, above the apps: an icon container and a widget container, which are things
 * the launcher itself offers rather than things an app publishes — which is why they are their own section and not
 * two more rows in the list. They were absent while nothing could draw either; they arrived with the cells, on the
 * same rule that kept them out (a row that adds an item the user cannot see is worse than a missing one).
 *
 * @param grid the grid a chosen widget would land on, used only to phrase its size ("3 × 2"). The *caller's* grid
 *   rather than one derived here, because home's two pairings put widgets in different zones — the pager on one,
 *   the widget area on the other — and each surface already knows its own.
 * @param cellWidthPx the measured cell size of that grid; a widget's span is its stated minimum divided by this.
 *   Zero before the surface has been measured, which [WidgetSpan.forMinSize] answers with no label at all rather
 *   than a wrong one.
 * @param onAddWidget **null while nothing can place a widget yet**, which hides the Add button rather than
 *   disabling it — the same nullable-lambda shape `SettingsScreen.onOpenDevHarness` and `AppsScreen`'s settings
 *   verb use for a destination that does not exist yet. The placement slice passes a real lambda and the button
 *   appears with nothing else here changing.
 * @param onAddIconContainer adds an empty icon container. Nullable on [onAddWidget]'s terms, and the caller passes
 *   null when the sheet was opened to fill a container — neither kind nests, so the section would be two rows that
 *   could not work.
 * @param onAddWidgetContainer the same, for a widget container.
 */
@Composable
internal fun WidgetPickerSheet(
    grid: GridConfig,
    cellWidthPx: Float,
    cellHeightPx: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAddWidget: ((WidgetProvider) -> Unit)? = null,
    onAddIconContainer: (() -> Unit)? = null,
    onAddWidgetContainer: (() -> Unit)? = null,
) {
    val viewModel = koinViewModel<WidgetPickerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current

    var opened by remember { mutableStateOf<WidgetProviderGroup?>(null) }
    // Back closes the detail pane first and the sheet second, so the two panes read as depth rather than as a swap.
    BackHandler { if (opened != null) opened = null else onDismiss() }

    // **The sheet is modal, and one claim says so twice.** `SurfaceGestureLock` is the launcher's answer to "does
    // something on screen own the finger?", and both behaviours fall out of holding it: `SurfacePager` gates its
    // pan on `!isLocked`, so a swipe cannot slide another surface in from under an open sheet; and
    // `surfaceMenuGestures` stands down while it is held, so a long-press on the scrim cannot open the menu of the
    // surface buried behind. The second is the same free consequence an open folder already gets.
    //
    // The declarative form, because the reason is a piece of state (this composable is on screen) rather than an
    // event — which is exactly the split `LockSurfaceGesture` documents, and the folder overlay's own case.
    LockSurfaceGesture(locked = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            // A tap anywhere off the sheet closes it. `detectTapGestures` rather than `clickable` so it stays
            // silent — no ripple and no semantics click on a full-screen scrim, exactly as the context menu's
            // tap-catcher does.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SheetHeightFraction)
                .clip(SheetShape)
                .wallpaperBackdrop(shape = SheetShape, scrimColor = colors.surfaceElevated)
                // Swallows taps on the sheet itself, so they do not reach the scrim behind it and dismiss.
                .pointerInput(Unit) { detectTapGestures { } }
                .uiInsetsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            AnimatedContent(
                targetState = opened,
                transitionSpec = {
                    // Opening pushes the list out to the left and brings the detail in from the right; going back
                    // reverses it. Expressive motion from the theme, where L1 used the animation defaults.
                    if (targetState != null) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "widgetPicker",
            ) { target ->
                if (target == null) {
                    ListPane(
                        groups = state.groups,
                        onOpen = { opened = it },
                        onDismiss = onDismiss,
                        onAddIconContainer = onAddIconContainer,
                        onAddWidgetContainer = onAddWidgetContainer,
                    )
                } else {
                    DetailPane(
                        group = target,
                        grid = grid,
                        cellWidthPx = cellWidthPx,
                        cellHeightPx = cellHeightPx,
                        onBack = { opened = null },
                        onAddWidget = onAddWidget,
                    )
                }
            }
        }
    }
}

/**
 * The first pane: a search field, the launcher's own **Components**, then one row per app that publishes widgets.
 */
@Composable
private fun ListPane(
    groups: List<WidgetProviderGroup>?,
    onOpen: (WidgetProviderGroup) -> Unit,
    onDismiss: () -> Unit,
    onAddIconContainer: (() -> Unit)? = null,
    onAddWidgetContainer: (() -> Unit)? = null,
) {
    val colors = LocalMorphicColors.current
    val search = rememberTextFieldState()
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Add to home",
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.content)
            }
        }

        MorphicTextField(
            state = search,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search widgets",
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.contentMuted) },
        )

        if (groups == null) {
            // Still reading the catalogue. Distinct from an empty list, which is a real answer — see
            // [WidgetPickerState.groups].
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.content)
            }
            return@Column
        }

        // Filtering stays here rather than in the ViewModel: it is a display filter over an already-loaded list,
        // with no store behind it and nothing to persist. L1 did the same.
        val query = search.text.toString()
        val filtered = remember(groups, query) {
            if (query.isBlank()) groups else groups.filter { it.appLabel.contains(query.trim(), ignoreCase = true) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // **Components come first, and only when nothing is being searched.** They are the launcher's own
            // offerings rather than any app's, so the search field — which filters apps by name — has nothing to
            // say about them, and leaving them pinned above a filtered list would read as two failed matches.
            val components = listOfNotNull(
                onAddIconContainer?.let {
                    ComponentRowSpec(
                        icon = Icons.Filled.GridView,
                        label = "Icon container",
                        description = "A panel that holds app and folder icons.",
                        onClick = it,
                    )
                },
                onAddWidgetContainer?.let {
                    ComponentRowSpec(
                        icon = Icons.Filled.Widgets,
                        label = "Widget container",
                        // Reworded from L1's, which says the widgets are stacked. Ours pages between them.
                        description = "A panel that pages between several widgets.",
                        onClick = it,
                    )
                },
            )
            if (query.isBlank() && components.isNotEmpty()) {
                item(key = "components-heading") { SectionHeading("Components") }
                items(components, key = { it.label }) { spec -> ComponentRow(spec) }
                item(key = "apps-heading") { SectionHeading("Apps") }
            }
            items(filtered, key = { it.packageName }) { group ->
                AppRow(group = group, onClick = { onOpen(group) })
            }
        }
    }
}

/** One **Components** row's content — kept as a value so the two are declared where the lambdas that fill them are. */
private data class ComponentRowSpec(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val onClick: () -> Unit,
)

/** A group label above a run of rows. Only drawn when there is more than one group to tell apart. */
@Composable
private fun SectionHeading(text: String) {
    val colors = LocalMorphicColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.contentMuted,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * One of the launcher's own components: an icon beside a name and a line saying what it is.
 *
 * Taller than an [AppRow] because it carries a description, which it needs: "Icon container" does not say what a
 * container *does*, where an app's name plus a widget count does. L1's own two labels, with the widget container's
 * description reworded — ours pages between its widgets rather than stacking them.
 */
@Composable
private fun ComponentRow(spec: ComponentRowSpec) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = spec.onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = spec.label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        }
    }
}

/**
 * One app's row: how many widgets it publishes, its name, and a chevron into the detail pane.
 *
 * The count in a circle is L1's, and it earns its place — it is the one thing that tells a user whether opening
 * the row is worth it, since an app with one widget is a single tap away from Add and an app with nine is a browse.
 */
@Composable
private fun AppRow(group: WidgetProviderGroup, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = group.providers.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.content,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = group.appLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.contentMuted,
        )
    }
}

/** The second pane: one app's widgets, one per page, with the size each would take. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailPane(
    group: WidgetProviderGroup,
    grid: GridConfig,
    cellWidthPx: Float,
    cellHeightPx: Float,
    onBack: () -> Unit,
    onAddWidget: ((WidgetProvider) -> Unit)?,
) {
    val colors = LocalMorphicColors.current
    val pagerState = rememberPagerState { group.providers.size }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.content)
            }
            Text(
                text = group.appLabel,
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            WidgetPage(
                provider = group.providers[page],
                sizeLabel = sizeLabel(group.providers[page], grid, cellWidthPx, cellHeightPx),
            )
        }

        if (group.providers.size > 1) {
            Dots(
                current = pagerState.currentPage,
                count = group.providers.size,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // Absent, not disabled, while nothing can place a widget — see [WidgetPickerSheet].
        if (onAddWidget != null) {
            val current = group.providers.getOrNull(pagerState.currentPage)
            MorphicButton(
                onClick = { current?.let(onAddWidget) },
                enabled = current != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text("Add to home")
            }
        }
    }
}

/** One widget: its published preview at the top, the cells it would occupy underneath. */
@Composable
private fun WidgetPage(provider: WidgetProvider, sizeLabel: String) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            val preview = provider.preview
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = provider.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            } else {
                // A provider that publishes neither a preview nor an icon. Its name is all there is to show, and
                // showing nothing at all would read as a failed load.
                Text(
                    text = provider.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.contentMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(text = provider.label, style = MaterialTheme.typography.titleMedium, color = colors.content)
        if (sizeLabel.isNotEmpty()) {
            Text(text = sizeLabel, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        }
    }
}

/** Which page of the detail pager is showing. The folder overlay's dots, at this sheet's scale. */
@Composable
private fun Dots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (index == current) colors.content else colors.contentDisabled),
            )
        }
    }
}

/**
 * "3 × 2" for [provider] on [grid], or empty when the grid has not been measured yet.
 *
 * Empty rather than a guess: a size label is a promise about where the widget will fit, and one computed against a
 * cell size of zero would be a confident lie. The row above it (the widget's name) still reads on its own.
 */
private fun sizeLabel(
    provider: WidgetProvider,
    grid: GridConfig,
    cellWidthPx: Float,
    cellHeightPx: Float,
): String = WidgetSpan.forMinSize(
    minWidthPx = provider.minWidthPx,
    minHeightPx = provider.minHeightPx,
    cellWidthPx = cellWidthPx,
    cellHeightPx = cellHeightPx,
    config = grid,
)?.visualLabel(grid).orEmpty()
