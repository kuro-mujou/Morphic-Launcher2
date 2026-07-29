package inkspire.morphic.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * Screen-level state holder for the APPS surface: streams the app collection, puts it in display order, and
 * launches on tap. Plain MVVM — the UI reads one immutable [state] flow and calls typed methods, with no
 * sealed-intent/reducer layer.
 *
 * **It is per surface, not per layout.** Every layout renders the same collection, so switching layout must not
 * reload anything or lose scroll-independent state; a ViewModel per layout would also multiply this one small
 * pipeline five times over. When a layout needs its own arrangement (the pager's page + slot, the category
 * layouts' membership) that arrives as another flow combined in here, not as another ViewModel.
 *
 * **Nothing is persisted for the built layout.** The vertical list is a *derived* layout — its order is a
 * function of the app cache and nothing else — so there is no repository write path here at all, unlike
 * `HomeViewModel`. That is the arrangement model's split, not an omission.
 */
class AppsViewModel(
    private val appRepository: AppRepository,
    private val appLauncher: AppLauncher,
) : ViewModel() {

    val state: StateFlow<AppsState> = appRepository.observeApps()
        .map { apps -> AppsState(apps = apps.sortedWith(LabelOrder)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsState())

    init {
        // The cache is offline-first, so the list renders from it immediately and this only tops it up. Done here
        // rather than assumed of whoever ran first, so the surface stands alone (the dev harness can open it
        // without home ever having been shown).
        // TODO(B6 data:apps): once the AppEvent listener keeps the cache live (and prunes uninstalls), warming it
        //  belongs to that listener at startup, not to each screen that reads it.
        viewModelScope.launch { appRepository.refresh() }
    }

    /** Opens the app for [component] (a tap on a row). Fire-and-forget — [AppLauncher] swallows a stale component. */
    fun launch(component: ComponentKey) = appLauncher.launch(component)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * A–Z by label, **locale-aware**, then by component as a tie-break.
         *
         * A [Collator] rather than L1's `sortedBy { label.lowercase() }`: lowercasing compares raw UTF-16, which
         * puts every accented letter after `Z` (so a Vietnamese or French app list breaks into two alphabets) and
         * gets Turkish dotless-i wrong. The collator sorts by the *current locale's* rules, which is what a user
         * scanning an alphabetical list expects. Default (tertiary) strength on purpose — a primary-strength
         * collator treats `a` and `ă` as equal, which is right for *searching* and wrong for *ordering*.
         *
         * The component tie-break makes the order total: two apps can share a label (a work-profile clone of a
         * personal app is the common one), and without it their relative order would depend on the cache's
         * emission order and could visibly swap between refreshes.
         */
        private val LabelOrder: Comparator<AppInfo> = run {
            val collator = Collator.getInstance()
            Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
                .thenBy { it.componentKey.flatten() }
        }
    }
}
