package inkspire.morphic.core.designsystem.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * Choose an installed app from a searchable list.
 *
 * **Placed in `core:designsystem` on its first consumer rather than its second**, which is a deliberate exception
 * to this codebase's usual extract-when-the-second-arrives rule (`IconPreviewPlate`'s). The reason is that the
 * other consumers are already *named and blocked*: HOME's surface menu has no "Add app" verb, the home vertical
 * list has no "Add apps" row (so its contents are whatever the seed put there), and a folder cannot be filled
 * except by dragging. All three are waiting on precisely this, and L1 kept its equivalent here too — as well as a
 * second, near-duplicate picker in `feature:home`, which is the outcome worth not repeating.
 *
 * **It takes a list, not a repository.** `core:designsystem` has no business knowing where apps come from, and
 * every caller already holds them: each supplies the list from its own ViewModel, which is also what lets a caller
 * filter first (a folder picker offers only apps not already in it).
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
        LazyColumn(Modifier.fillMaxSize()) {
            items(matches, key = { it.componentKey.flatten() }) { app ->
                val row = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { onPick(app.componentKey) }
                if (selected == null) {
                    AppRowCell(app = app, modifier = row)
                } else {
                    Row(
                        modifier = row,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The row carries the click, not the box — a checkbox is a 20dp target inside a 64dp row,
                        // and the whole row is what a user aims at. `onClick = null` is Compose's own way of saying
                        // "this control is drawn, and something else is the target", which also stops it taking a
                        // second, competing tap.
                        Checkbox(
                            checked = app.componentKey in selected,
                            onCheckedChange = null
                        )
                        AppRowCell(
                            app = app,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
        // **"Nothing matched" and "nothing has arrived yet" are different**, and saying the first when the second
        // is true reads as a broken picker: an empty list with an empty query is a caller whose apps have not
        // loaded, not a search that failed. Only a non-empty query can fail to match.
        if (matches.isEmpty() && query.isNotEmpty()) {
            Text(
                text = "No apps match “$query”",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
