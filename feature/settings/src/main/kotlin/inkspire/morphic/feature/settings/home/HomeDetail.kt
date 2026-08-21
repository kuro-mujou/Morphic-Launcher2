package inkspire.morphic.feature.settings.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.component.button.MorphicSegmentedButtons
import inkspire.morphic.core.designsystem.grid.sideZoneFraction
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.sideSlot
import inkspire.morphic.core.model.sideZoneEdge
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.component.CompanionSide
import inkspire.morphic.feature.settings.component.EditorCompanion
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.SettingsGroupCard
import inkspire.morphic.feature.settings.component.SettingsNavRow
import inkspire.morphic.feature.settings.component.of
import inkspire.morphic.feature.settings.label
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else in this module. */
private val ScreenPadding = 20.dp
private val SwitchGap = 16.dp

/** How long the mockup and the zone rows take to cross-fade between pairings. Short: the switch is one tap. */
private const val SwapMs = 180

/**
 * **Home**: which pairing HOME is, a picture of it, and a way into each of its two zones.
 *
 * A **hub**, like the Icons section and unlike every surface section — it configures one thing and routes for the
 * rest. That shape is forced rather than chosen: HOME is one surface with *two* zones, and a zone's icon controls are
 * only legible through the live preview `SurfaceDetail` pins above them. `SurfaceDetail` can pin exactly one, so both
 * zones in one pane would leave the second group's preview scrolling away from the sliders it exists to explain. Each
 * zone keeps the pane it already had; this screen is the way in.
 *
 * **One row in the settings list, which is the point.** The list used to carry HOME's two zones as two top-level rows
 * — splitting HOME by *zone* while splitting APPS not at all, and forcing both rows to rename themselves as a setting
 * in the surface register changed. `HomeLayout` is a single enum precisely so a main area and its side zone cannot be
 * chosen apart; the list was the one place in the codebase that took that couple back apart. Full argument:
 * [docs/HOME_SETTINGS_HUB_PLAN.md](../../../../../../../../docs/HOME_SETTINGS_HUB_PLAN.md).
 *
 * **The segmented control switches the pairing rather than selecting one to configure**, which is where this parts
 * company with the APPS section's chip row. That row writes nothing on purpose: a user can genuinely have one
 * arrangement on the left edge and another on the right, both live. HOME has no such state — there is exactly one of
 * it and exactly one pairing in force — so a selector that only selected would spend the screen configuring a home
 * that does not exist, beside a second control elsewhere that did switch. That second control was the register's
 * center card, and it is gone.
 *
 * A **detail**, not a screen: the theme, background, app bar and back belong to `SettingsScreen`, which has one of
 * each for what may be two panes.
 *
 * @param onOpenSection opens one of the two zone panes. The same action the surface register's gear uses — a pane
 *   opening another pane is already how this surface navigates, so the hub needed no mechanism of its own.
 */
@Composable
internal fun HomeDetail(
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<HomeHubViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        HomeLayoutSwitch(selected = state.layout, onSelect = viewModel::setLayout)
        Spacer(Modifier.height(SwitchGap))

        // **Cross-fade, not a pager.** The two pairings are mutually-exclusive states rather than pages sitting side
        // by side, so there is nothing to travel between — and a horizontal pager here would compete with the sliders
        // and grid editors on the panes it leads to, as well as being the segmented control a second time.
        //
        // Keyed on the whole state so the mockup re-draws when the extent resolves, not only when the pairing moves.
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(SwapMs)) togetherWith fadeOut(tween(SwapMs)) },
            label = "home-pairing",
            modifier = Modifier.fillMaxWidth(),
        ) { shown ->
            Column {
                PairingMockup(shown)
                Spacer(Modifier.height(SwitchGap))
                HomeZoneRows(shown.layout, onOpenSection)
            }
        }
    }
}

/**
 * The pairing switch.
 *
 * A segmented control because there are **two** mutually-exclusive options — which is what one is for, and why this
 * does not contradict the surface register's own finding that chips beat a segmented control: that was about *six*
 * options per edge, wrapping into rows.
 */
@Composable
private fun HomeLayoutSwitch(selected: HomeLayout, onSelect: (HomeLayout) -> Unit) {
    val options = HomeLayout.entries
    MorphicSegmentedButtons(
        options = options.map { it.label },
        selectedIndex = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A picture of the pairing: the screen's shape, split into a main area and its side zone at their real proportion.
 *
 * **[GridEditor] with both bounds null**, which is the arrangement it already draws for the APPS list — the frame and
 * the companion split, no buttons and no caption. Reused rather than hand-drawn because the split is the part that is
 * easy to get subtly wrong and impossible to notice: which side the zone sits on is `SideZoneEdge`'s rule (a dock is
 * a bottom strip or a trailing rail; a widget area is a top strip or a leading rail), and a hand-rolled rectangle
 * would eventually disagree with the two panes that draw it properly.
 *
 * **The extent is the stored one**, so the strip is the size the user set rather than the blueprint's default —
 * otherwise this contradicts the zone's own pane one tap away, which is the exact fault a preview exists to prevent.
 * Falls back to the blueprint until the store answers, since a frame with no split at all would read as a pairing
 * with one zone.
 *
 * No counts are drawn inside it, deliberately: the lattice is what the *zone* panes edit, and putting a grid here
 * would invite presses on a picture with no buttons. What this says is the one thing the rows below cannot — how the
 * screen divides.
 */
@Composable
private fun PairingMockup(state: HomeHubState) {
    val window = usableWindowArea(uiInsets)
    val edge = currentDeviceConfiguration().sideZoneEdge(state.layout)
    val extentDp = (state.sideExtentDp ?: state.layout.sideSlot.blueprint.extentDp ?: 0).toFloat()

    GridEditor(
        cols = 1,
        rows = 1,
        colBounds = null,
        rowBounds = null,
        aspectRatio = window.widthDp / window.heightDp.coerceAtLeast(1f),
        onEdit = { _, _ -> },
        companion = EditorCompanion(
            fraction = window.sideZoneFraction(extentDp, edge),
            side = CompanionSide.of(edge),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The two zones of one pairing, in the order they sit on screen: main area first, then the side zone.
 *
 * Both rows go through [SettingsNavRow] with the section's own `meta`, so the hub cannot name a zone differently from
 * the way the app bar titles its pane — the vocabulary is resolved once, in one place.
 */
@Composable
private fun HomeZoneRows(
    layout: HomeLayout,
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
) {
    // The same panel the settings index puts its rows on — this hub is one tap from that list, and two runs of the
    // same row wearing different dress is exactly what a shared container prevents.
    SettingsGroupCard {
        listOf(SettingsSection.HOME_GRID, SettingsSection.DOCK).forEach { section ->
            SettingsNavRow(
                section = section,
                homeLayout = layout,
                // A hub row never marks itself: the pane it opens replaces this one rather than sitting beside it,
                // even in two-pane, so there is never a moment where both are on screen.
                selected = false,
                showChevron = true,
                onClick = { onOpenSection(section, null) },
            )
        }
    }
}
