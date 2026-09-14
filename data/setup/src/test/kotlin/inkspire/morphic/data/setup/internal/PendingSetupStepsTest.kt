package inkspire.morphic.data.setup.internal

import inkspire.morphic.data.settings.Onboarding
import inkspire.morphic.data.settings.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which steps the setup hub shows. */
class PendingSetupStepsTest {

    private val nothingDone = SetupFacts(
        defaultLauncherAskable = true,
        wallpaperChosen = false,
        iconsStyled = false,
        widgetPlaced = false,
    )

    @Test
    fun `with nothing done, every step shows, in order`() {
        assertEquals(SetupStep.entries, pendingSetupSteps(nothingDone, Onboarding.Completed))
    }

    @Test
    fun `a step its owner says is done does not show`() {
        val facts = nothingDone.copy(wallpaperChosen = true, widgetPlaced = true)

        assertEquals(listOf(SetupStep.DEFAULT_LAUNCHER, SetupStep.ICON_STYLE), pendingSetupSteps(facts, Onboarding.Completed))
    }

    @Test
    fun `the default-launcher step is done when there is nothing to ask`() {
        val facts = nothingDone.copy(defaultLauncherAskable = false)

        assertEquals(false, SetupStep.DEFAULT_LAUNCHER in pendingSetupSteps(facts, Onboarding.Completed))
    }

    @Test
    fun `a dismissed step does not show, but the default-launcher step cannot be dismissed`() {
        val dismissedEverything = Onboarding.Completed.copy(dismissedSetupSteps = SetupStep.entries.map { it.name }.toSet())

        assertEquals(listOf(SetupStep.DEFAULT_LAUNCHER), pendingSetupSteps(nothingDone, dismissedEverything))
    }

    @Test
    fun `with everything done, the hub is empty`() {
        val facts = SetupFacts(defaultLauncherAskable = false, wallpaperChosen = true, iconsStyled = true, widgetPlaced = true)

        assertEquals(emptyList<SetupStep>(), pendingSetupSteps(facts, Onboarding.Completed))
    }
}
