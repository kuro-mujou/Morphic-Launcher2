package inkspire.morphic.feature.settings.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.feature.settings.SettingsSection
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders until the settings port brings real row components with it. */
private val ScreenPadding = 20.dp

/**
 * The **surface register**: for each edge of HOME, which surface it opens and in which layout.
 *
 * The first real settings section, and the one that makes the launcher a launcher — until an edge is bound here, no
 * edge of HOME is swipeable and the app list is unreachable by gesture. Choosing an option writes straight through
 * [SurfaceRegisterViewModel] to `data:settings`, and `feature:shell` is watching the same flow, so the change takes
 * effect on HOME immediately with nothing to apply or confirm.
 *
 * **It is a cross, because the setting is spatial** ([SurfaceRegisterCross]) — L1's `SurfaceRegister`, ported. Which
 * edge opens what is a fact about *where things are*, and the four labeled chip groups this replaced made the reader
 * rebuild that arrangement in their head from a list. Five cards in a plus simply are it.
 *
 * **The cross replaced chips, which had replaced a segmented control**, and the reason chips won then is the reason
 * they lose now: six options per edge is too many to lay out in one row, so they wrapped. What that reasoning missed is
 * that the *edges* were the part with a shape, not the options — so the options moved into a modal
 * ([SideBindingPicker]) and the edges got the picture.
 *
 * **HOME's pairing is *not* offered here, and neither is `transition`.** The rule is unchanged — a control appears
 * when the thing it configures exists — but the pairing fails a different test: it would exist twice. A picker on the
 * center card and the segmented control at the head of the Home section are two live controls for one setting, and the
 * section is where a user goes to change home. So the center card carries only its gear. What this cross keeps is what
 * it is for: **where** surfaces are. `SurfacePager` still implements only `SLIDE`, so the transition stays out.
 *
 * **One picker, hoisted**, rather than one per slot: *which* edge is being filled is this screen's state, so at most one
 * dialog exists whatever the cross is doing.
 *
 * A **detail**, not a screen: the theme, the background, the app bar and back all belong to `SettingsScreen`, which
 * has one of each for what may be two panes.
 */
@Composable
internal fun SurfaceRegisterDetail(
    onOpenSection: (SettingsSection, AppsLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SurfaceRegisterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var picking by remember { mutableStateOf<HomeEdge?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        SurfaceRegisterCross(
            homeLayout = state.register.homeLayout,
            bindings = state.register.sides,
            onPick = { picking = it },
            onOpenSettings = onOpenSection,
        )
    }

    val edge = picking
    if (edge != null) {
        SideBindingPicker(
            edge = edge,
            selected = (state.register.sides[edge] as? SideBinding.Apps)?.layout,
            onSelect = { layout ->
                viewModel.bindApps(edge, layout)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
