package inkspire.morphic.feature.home.gestureaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.AppShortcut
import inkspire.morphic.data.apps.AppShortcuts
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the state survives with nothing collecting, matching every other screen in this module. */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * One app and the shortcuts it publishes — a section of the picker's list.
 *
 * @property shortcuts empty for the great majority of apps, which is why the Shortcuts part of the list is far
 *   shorter than the Apps part above it.
 */
data class ShortcutGroup(val app: AppInfo, val shortcuts: List<AppShortcut>)

/**
 * What the picker shows, already filtered by the search box.
 *
 * **Filtering happens here rather than in the composable**, so the screen renders what it is given and the matching
 * rule has one home — the alternative is three `filter` calls in the UI drifting apart on case-sensitivity.
 *
 * @property assigned what this gesture does today, so the picker can mark the current choice. Null when the gesture
 *   is unassigned, which is what makes the "None" row the selected one.
 * @property loadingShortcuts true until the platform has answered. Shortcuts arrive later than apps — one query
 *   across every profile — and a section that appeared without warning halfway through a scroll would move the
 *   list under the finger.
 */
data class GestureActionState(
    val apps: List<AppInfo> = emptyList(),
    val shortcutGroups: List<ShortcutGroup> = emptyList(),
    val assigned: GestureAction? = null,
    val query: String = "",
    val loadingShortcuts: Boolean = true,
)

/**
 * The action picker's state holder: every app, every shortcut, and what this gesture is set to now.
 *
 * **Shortcuts are read once, on open, rather than observed.** They are a live platform question with no change
 * notification worth subscribing to, and re-reading them per keystroke would be a binder round trip per character.
 * An app installed while this screen is open therefore shows in Apps (that list *is* observed) but its shortcuts
 * appear on the next visit, which is a trade this screen can afford and a watcher would not pay for.
 *
 * **The gesture and the item come from the route**, so this is the first ViewModel here that takes per-instance
 * parameters — which is why the `NavEntry` ViewModel-store decorator matters: without it every route would be
 * handed the first instance ever created, whatever item it was built for.
 */
class GestureActionViewModel(
    private val item: GridItem,
    private val gesture: ItemGesture,
    private val settingsRepository: SettingsRepository,
    private val appShortcuts: AppShortcuts,
    appRepository: AppRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val shortcuts = MutableStateFlow<List<AppShortcut>?>(null)

    init {
        viewModelScope.launch { shortcuts.value = appShortcuts.allShortcuts() }
    }

    val state: StateFlow<GestureActionState> =
        combine(
            appRepository.observeApps(),
            shortcuts,
            query,
            settingsRepository.homeItemGestures.map { it.actionsOn(item)[gesture] },
        ) { apps, loaded, text, assigned ->
            val matching = apps.filter { it.label.matches(text) }
            GestureActionState(
                apps = matching,
                // Grouped by the app that publishes them, and apps with none are simply absent — the section is a
                // list of what *has* shortcuts rather than of every app with most rows empty.
                shortcutGroups = loaded
                    .orEmpty()
                    .groupBy { it.packageName }
                    .mapNotNull { (packageName, published) ->
                        val owner = apps.firstOrNull { it.componentKey.packageName == packageName }
                            ?: return@mapNotNull null
                        val hits = published.filter { owner.label.matches(text) || it.label.matches(text) }
                        if (hits.isEmpty()) null else ShortcutGroup(owner, hits)
                    }
                    .sortedBy { it.app.label.lowercase() },
                assigned = assigned,
                query = text,
                loadingShortcuts = loaded == null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GestureActionState())

    /** Types into the search box. */
    fun search(text: String) {
        query.value = text
    }

    /** Assigns [action], or clears the gesture when it is null — which is what the "None" row writes. */
    fun choose(action: GestureAction?) {
        viewModelScope.launch { settingsRepository.setItemGesture(item, gesture, action) }
    }

    /** Assigns an app by the key the picker row carries. */
    fun chooseApp(component: ComponentKey) = choose(GestureAction.LaunchApp(component))

    /**
     * Assigns a shortcut, keeping its label for display.
     *
     * The label is a copy rather than a lookup: resolving the live one would cost a platform query per row of the
     * sheet that shows it — see `GestureAction.LaunchShortcut`.
     */
    fun chooseShortcut(shortcut: AppShortcut) = choose(
        GestureAction.LaunchShortcut(
            id = shortcut.id,
            packageName = shortcut.packageName,
            userSerial = shortcut.userSerial,
            label = shortcut.label,
        ),
    )
}

/** Case-insensitive contains, and empty matches everything — the one matching rule the whole screen uses. */
private fun String.matches(query: String): Boolean =
    query.isBlank() || contains(query.trim(), ignoreCase = true)
