package inkspire.morphic.data.setup.internal

import inkspire.morphic.data.settings.Onboarding
import inkspire.morphic.data.settings.SetupStep

/**
 * What each step's owner currently says, reduced to the one question the hub asks of it: is it done?
 *
 * @property defaultLauncherAskable true while there is a way to ask for the home role and the launcher does not hold it.
 * @property wallpaperChosen true once a wallpaper has been chosen through the launcher.
 * @property iconsStyled true once the global icon recipe is anything but the plain default.
 * @property widgetPlaced true once any widget is bound to a placement or a container.
 */
internal data class SetupFacts(
    val defaultLauncherAskable: Boolean,
    val wallpaperChosen: Boolean,
    val iconsStyled: Boolean,
    val widgetPlaced: Boolean,
)

/**
 * **The one rule deciding which steps the hub shows**: every step not done and not put away, in [SetupStep] order.
 *
 * Pure, so the rule is tested without the four stores it reads.
 */
internal fun pendingSetupSteps(facts: SetupFacts, onboarding: Onboarding): List<SetupStep> =
    SetupStep.entries.filter { step ->
        val done = when (step) {
            SetupStep.DEFAULT_LAUNCHER -> !facts.defaultLauncherAskable
            SetupStep.WALLPAPER -> facts.wallpaperChosen
            SetupStep.ICON_STYLE -> facts.iconsStyled
            SetupStep.FIRST_WIDGET -> facts.widgetPlaced
        }
        !done && !onboarding.isDismissed(step)
    }
