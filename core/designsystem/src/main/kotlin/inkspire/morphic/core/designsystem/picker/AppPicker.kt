package inkspire.morphic.core.designsystem.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import java.text.Collator

/** How tall one row is. A placeholder — see the "don't invent a dimension nothing owns yet" rule. */
private val PickerRowHeight = 64.dp

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
 * Single-select, because that is what every consumer above needs first and it is the smaller promise. A
 * multi-select variant is an additive change if "Add apps" wants one; guessing at it now would be designing for a
 * caller that does not exist.
 *
 * @param apps what to offer, in the order to offer it. Not re-sorted here — the caller's order is the answer.
 * @param searchState hoisted so it survives a configuration change, per the design system's text-field rule: the
 *   field's own KDoc explains why this is the one component whose state stays with the caller.
 */
@Composable
fun AppPicker(
    apps: List<AppInfo>,
    onPick: (ComponentKey) -> Unit,
    modifier: Modifier = Modifier,
    searchState: TextFieldState = rememberTextFieldState(),
    placeholder: String = "Search apps",
) {
    // **A locale-aware collator, not `contains` on a lowercased string.** The APPS surface already learned this
    // one: `lowercase()` compares raw UTF-16, so an accented label sorts and matches as if it were a different
    // alphabet. Here it matters for matching "Éditeur" when the user types "e".
    val collator = remember { Collator.getInstance().apply { strength = Collator.PRIMARY } }
    val query by remember { derivedStateOf { searchState.text.toString().trim() } }
    val matches = remember(apps, query, collator) {
        if (query.isEmpty()) apps else apps.filter { it.label.matches(query, collator) }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MorphicTextField(
            state = searchState,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(matches, key = { it.componentKey.flatten() }) { app ->
                AppRowCell(
                    app = app,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PickerRowHeight)
                        .clickable { onPick(app.componentKey) },
                )
            }
        }
        // **"Nothing matched" and "nothing has arrived yet" are different**, and saying the first when the second
        // is true reads as a broken picker: an empty list with an empty query is a caller whose apps have not
        // loaded, not a search that failed. Only a non-empty query can fail to match.
        if (matches.isEmpty() && query.isNotEmpty()) {
            Text("No apps match “$query”", modifier = Modifier.padding(16.dp))
        }
    }
}

/**
 * Whether [this] label contains [query], ignoring case and accents.
 *
 * Done by hand because `Collator` compares whole strings and there is no substring form: each window of the label
 * the same length as the query is compared, which is O(label × query) and fine for a label.
 */
private fun String.matches(query: String, collator: Collator): Boolean {
    if (query.length > length) return false
    for (start in 0..length - query.length) {
        if (collator.compare(substring(start, start + query.length), query) == 0) return true
    }
    return false
}
