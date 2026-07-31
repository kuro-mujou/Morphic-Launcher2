package inkspire.morphic.data.settings.internal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.SurfaceTransition
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.data.settings.IconOverride
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.data.settings.SurfaceMetrics
import inkspire.morphic.data.settings.SurfaceRegister
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.serializer

/**
 * The single DataStore backing every settings slice.
 *
 * A `Context` extension because that is the only shape `preferencesDataStore` offers, and it must be declared once at
 * file scope — creating two stores over one file throws at runtime.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

/** The surface register's slice: one key, one blob. */
private val SurfaceRegisterSlice = SettingsSlice(
    name = "surface_register",
    serializer = serializer<SurfaceRegister>(),
    default = SurfaceRegister.Default,
)

/** The per-grid metric overrides: one key, one blob, sparse inside. */
private val SurfaceMetricsSlice = SettingsSlice(
    name = "surface_metrics",
    serializer = serializer<SurfaceMetrics>(),
    default = SurfaceMetrics.Default,
)

/**
 * Default [SettingsRepository]: one Preferences DataStore, one key per slice, each holding a JSON blob.
 *
 * **A read decodes one slice, and a write rewrites one slice.** That is the whole structural difference from L1, whose
 * every mutator called `prefs.toLauncherSettings()` *inside* its edit transaction — deserializing all ~265 keys to
 * change one field, then rewriting every key of that field's group (moving one slider rewrote 18). Scoping storage to
 * slices makes the cost proportional to what changed instead of to how many settings exist.
 *
 * `internal` so only Koin constructs it; consumers depend on [SettingsRepository].
 */
internal class SettingsRepositoryImpl(
    context: Context,
    private val dispatchers: AppDispatchers,
) : SettingsRepository {

    private val dataStore = context.settingsDataStore

    override val surfaceRegister: Flow<SurfaceRegister> = dataStore.read(SurfaceRegisterSlice) { it }

    override suspend fun setHomeLayout(layout: HomeLayout) =
        update(SurfaceRegisterSlice) { copy(homeLayout = layout) }

    override suspend fun setSide(edge: HomeEdge, binding: SideBinding?) =
        update(SurfaceRegisterSlice) {
            // Unbinding *removes* the key rather than storing a null: the swipeable set is the map's key set, so an
            // edge present-but-null would be a second way to say "not swipeable" that every reader would have to know
            // about.
            copy(sides = if (binding == null) sides - edge else sides + (edge to binding))
        }

    override suspend fun setSurfaceTransition(transition: SurfaceTransition) =
        update(SurfaceRegisterSlice) { copy(transition = transition) }

    // Resolved here rather than by the caller: the blueprint supplies the base, the slice supplies the difference.
    // `distinctUntilChanged` inside `read` then means a consumer only wakes when *its own* grid's resolved value
    // changes — editing the dock's icon size does not recompose the app drawer.
    override fun iconSizing(slot: GridSlot, device: DeviceConfiguration): Flow<IconSizing> =
        dataStore.read(SurfaceMetricsSlice) { it.iconSizing(slot, device, base = slot.blueprint.icon) }

    override suspend fun updateIcon(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: IconOverride.() -> IconOverride,
    ) = update(SurfaceMetricsSlice) { withIconOverride(slot, device, transform) }

    /**
     * Streams [slice], decoded and projected through [project], skipping re-emissions of an equal *projected*
     * value.
     *
     * **Projecting before `distinctUntilChanged` is what makes a narrow read narrow.** DataStore re-emits the whole
     * `Preferences` when *any* key changes, so without this every consumer would wake on every unrelated write.
     * `iconSizing` asks for one grid on one device: a write that changes some other grid's override still decodes
     * here, but projects to the same value and is dropped instead of waking that consumer.
     *
     * Both the decode and the comparison run on `io`, since `flowOn` applies to everything upstream of it.
     */
    private fun <T, R> DataStore<Preferences>.read(slice: SettingsSlice<T>, project: (T) -> R): Flow<R> {
        val key = stringPreferencesKey(slice.name)
        return data
            .map { project(slice.decode(it[key])) }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)
    }

    /**
     * Applies [transform] to [slice] atomically.
     *
     * The read, the transform and the write all happen inside one `edit`, so a concurrent write to the same slice
     * cannot be lost. (L1 had one mutator that read *outside* its transaction — a genuine lost-update race, and the
     * reason this is a single helper rather than a pattern each method repeats.)
     */
    private suspend fun <T> update(slice: SettingsSlice<T>, transform: T.() -> T) {
        val key = stringPreferencesKey(slice.name)
        dataStore.edit { prefs -> prefs[key] = slice.encode(slice.decode(prefs[key]).transform()) }
    }
}
