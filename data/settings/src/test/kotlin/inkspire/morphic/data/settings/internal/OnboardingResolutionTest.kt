package inkspire.morphic.data.settings.internal

import inkspire.morphic.data.settings.Onboarding
import inkspire.morphic.data.settings.SetupStep
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** How the onboarding flag is read — above all, what an absent one means. */
class OnboardingResolutionTest {

    private val slice = SettingsSlice(
        name = "onboarding",
        serializer = serializer<Onboarding>(),
        default = Onboarding.Default,
    )

    @Test
    fun `a fresh install, with nothing stored, is not set up`() {
        assertEquals(Onboarding.Default, slice.resolve(stored = null, surfaceRegisterStored = false))
    }

    @Test
    fun `an install that stored a surface register before the flag existed is set up, with no hint to show`() {
        assertEquals(Onboarding.Legacy, slice.resolve(stored = null, surfaceRegisterStored = true))
    }

    @Test
    fun `a stored unfinished flag re-opens the picker even over a stored register`() {
        val startedOver = slice.encode(Onboarding(completed = false))

        assertEquals(Onboarding.Default, slice.resolve(stored = startedOver, surfaceRegisterStored = true))
    }

    @Test
    fun `a stored finished flag is read as finished`() {
        val finished = slice.encode(Onboarding.Completed)

        assertEquals(Onboarding.Completed, slice.resolve(stored = finished, surfaceRegisterStored = false))
    }

    @Test
    fun `a flag stored before the hint existed still has the hint to come`() {
        val beforeTheHint = """{"completed":true}"""

        assertEquals(Onboarding.Completed, slice.resolve(stored = beforeTheHint, surfaceRegisterStored = true))
    }

    @Test
    fun `a dismissed setup step survives a round trip, and a step this build does not know leaves setup finished`() {
        val stored = """{"completed":true,"edgeHintDismissed":true,"dismissedSetupSteps":["WALLPAPER","RETIRED_STEP"]}"""

        val read = slice.resolve(stored = stored, surfaceRegisterStored = true)

        assertEquals(true, read.completed)
        assertEquals(true, read.isDismissed(SetupStep.WALLPAPER))
        assertEquals(false, read.isDismissed(SetupStep.ICON_STYLE))
    }

    @Test
    fun `the default-launcher step is never dismissed, whatever is stored`() {
        val stored = """{"completed":true,"dismissedSetupSteps":["DEFAULT_LAUNCHER"]}"""

        val read = slice.resolve(stored = stored, surfaceRegisterStored = true)

        assertEquals(false, read.isDismissed(SetupStep.DEFAULT_LAUNCHER))
    }

    @Test
    fun `a dismissed hint survives a round trip`() {
        val dismissed = Onboarding(completed = true, edgeHintDismissed = true)

        assertEquals(dismissed, slice.resolve(stored = slice.encode(dismissed), surfaceRegisterStored = true))
    }
}
