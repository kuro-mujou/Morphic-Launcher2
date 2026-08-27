package inkspire.morphic.feature.home.widgetpicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.cell.CardAlpha
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.FanAnchor
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.data.layout.WidgetSpan
import inkspire.morphic.data.widgets.WidgetProvider
import inkspire.morphic.data.widgets.WidgetProviderGroup
import inkspire.morphic.feature.home.ContainerAddGlyph
import inkspire.morphic.feature.home.HomeViewModel
import inkspire.morphic.feature.home.IconArrangementSwatch
import inkspire.morphic.feature.home.LauncherBottomSheet
import inkspire.morphic.feature.home.containerPanel
import org.koin.androidx.compose.koinViewModel

/**
 * **The widget picker** — a bottom sheet listing every installed widget, grouped by the app that publishes it, with
 * one app's widgets browsed as a pager of previews.
 *
 * A two-pane sheet: a list that slides left to a detail pane for whatever was chosen, and back again. **Both kinds of
 * entry reach that pane** — an app's widgets and the launcher's own components alike ([PickerEntry]) — so nothing on
 * this sheet is placed by a single tap. Which is the point: a tile appearing on a home screen behind a sheet the user
 * is still looking at gives them no chance to see what it was or how much room it took.
 * - **Colors come from the theme.** Hardcoding white here would make this the one surface ignoring the brightness
 *   signal the whole launcher theme is built on.
 * - **The sheet itself is [LauncherBottomSheet]** — the frosted panel, the scrim, the modality claim and `uiInsets`
 *   all live there, extracted when the icon container's app picker became the second thing wanting exactly this
 *   chrome. This file is the two panes and nothing else.
 *
 * **A "Components" section sits above the apps**: an icon container and a widget container, which are things
 * the launcher itself offers rather than things an app publishes — which is why they are their own section and not
 * two more rows in the list. They were absent while nothing could draw either; they arrived with the cells, on the
 * same rule that kept them out (a row that adds an item the user cannot see is worse than a missing one). Each opens
 * a page previewing **the cell it will become**, which is what an app's widget already got and what L1 could only
 * approximate with a glyph.
 *
 * @param grid the grid a chosen widget would land on, used only to phrase its size ("3 × 2"). The *caller's* grid
 *   rather than one derived here, because home's two pairings put widgets in different zones — the pager on one,
 *   the widget area on the other — and each surface already knows its own. **Null when there is no grid to land
 *   on**, which is the widget container's case: every page of a container fills the container, so a footprint would
 *   be a promise nothing keeps.
 * @param cellWidthPx the measured cell size of that grid; a widget's span is its stated minimum divided by this.
 *   Zero before the surface has been measured, which [WidgetSpan.forMinSize] answers with no label at all rather
 *   than a wrong one.
 * @param onAddWidget **null while nothing can place a widget yet**, which hides the Add button rather than
 *   disabling it — the same nullable-lambda shape `AppsScreen`'s settings
 *   verb use for a destination that does not exist yet. The placement slice passes a real lambda and the button
 *   appears with nothing else here changing.
 * @param hasRoomFor whether the surface could take an item of that footprint **right now**. Asked per entry, so
 *   the Add row is absent for exactly the ones that would fail — see [DetailFrame]'s `onAdd`. It matters most on the
 *   **widget area**, which is a single grid drawn all at once: unlike the pager it cannot grow a page, so a widget
 *   bigger than the room left has nowhere to go and the user has to be told before binding one. The default admits
 *   everything, for a caller with no limit to state.
 * @param onAddIconContainer adds an empty icon container, laid out the way its detail page was left. Nullable on
 *   [onAddWidget]'s terms, and the caller passes null when the sheet was opened to fill a container — neither
 *   kind nests, so the section would be two rows that could not work. Null also **removes the row**, since a
 *   component with nowhere to go has nothing to preview.
 * @param onAddWidgetContainer the same, for a widget container.
 */
