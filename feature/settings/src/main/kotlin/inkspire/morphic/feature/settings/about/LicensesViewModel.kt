package inkspire.morphic.feature.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * What the licenses section shows.
 *
 * @property loading true until the manifest has been read and parsed. A real state rather than an implicit one: the
 *   parse is off the main thread, so the pane's first frame genuinely has nothing, and an empty list drawn without
 *   saying why reads as "this app uses no open-source libraries" — the opposite of what the screen is for.
 * @property failed true when the manifest could not be read or parsed at all. Also a real state, and shown as one:
 *   this screen's entire value is that it is not hand-written, so it must not be able to *look* complete while being
 *   empty.
 */
internal data class LicensesState(
    val index: LicenseIndex = LicenseIndex.Empty,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Screen-level state holder for the **open-source licenses** list.
 *
 * Reads the manifest the build generated and parses it once. Like [AboutViewModel] it sits over a snapshot rather
 * than a stream — the set of libraries in an APK cannot change while the APK is running — so the state is a
 * `MutableStateFlow` filled in `init` rather than a `stateIn` over a repository.
 *
 * @param manifest where the JSON comes from. An interface because the resource it reads belongs to `:app` — see
 *   [LicenseManifestSource] for why that indirection is worth its cost.
 */
internal class LicensesViewModel(private val manifest: LicenseManifestSource) : ViewModel() {

    private val mutableState = MutableStateFlow(LicensesState())
    val state: StateFlow<LicensesState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            // Both halves go off the main thread together: the read is a resource stream and the parse is ~80KB of
            // JSON into a few hundred objects. Neither is slow; both are pointless work to do while a frame waits.
            val parsed = withContext(Dispatchers.Default) {
                runCatching { parseLicenseManifest(manifest.readJson()) }
            }
            mutableState.value = parsed.fold(
                onSuccess = { LicensesState(index = it, loading = false) },
                onFailure = { error ->
                    // Logged rather than swallowed: a manifest that will not parse is a build problem, and the only
                    // place it can be noticed is here.
                    Timber.e(error, "Could not read the open-source manifest")
                    LicensesState(loading = false, failed = true)
                },
            )
        }
    }
}
