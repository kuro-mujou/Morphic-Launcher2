package inkspire.morphic.core.designsystem.picker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * Choose an installed app from a searchable list.
 *
 * **Placed in `core:designsystem` on its first consumer rather than its second**, which is a deliberate exception
 * to this codebase's usual extract-when-the-second-arrives rule (`IconPreviewPlate`'s). The reason is that the
 * other consumers were already *named and blocked*: HOME's surface menu has no "Add app" verb, the home vertical
 * list had no "Add apps" row (so its contents were whatever the seed put there), and a folder could not be filled
 * except by dragging. Two of the three are built and open this; only HOME's own surface menu is still waiting, which
 * is the bet paying out. L1 kept its equivalent here too — as well as a second, near-duplicate picker in `feature:home`,
 * which is the outcome worth not repeating, and the reason both multi-select consumers here share one sheet.
 *
 * **It takes a list, not a repository.** `core:designsystem` has no business knowing where apps come from, and
 * every caller already holds them: each supplies the list from its own ViewModel, which is also what lets a caller
 * filter first (a folder picker offers only apps not already in it).
 *
 * **A grid of icons, not a list of rows**, which is L1's shape and the right one for the job: a picker is browsed
 * by *recognition*, and an icon is what a user recognizes an app by. The grid puts three to five times as many apps
 * on screen as the 64dp rows this drew before, which is what makes ticking several of them one act rather than a
 * scroll between each. The rows are not missed — `AppRowCell` is still what a *list surface* draws, where a row is
 * the item rather than a way to browse it.
 *
 * **Both selection modes, decided by [selected].** It went in single-select only, on the grounds that guessing at a
 * multi-select variant would be designing for a caller that did not exist. The icon container's settings screen is
 * that caller: filling a container one app at a time, with the sheet closing after each, is the wrong shape for
 * what is usually one deliberate act of "put these four in here". The addition is a nullable set rather than a
 * second composable, because everything else — the search, the collator, the rows, the empty states — is identical,
 * and two pickers that had to agree about all of it is the duplication this design system keeps not making.
 *
 * @param apps what to offer, in the order to offer it. Not re-sorted here — the caller's order is the answer.
 * @param onPick a tap on a row. In single-select that is the choice; in multi-select it is a **toggle**, and the
 *   caller commits when it is done.
 * @param selected null for single-select. A set — even an empty one — puts a checkbox on every row and makes
 *   [onPick] a toggle. Held by the caller rather than here, so the commit reads the same state the rows drew.
 * @param searchState hoisted so it survives a configuration change, per the design system's text-field rule: the
 *   field's own KDoc explains why this is the one component whose state stays with the caller.
 */
@Composable
fun AppPicker(
    apps: List<AppInfo>,
    onPick: (ComponentKey) -> Unit,
    modifier: Modifier = Modifier,
    selected: Set<ComponentKey>? = null,
    searchState: TextFieldState = rememberTextFieldState(),
    placeholder: String = "Search apps",
) {
    // **A locale-aware collator, not `contains` on a lowercased string.** The APPS surface already learned this
    // one: `lowercase()` compares raw UTF-16, so an accented label sorts and matches as if it were a different
    // alphabet. Here it matters for matching "Éditeur" when the user types "e".
    val collator = remember { labelCollator() }
    val query by remember { derivedStateOf { searchState.text.toString().trim() } }
    val matches = remember(apps, query, collator) {
        if (query.isEmpty()) apps else apps.filter { it.label.matchesLabel(query, collator) }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MorphicTextField(
            state = searchState,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        // **Adaptive columns rather than a fixed count**, so one grid serves a phone, a tablet and the narrower
        // bottom sheet the multi-select consumers open it in without any of them stating a number.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(matches, key = { it.componentKey.flatten() }) { app ->
                PickerCell(
                    app = app,
                    // Null stays null: a single-select picker draws no marks at all, rather than drawing every one
                    // of them unselected, which would promise a commit step it does not have.
                    selected = selected?.let { app.componentKey in it },
                    onClick = { onPick(app.componentKey) },
                )
            }
        }
        // **"Nothing matched" and "nothing has arrived yet" are different**, and saying the first when the second
        // is true reads as a broken picker: an empty list with an empty query is a caller whose apps have not
        // loaded, not a search that failed. Only a non-empty query can fail to match.
        if (matches.isEmpty() && query.isNotEmpty()) {
            Text(
                text = "No apps match \u201C$query\u201D",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * One app in the grid: its icon, its name under it, and a mark when it is chosen.
 *
 * **The whole cell is the target**, not the icon — a 48dp icon inside a 76dp cell leaves a ring of dead space that a
 * thumb lands in constantly, and in multi-select that miss costs a tick rather than a launch.
 *
 * **No plate behind the icon, and nothing here arranges that.** `AppIcon` drops it wherever `LocalOverFilm` is set,
 * which is every surface this picker opens on — a bottom sheet, or the icon studio's own frosted screen. L1 had to
 * say so explicitly (`LocalSkinBackdropAllowed provides false`) because it had no such rule; here it falls out.
 *
 * @param selected null in single-select, which draws no mark at all.
 */
@Composable
private fun PickerCell(app: AppInfo, selected: Boolean?, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            AppIcon(
                component = app.componentKey,
                contentDescription = app.label,
                sizePx = with(LocalDensity.current) { 48.dp.roundToPx() },
                modifier = Modifier.size(48.dp),
            )
            if (selected == true) SelectedMark(size = 18.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The tick on a chosen cell: a filled disc with a check struck through it.
 *
 * **Drawn rather than imported**, which is this module's standing trade — it carries no material-icons dependency,
 * and `MorphicResetButton` and `TopActionZone` draw their marks by hand for the same reason.
 *
 * A **disc** behind the check rather than a bare stroke, because the mark sits on an app's own artwork: a tick alone
 * disappears into a light icon and a dark one in turn, where a disc in [MorphicColors.accent] is the palette's
 * high-contrast emphasis and reads against both.
 */
@Composable
private fun SelectedMark(size: Dp) {
    val colors = LocalMorphicColors.current
    Canvas(Modifier.size(size)) {
        drawCircle(color = colors.accent)
        checkPath(colors.onAccent)
    }
}

/** The check itself, as fractions of the disc it is struck on — two strokes, joined, so the corner is not a notch. */
private fun DrawScope.checkPath(color: Color) {
    val side = size.minDimension
    val stroke = Stroke(width = side * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(side * 0.28f, side * 0.52f)
        lineTo(side * 0.44f, side * 0.68f)
        lineTo(side * 0.74f, side * 0.34f)
    }
    drawPath(path, color, style = stroke)
}