@Composable
internal fun WidgetPickerSheet(
    grid: GridConfig?,
    cellWidthPx: Float,
    cellHeightPx: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAddWidget: ((WidgetProvider) -> Unit)? = null,
    onAddIconContainer: ((IconArrangement) -> Unit)? = null,
    onAddWidgetContainer: (() -> Unit)? = null,
    hasRoomFor: (WidgetSpan) -> Boolean = { true },
) {
    val viewModel = koinViewModel<WidgetPickerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var opened by remember { mutableStateOf<PickerEntry?>(null) }

    // Whether a component can be placed at all — the list only offers what something will accept. **Not** the add
    // itself any more: an icon container's page carries a choice (which arrangement), so the commit has to be built
    // where that choice lives. Exhaustive over the enum, so a third component cannot be listed without saying
    // whether placing it is possible.
    val canAdd: (ComponentKind) -> Boolean = { kind ->
        when (kind) {
            ComponentKind.ICON_CONTAINER -> onAddIconContainer != null
            ComponentKind.WIDGET_CONTAINER -> onAddWidgetContainer != null
        }
    }

    LauncherBottomSheet(
        onDismiss = onDismiss,
        // Back closes the detail pane first and the sheet second, so the two read as depth rather than as a swap.
        onBack = { if (opened != null) opened = null else onDismiss() },
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = opened,
            transitionSpec = {
                // Opening pushes the list out to the left and brings the detail in from the right; going back
                // reverses it, on expressive motion from the theme rather than the animation defaults.
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
            when (target) {
                null -> ListPane(
                    groups = state.groups,
                    onOpen = { opened = PickerEntry.Widgets(it) },
                    onDismiss = onDismiss,
                    // A component is listed only when something can place it — see [WidgetPickerSheet].
                    components = ComponentKind.entries.filter(canAdd),
                    onOpenComponent = { opened = PickerEntry.Component(it) },
                )

                is PickerEntry.Widgets -> DetailPane(
                    group = target.group,
                    grid = grid,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    onBack = { opened = null },
                    onAddWidget = onAddWidget,
                    hasRoomFor = hasRoomFor,
                )

                is PickerEntry.Component -> ComponentDetailPane(
                    kind = target.kind,
                    grid = grid,
                    onBack = { opened = null },
                    onAddIconContainer = onAddIconContainer,
                    onAddWidgetContainer = onAddWidgetContainer,
                    hasRoomFor = hasRoomFor,
                )
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
    components: List<ComponentKind> = emptyList(),
    onOpenComponent: (ComponentKind) -> Unit = {},
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
            // Still reading the catalog. Distinct from an empty list, which is a real answer — see
            // [WidgetPickerState.groups].
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.content)
            }
            return@Column
        }

        // Filtering stays here rather than in the ViewModel: it is a display filter over an already-loaded list,
        // with no store behind it and nothing to persist.
        val query = search.text.toString()
        val filtered = remember(groups, query) {
            if (query.isBlank()) groups else groups.filter { it.appLabel.contains(query.trim(), ignoreCase = true) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // **Components come first, and only when nothing is being searched.** They are the launcher's own
            // offerings rather than any app's, so the search field — which filters apps by name — has nothing to
            // say about them, and leaving them pinned above a filtered list would read as two failed matches.
            if (query.isBlank() && components.isNotEmpty()) {
                item(key = "components-heading") { SectionHeading("Components") }
                items(components, key = { it.name }) { kind ->
                    ComponentRow(kind) { onOpenComponent(kind) }
                }
                item(key = "apps-heading") { SectionHeading("Apps") }
            }
            items(filtered, key = { it.packageName }) { group ->
                AppRow(group = group, onClick = { onOpen(group) })
            }
        }
    }
}

/**
 * One of the launcher's **own** placeable components, as opposed to an app's widget.
 *
 * An enum rather than a row built where its callback is, because each of these is now two views — a row in the list
 * and a page in the detail pane — and a name that differed between them would be one component reading as two.
 *
 * @property icon stands for the component in the *list*. The detail page draws the real thing instead, so this is
 *   the only place a glyph has to do the job.
 */
private enum class ComponentKind(
    val label: String,
    val description: String,
    val icon: ImageVector,
) {
    ICON_CONTAINER(
        label = "Icon container",
        description = "A panel that holds app and folder icons.",
        icon = Icons.Filled.GridView,
    ),

    // Pages between its widgets rather than stacking them, which is what the wording says.
    WIDGET_CONTAINER(
        label = "Widget container",
        description = "A panel that pages between several widgets.",
        icon = Icons.Filled.Widgets,
    ),
}

