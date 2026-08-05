package inkspire.morphic.data.settings.internal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HorizontalPaddingRange
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.SurfaceTransition
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.core.model.toGridConfig
import inkspire.morphic.data.settings.AppsChrome
import inkspire.morphic.data.settings.GridOverride
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
 * How frosted surfaces render over the wallpaper: one key, one polymorphic blob.
 *
 * The only slice whose type is a **sealed hierarchy** rather than a data class, which is what puts a `"type"`
 * discriminator in the blob. `BackdropEffect`'s variants carry `@SerialName`s so that discriminator is a short stable
 * word rather than a fully-qualified class name a rename would invalidate.
 */
private val BackdropEffectSlice = SettingsSlice(
    name = "backdrop_effect",
    serializer = serializer<BackdropEffect>(),
    default = BackdropEffect.Default,
)

/** The APPS surface's chrome: one key, one blob. */
private val AppsChromeSlice = SettingsSlice(
    name = "apps_chrome",
    serializer = serializer<AppsChrome>(),
    default = AppsChrome.Default,
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

    override val backdropEffect: Flow<BackdropEffect> = dataStore.read(BackdropEffectSlice) { it }

    override val appsChrome: Flow<AppsChrome> = dataStore.read(AppsChromeSlice) { it }

    override suspend fun setSearchPlacement(placement: SearchPlacement) =
        update(AppsChromeSlice) { copy(search = placement) }

    override suspend fun setTabBarEdge(edge: VerticalEdge) = update(AppsChromeSlice) { copy(tabBarEdge = edge) }

    // Ignores the old value rather than transforming it — see the interface. The `update` helper is still the right
    // path: it is what puts the write inside a DataStore transaction.
    override suspend fun setBackdropEffect(effect: BackdropEffect) = update(BackdropEffectSlice) { effect }

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
    //
    // `requireNotNull` rather than a silent fallback: a grid with no `icon` draws tiles, not icon cells (the category
    // card), so asking it for icon sizing is a coding mistake. Answering with a plausible default would hide it — and
    // the caller would then be drawing icons at a size nothing configures.
    override fun iconSizing(slot: GridSlot, device: DeviceConfiguration): Flow<IconSizing> {
        val base = requireNotNull(slot.blueprint.icon) {
            "$slot draws tiles rather than icon cells, so it has no icon sizing to resolve"
        }
        return dataStore.read(SurfaceMetricsSlice) { it.iconSizing(slot, device, base) }
    }

    override suspend fun updateIcon(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: IconOverride.() -> IconOverride,
    ) = update(SurfaceMetricsSlice) { withIconOverride(slot, device, transform) }

    override fun gridConfig(slot: GridSlot, device: DeviceConfiguration): Flow<GridConfig> {
        val blueprint = slot.blueprint
        return dataStore.read(SurfaceMetricsSlice) { metrics ->
            blueprint.toGridConfig(metrics.gridSize(slot, device, blueprint.defaults.getValue(device)))
        }
    }

    override fun gridCols(slot: GridSlot, device: DeviceConfiguration): Flow<Int> {
        val blueprint = slot.blueprint
        return dataStore.read(SurfaceMetricsSlice) { metrics ->
            metrics.gridSize(slot, device, blueprint.defaults.getValue(device)).cols
        }
    }

    override suspend fun updateGrid(
        slot: GridSlot,
        device: DeviceConfiguration,
        transform: GridOverride.() -> GridOverride,
    ) {
        // Not a no-op but a throw: the editor only offers editable grids, so reaching a fixed one means a caller has
        // gone wrong, and silently dropping the write would hide it.
        val range = requireNotNull(slot.blueprint.editRange) { "$slot has no editor, so its size cannot be overridden" }
        update(SurfaceMetricsSlice) {
            withGridOverride(slot, device) {
                val edited = transform()
                // Floors only. A maximum depends on measured area and icon size — a runtime question, and the reason
                // `GridEditRange` carries no maxima at all.
                //
                // A null `minRows` drops the row axis entirely rather than flooring it, which is how "this grid has
                // no row count of its own" is enforced for both grids that claim it: a scrolling one takes its rows
                // from its content, the dock from its height.
                GridOverride(
                    cols = edited.cols?.coerceAtLeast(range.minCols),
                    rows = range.minRows?.let { min -> edited.rows?.coerceAtLeast(min) },
                )
            }
        }
    }

    // `requireNotNull` for the same reason `iconSizing` uses one: a grid with no extent in its blueprint is not a
    // fixed-extent strip at all, so asking how thick it is has no honest answer, and inventing one would let a caller
    // size a grid by a number nobody configured.
    override fun dockExtent(device: DeviceConfiguration): Flow<Int> {
        val base = requireNotNull(GridSlot.HOME_DOCK.blueprint.extentDp) {
            "the dock must declare a default extent; its rows and columns are divided out of it"
        }
        return dataStore.read(SurfaceMetricsSlice) { it.dockExtent(device, base) }
    }

    // Same `requireNotNull` as the dock's, and the same meaning: a grid with no row height in its blueprint takes its
    // rows' height from something already chosen — an extent divided, or a width derived — so there is nothing here
    // to answer with and a default would be a number nobody configured.
    override fun listRowHeight(device: DeviceConfiguration): Flow<Int> {
        val base = requireNotNull(GridSlot.APPS_LIST.blueprint.rowHeightDp) {
            "the list must declare a default row height; being one lane, it can derive none"
        }
        return dataStore.read(SurfaceMetricsSlice) { it.listRowHeight(device, base) }
    }

    override suspend fun setListRowHeight(device: DeviceConfiguration, dp: Int?) {
        require(dp == null || dp > 0) { "a $dp dp row could not hold an icon" }
        update(SurfaceMetricsSlice) { withListRowHeight(device, dp) }
    }

    // No `requireNotNull` here, unlike the two extents above: `horizontalPaddingDp` is not nullable on a blueprint,
    // because every grid has edges. It is the measurement that *does* apply to all eight.
    override fun horizontalPadding(slot: GridSlot, device: DeviceConfiguration): Flow<Int> {
        val base = slot.blueprint.horizontalPaddingDp
        return dataStore.read(SurfaceMetricsSlice) { it.horizontalPadding(slot, device, base) }
    }

    override suspend fun setHorizontalPadding(slot: GridSlot, device: DeviceConfiguration, dp: Int?) {
        // Clamped to the declared range rather than left to the caller, which is `updateGrid`'s treatment and not
        // `setDockExtent`'s. The difference is that both of a padding's bounds are static facts: zero is "no margin",
        // and the ceiling is a judgement about how much of a grid may be given away — neither needs a measured screen.
        val clamped = dp?.coerceIn(HorizontalPaddingRange.first, HorizontalPaddingRange.last)
        update(SurfaceMetricsSlice) { withHorizontalPadding(slot, device, clamped) }
    }

    override suspend fun setDockExtent(device: DeviceConfiguration, dp: Int?) {
        // The one bound this layer can state without measuring anything. Everything else about a dock's extent —
        // whether a line of icons fits it, whether it swallows the screen — needs the current icon sizing and the
        // current window, so it is checked where those are known rather than guessed at here.
        require(dp == null || dp > 0) { "a dock $dp dp thick could not hold a cell" }
        update(SurfaceMetricsSlice) { withDockExtent(device, dp) }
    }

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
