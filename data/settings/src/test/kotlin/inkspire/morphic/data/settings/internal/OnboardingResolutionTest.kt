package inkspire.morphic.data.settings.internal

import inkspire.morphic.data.settings.Onboarding
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
    fun `an install that stored a surface register before the flag existed counts as set up`() {
        assertEquals(Onboarding.Completed, slice.resolve(stored = null, surfaceRegisterStored = true))
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
}
