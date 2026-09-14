package inkspire.morphic.data.setup.internal

import android.content.Intent
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.data.apps.DefaultLauncherRole
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SetupStep
import inkspire.morphic.data.setup.SetupSteps
import inkspire.morphic.data.wallpaper.WallpaperRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Default [SetupSteps]: four owners' answers, combined through [pendingSetupSteps].
 *
 * **A single instance**, so the home-role answer one reader refreshes is the answer the other reads — the settings hub
 * and HOME's menu cannot disagree about whether that step is still open.
 *
 * `internal` so only Koin constructs it; consumers depend on [SetupSteps].
 */
internal class SetupStepsImpl(
    private val settingsRepository: SettingsRepository,
    private val defaultLauncherRole: DefaultLauncherRole,
    wallpaperRepository: WallpaperRepository,
    layoutRepository: LayoutRepository,
    private val dispatchers: AppDispatchers,
) : SetupSteps {

    private val request = MutableStateFlow<Intent?>(null)

    override val defaultLauncherRequest: StateFlow<Intent?> = request.asStateFlow()

    override val pending: Flow<List<SetupStep>> = combine(
        settingsRepository.onboarding,
        request,
        wallpaperRepository.wallpaper,
        settingsRepository.iconAppearance,
        layoutRepository.widgets(),
    ) { onboarding, askable, wallpaper, icons, widgets ->
        pendingSetupSteps(
            facts = SetupFacts(
                defaultLauncherAskable = askable != null,
                wallpaperChosen = wallpaper.image != null,
                iconsStyled = icons != IconAppearance.Base,
                widgetPlaced = widgets.isNotEmpty(),
            ),
            onboarding = onboarding,
        )
    }.distinctUntilChanged()

    // Off the main thread: both halves of the role check are binder calls into the package manager.
    override suspend fun refresh() {
        request.value = withContext(dispatchers.io) {
            if (defaultLauncherRole.isDefault()) null else defaultLauncherRole.requestIntent()
        }
    }

    override suspend fun dismiss(step: SetupStep) {
        require(step.dismissible) { "$step cannot be put away" }
        settingsRepository.dismissSetupStep(step)
    }
}
