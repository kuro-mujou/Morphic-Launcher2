package inkspire.morphic.feature.settings.register

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.SurfaceTransition
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.label
import org.koin.androidx.compose.koinViewModel

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
 * **HOME's pairing is *not* offered here; the crossing `transition` now is.** The rule is unchanged — a control
 * appears when the thing it configures exists — but the pairing fails a different test: it would exist twice. A picker
 * on the center card and the segmented control at the head of the Home section are two live controls for one setting,
 * and the section is where a user goes to change home. So the center card carries only its gear. The transition has no
 * such second home: it is a property of the *crossing* between surfaces, which is exactly what the register is about,
 * so its picker sits under the cross now that `SurfacePager` draws all six.
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
    var pickingTransition by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SurfaceRegisterCross(
            homeLayout = state.register.homeLayout,
            bindings = state.register.sides,
            onPick = { picking = it },
            onOpenSettings = onOpenSection,
        )

        Spacer(Modifier.height(20.dp))

        // The one setting that belongs to the crossing itself rather than to an edge — so it sits under the cross,
        // not on it. A row that opens a modal, like the edges do, since six motions each want a line describing them.
        MorphicGroupPanel {
            TransitionRow(
                transition = state.register.transition,
                onClick = { pickingTransition = true },
            )
        }
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

    if (pickingTransition) {
        SurfaceTransitionPicker(
            selected = state.register.transition,
            onSelect = { transition ->
                viewModel.setTransition(transition)
                pickingTransition = false
            },
            onDismiss = { pickingTransition = false },
        )
    }
}

/**
 * The row that opens the [SurfaceTransitionPicker]: names the setting and shows the [transition] in force, with a
 * chevron because a tap opens a modal.
 *
 * Inline here rather than through `SettingsNavRow`, which is keyed to a `SettingsSection` and resolves its title from
 * `meta` — this row names a *setting within* a section, not a section, and carries a live value rather than a glyph.
 */
@Composable
private fun TransitionRow(transition: SurfaceTransition, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Switch animation",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.content,
            modifier = Modifier.weight(1f),
        )
        Text(text = transition.label, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.contentMuted,
        )
    }
}
