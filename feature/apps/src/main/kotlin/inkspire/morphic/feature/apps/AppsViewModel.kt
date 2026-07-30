package inkspire.morphic.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.layout.AppsOrderRepository
import inkspire.morphic.data.layout.LayoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * Screen-level state holder for the APPS surface: streams the app collection, puts it in display order, keeps the
 * pager's stored arrangement in step with what is installed, and launches on tap. Plain MVVM — the UI reads one
 * immutable [state] flow and calls typed methods, with no sealed-intent/reducer layer.
 *
 * **It is per surface, not per layout.** Every layout renders the same collection, so switching layout must not
 * reload anything — which is also why both shapes in [AppsState] are always maintained, even though a given layout
 * reads only one. Keeping the pager's store synced while the list is on screen is the point, not waste: the
 * arrangement is the user's, and it should be current the moment they switch to it.
 *
 * **Two kinds of order, and only one of them is stored.** The derived layouts are a function of the app cache
 * ([apps], A–Z); the pager is an arrangement the user makes, so it lives in [AppsOrderRepository]. This holder is
 * where the two meet — it hands the repository the A–Z order so newly installed apps append in a sensible place,
 * and hands the UI the resolved pages.
 */
class AppsViewModel(
    private val appRepository: AppRepository,
    private val appsOrderRepository: AppsOrderRepository,
    layoutRepository: LayoutRepository,
    private val appLauncher: AppLauncher,
) : ViewModel() {

    /**
     * How many entries fit one pager page, or null until the surface has measured its grid.
     *
     * The capacity belongs to the UI: it comes from `AppsPagerGrid` resolved against the detected device, which is
     * a `@Composable` read — the same reason `HomeViewModel` is told its `GridConfig` rather than guessing one.
     */
    private val pagerCapacity = MutableStateFlow<Int?>(null)

    private val sortedApps: Flow<List<AppInfo>> =
        appRepository.observeApps().map { apps -> apps.sortedWith(LabelOrder) }

    /**
     * The stored pager arrangement, re-subscribed whenever the capacity changes.
     *
     * Emits empty (rather than nothing) before a capacity is known, so the derived layouts still render on the
     * first frame — a `combine` waits for *every* source, and a list that never appeared because the pager had not
     * been measured would be a blank screen for a layout that doesn't use it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pagerItems: Flow<List<List<IconItem>>> =
        pagerCapacity.flatMapLatest { perPage ->
            if (perPage == null) flowOf(emptyList()) else appsOrderRepository.pagerPages(ORIENTATION, perPage)
        }

    val state: StateFlow<AppsState> =
        combine(sortedApps, pagerItems, layoutRepository.folders()) { apps, pages, folders ->
            val infoByComponent = apps.associateBy { it.componentKey }
            val folderById = folders.associateBy { it.id }
            AppsState(
                apps = apps,
                pagerPages = pages.map { page ->
                    page.mapNotNull { item ->
                        when (item) {
                            // An entry the app cache can't resolve is dropped rather than drawn blank; the store
                            // still holds it, and `syncPager` below removes it once the cache says it is gone.
                            is IconItem.App -> infoByComponent[item.component]?.let(AppsItem::App)
                            is IconItem.Folder -> folderById[item.folderId]?.let { folder ->
                                AppsItem.Folder(folder, folder.apps.mapNotNull(infoByComponent::get))
                            }
                        }
                    }
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsState())

    init {
        // The cache is offline-first, so the surface renders from it immediately and this only tops it up. Done
        // here rather than assumed of whoever ran first, so the surface stands alone (the dev harness can open it
        // without home ever having been shown).
        // TODO(B6 data:apps): once the AppEvent listener keeps the cache live (and prunes uninstalls), warming it
        //  belongs to that listener at startup, not to each screen that reads it.
        viewModelScope.launch { appRepository.refresh() }

        // Keep the stored arrangement in step with reality. One collector covers all three cases that change it —
        // first run (an empty store makes every app "new"), an install or uninstall, and a capacity change — which
        // is why there is no separate seed step to drift out of sync with this one. `syncPager` writes nothing when
        // nothing changed, so the common launch costs a read.
        viewModelScope.launch {
            combine(sortedApps, pagerCapacity) { apps, perPage -> apps to perPage }
                .collect { (apps, perPage) ->
                    if (perPage != null) {
                        appsOrderRepository.syncPager(ORIENTATION, perPage, apps.map { it.componentKey })
                    }
                }
        }
    }

    /**
     * Supplies the pager's resolved grid (from the blueprint, for the detected device). Idempotent per capacity:
     * only the number of entries per page matters here, so a config that resolves to the same product is ignored.
     */
    fun setPagerGrid(config: GridConfig) {
        pagerCapacity.value = config.rows * config.cols
    }

    /** Opens the app for [component] (a tap). Fire-and-forget — [AppLauncher] swallows a stale component. */
    fun launch(component: ComponentKey) = appLauncher.launch(component)

    companion object {
        val ORIENTATION = Orientation.PORTRAIT
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
         * emission order and could visibly swap between refreshes — including, now, changing where a newly
         * installed app lands on the pager.
         */
        private val LabelOrder: Comparator<AppInfo> = run {
            val collator = Collator.getInstance()
            Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
                .thenBy { it.componentKey.flatten() }
        }
    }
}