/**
 * What the detail pane is showing — one app's widgets, or one of the launcher's own components.
 *
 * **A sum type rather than two nullable states**, so "both open at once" is not representable: there is one pane and
 * one back gesture, and a second flag would need a rule forbidding the state it invented. The same reason
 * `LauncherMenuHost` holds one request for two kinds of menu.
 */
private sealed interface PickerEntry {
    data class Widgets(val group: WidgetProviderGroup) : PickerEntry
    data class Component(val kind: ComponentKind) : PickerEntry
}

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
 * container *does*, where an app's name plus a widget count does.
 */
@Composable
private fun ComponentRow(kind: ComponentKind, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = kind.icon,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = kind.label, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(
                text = kind.description,
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
 * The count in a circle earns its place — it is the one thing that tells a user whether opening
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
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accentMuted),
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
    grid: GridConfig?,
    cellWidthPx: Float,
    cellHeightPx: Float,
    onBack: () -> Unit,
    onAddWidget: ((WidgetProvider) -> Unit)?,
    hasRoomFor: (WidgetSpan) -> Boolean,
) {
    val pagerState = rememberPagerState { group.providers.size }
    val current = group.providers.getOrNull(pagerState.currentPage)
    // **The span decides both the label and the verb**, so it is resolved once here rather than by each. A page
    // reading "4 × 2" beside an Add button that cannot place a 4 × 2 is the disagreement this avoids.
    val currentSpan = current?.let { spanOf(it, grid, cellWidthPx, cellHeightPx) }
    val fits = currentSpan == null || hasRoomFor(currentSpan)

    DetailFrame(
        title = group.appLabel,
        onBack = onBack,
        // Absent, not disabled, while nothing can place a widget — see [WidgetPickerSheet]. The page is folded into
        // the same test rather than disabling the button under a finger: an out-of-range page is unreachable here,
        // since a group always publishes at least one provider, so it is a defensive null and not a state to show.
        onAdd = if (onAddWidget != null && current != null && fits) {
            { onAddWidget(current) }
        } else {
            null
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val provider = group.providers[page]
            val span = spanOf(provider, grid, cellWidthPx, cellHeightPx)
            WidgetPage(
                provider = provider,
                // Both null together — `spanOf` answers null for a null grid — but written as one test rather than
                // leaning on that, since an early return here would blank the whole page instead of its size line.
                sizeLabel = if (grid != null && span != null) span.visualLabel(grid) else "",
                // Per *page*, not per current page: a pager settles between two widgets, and a notice that belonged
                // to the one being swiped away would follow the finger across.
                roomless = span != null && !hasRoomFor(span),
            )
        }

        if (group.providers.size > 1) {
            Dots(
                current = pagerState.currentPage,
                count = group.providers.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The second pane for one of the launcher's **own** components: what it will look like, how much room it takes, and
 * the same Add button an app's widget gets.
 *
 * **It reaches the pane at all because L1's did**, and the reason holds: tapping a row and having a tile appear on a
 * home screen behind a sheet you are still looking at gives no chance to see what was placed or how big it is. What
 * L1 showed there was a Material glyph in a box — a stand-in, because L1 had no way to draw a container outside its
 * own grid. This one draws the **real cell**, which is the whole difference; see [ComponentPage].
 *
 * No pager and no dots: a component is one thing, where an app publishes several widgets.
 */
@Composable
private fun ComponentDetailPane(
    kind: ComponentKind,
    grid: GridConfig?,
    onBack: () -> Unit,
    onAddIconContainer: ((IconArrangement) -> Unit)?,
    onAddWidgetContainer: (() -> Unit)?,
    hasRoomFor: (WidgetSpan) -> Boolean,
) {
    val span = grid?.let { containerSpan(it) }
    val fits = span == null || hasRoomFor(span)
    // **The shape is chosen before the container exists**, which is why this state lives here rather than in the
    // container's settings: an arrangement is a property of the thing being placed, and picking it afterwards means
    // placing something the user did not ask for and then correcting it. The grid opens because it is the plainest
    // — a first container should not surprise anyone.
    var arrangement by remember { mutableStateOf<IconArrangement>(IconArrangement.Grid) }
    val onAdd: (() -> Unit)? = when (kind) {
        ComponentKind.ICON_CONTAINER -> onAddIconContainer?.let { add -> { add(arrangement) } }
        ComponentKind.WIDGET_CONTAINER -> onAddWidgetContainer
    }
    DetailFrame(title = kind.label, onBack = onBack, onAdd = onAdd.takeIf { fits }) {
        ComponentPage(
            kind = kind,
            sizeLabel = if (grid != null && span != null) span.visualLabel(grid) else "",
            roomless = !fits,
            arrangement = arrangement,
            onArrangement = { arrangement = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The chrome both detail panes wear: a back button beside the title, the pane's own content, and **Add to home**.
 *
 * Extracted at the second pane rather than up front. The back row is identical in both and the button is the pane's
 * one commit, so a component reached through a different-looking frame would read as a different kind of place.
 *
 * @param onAdd null when nothing can place this. **Absent, not disabled**, which is this launcher's standing rule
 *   for a verb with no op behind it.
 */
@Composable
private fun DetailFrame(
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMorphicColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.content)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        content()

        if (onAdd != null) {
            MorphicButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text("Add to home")
            }
        }
    }
}

/**
 * A component previewed as **the cell it will become** — the real container face, square, at the footprint it lands
 * with.
 *
 * **The picture is the composable the grid itself draws**, not a drawing of it: `containerPanel` for the fill and the
 * corner, [ContainerAddGlyph] for the "+". A hand-made likeness is the bug this codebase keeps rediscovering — it
 * agrees on the day it is written and drifts the first time the real one is restyled — and a preview that has
 * drifted is worse than none, because it is believed.
 *
 * **Square, because a container lands square** (`HomeViewModel.ContainerSpan` both ways). The label below reads that
 * same constant back, so the shape and the number cannot disagree.
 *
 * **It renders flat rather than frosted, and that is right here.** The sheet is itself a film, so a frosted tile
 * inside it fills with its scrim (`LocalOverFrost`) — the launcher's own no-double-blur rule, which a preview is
 * subject to like anything else on a sheet. What it can show is the shape, the corner and the affordance; what a
 * container is *made* of is only truthful over the wallpaper, which is where the user is about to put it.
 *
 * **An icon container shows its arrangement instead of the "+"**, because on this page the arrangement is what the
 * user is deciding and the tile is the only place to see it at the size it will land. The dots come from the same
 * `iconContainerSlots` the real container lays out with (see [IconArrangementSwatch]), so this is still the shape
 * the thing actually makes rather than a likeness of it. A widget container has nothing to choose and keeps the
 * "+" it will show on home.
 */
@Composable
private fun ComponentPage(
    kind: ComponentKind,
    sizeLabel: String,
    roomless: Boolean,
    arrangement: IconArrangement,
    onArrangement: (IconArrangement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The same translucent fill a widget's preview gets — see [WidgetPage] — and here it is what makes the tile
        // visible at all. A container over the film fills with its own scrim, which *is* `colors.surface`, so an
        // opaque box behind it would be that exact color and the preview would be a "+" floating on nothing.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = CardAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            // A wrapper sizes the square and the container fills it, rather than the aspect ratio going into
            // `containerPanel`'s own chain — that modifier opens with `fillMaxSize`, so the two would race.
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .aspectRatio(1f),
            ) {
                Box(modifier = Modifier.containerPanel(), contentAlignment = Alignment.Center) {
                    if (kind == ComponentKind.ICON_CONTAINER) {
                        IconArrangementSwatch(
                            arrangement = arrangement,
                            color = colors.content,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                        )
                    } else {
                        ContainerAddGlyph(contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        if (kind == ComponentKind.ICON_CONTAINER) {
            Spacer(Modifier.height(12.dp))
            ArrangementRow(selected = arrangement, onPick = onArrangement)
        }
        Spacer(Modifier.height(12.dp))
        Text(text = kind.description, style = MaterialTheme.typography.bodyMedium, color = colors.content)
        Text(text = sizeLabel, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        if (roomless) RoomlessNotice()
    }
}


/**
 * The **shapes** an icon container can be given, one entry each.
 *
 * A fan is offered once rather than once per corner: the corner is a parameter of a container that already exists,
 * adjusted where it can be seen on the wallpaper, and four near-identical swatches here would ask the user to
 * decide something they have no way to judge yet. The container's settings are where the corner is chosen.
 *
 * [FanAnchor.TOP_LEFT] stands for the family because it fills in reading order: the innermost arc sits nearest the
 * corner a left-to-right reader starts from.
 */
private val PickableArrangements = listOf<IconArrangement>(
    IconArrangement.Grid,
    IconArrangement.Circle,
    IconArrangement.Fan(FanAnchor.TOP_LEFT),
    IconArrangement.Beehive,
)

/**
 * The shapes to choose between, drawn as the shapes they make.
 *
 * A row rather than a dialog: they are told apart by looking rather than by reading, and the choice is being made
 * *while* the tile above shows the result — a dialog would cover the one thing worth watching.
 */
@Composable
private fun ArrangementRow(selected: IconArrangement, onPick: (IconArrangement) -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickableArrangements.forEach { option ->
            val chosen = option == selected
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (chosen) colors.accent.copy(alpha = 0.22f) else colors.surface.copy(alpha = CardAlpha))
                    .clickable { onPick(option) }
                    .padding(9.dp),
            ) {
                IconArrangementSwatch(
                    arrangement = option,
                    color = if (chosen) colors.accent else colors.contentMuted,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** One widget: its published preview at the top, the cells it would occupy underneath. */
@Composable
private fun WidgetPage(provider: WidgetProvider, sizeLabel: String, roomless: Boolean) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // **A translucent fill, not an opaque one, and the reason is what it does to a *white* preview.** Half the
        // widgets on a device publish artwork on a plain light background; drawn on an opaque `surface`, which is
        // near-white under a bright wallpaper, the artwork's own edge disappears and the widget reads as filling the
        // whole box — a 1×1 clock looks like a 4×2 panel, and the size label underneath is the only thing saying
        // otherwise. Over the sheet's frost this is blurred wallpaper with a light tint on it, so an edge shows.
        // [CardAlpha] rather than a value chosen here: it is what the launcher's own cards are drawn at, in the same
        // situation — a translucent tile over frost — and the two looking alike is the point.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = CardAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            val preview = provider.preview
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = provider.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
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
        if (roomless) RoomlessNotice()
    }
}

/**
 * Says why there is no Add button on this page.
 *
 * **The button is absent rather than disabled**, which is this launcher's standing rule — so something has to carry
 * the reason, or the page reads as one where adding was never offered. It sits directly under the footprint it is
 * about, since the footprint *is* the reason.
 *
 * `error` rather than `contentMuted`: nothing has gone wrong yet, but this is the one line on the page a user has to
 * read before wondering where the button went, and muted text beside a muted size label would not be read at all.
 */
@Composable
private fun RoomlessNotice() {
    Text(
        text = "Not enough room",
        style = MaterialTheme.typography.bodyMedium,
        color = LocalMorphicColors.current.error,
    )
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
 * "3 × 2" for [provider] on [grid], or empty when there is no size to promise.
 *
 * Two ways that happens, and both mean the same thing here. The grid may not have been **measured** yet, and a span
 * computed against a cell size of zero would be a confident lie. Or there may be no grid at all — a widget added to
 * a **container** fills the container whatever it asked for, so its footprint is not the caller's to state. Empty
 * either way; the widget's name above it still reads on its own.
 */
private fun spanOf(
    provider: WidgetProvider,
    grid: GridConfig?,
    cellWidthPx: Float,
    cellHeightPx: Float,
): WidgetSpan? {
    if (grid == null) return null
    return WidgetSpan.forMinSize(
        minWidthPx = provider.minWidthPx,
        minHeightPx = provider.minHeightPx,
        cellWidthPx = cellWidthPx,
        cellHeightPx = cellHeightPx,
        config = grid,
    )
}

/**
 * The footprint a container lands with, on [grid].
 *
 * `HomeViewModel.ContainerSpan` rather than a literal, for [spanOf]'s reason applied to the other kind of entry: the
 * number the page prints and the number the placement searches for have to be one number.
 */
private fun containerSpan(grid: GridConfig): WidgetSpan {
    val span = HomeViewModel.ContainerSpan * grid.cellMultiplier
    return WidgetSpan(rowSpan = span, colSpan = span)
}
