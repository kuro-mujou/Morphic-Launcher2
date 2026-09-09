package inkspire.morphic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards on the one bridge between a window and its stored layout.
 *
 * Every assertion here is about something that fails **silently**: a key resolved for the wrong mode does not throw,
 * it writes a correct-looking arrangement into rows nobody is drawing, and the user sees a layout that will not stay
 * where they put it.
 */
class ArrangementKeyTest {

    @Test
    fun `a configuration resolves to its own mode's key`() {
        assertEquals(ArrangementKey.PHONE_PORTRAIT, DeviceConfiguration.PHONE_PORTRAIT.arrangementKey(true))
        assertEquals(ArrangementKey.PHONE_PORTRAIT_LINKED, DeviceConfiguration.PHONE_PORTRAIT.arrangementKey(false))
        assertEquals(ArrangementKey.TABLET_LANDSCAPE, DeviceConfiguration.TABLET_LANDSCAPE.arrangementKey(true))
        assertEquals(
            ArrangementKey.TABLET_LANDSCAPE_LINKED,
            DeviceConfiguration.TABLET_LANDSCAPE.arrangementKey(false),
        )
    }

    @Test
    fun `the two modes never resolve to the same key`() {
        DeviceConfiguration.entries.forEach { configuration ->
            assertTrue(
                "$configuration resolves to one key in both modes",
                configuration.arrangementKey(independentLayout = true) !=
                    configuration.arrangementKey(independentLayout = false),
            )
        }
    }

    @Test
    fun `every key is reachable, so no arrangement is storage nothing writes`() {
        val resolved = DeviceConfiguration.entries.flatMap {
            listOf(it.arrangementKey(true), it.arrangementKey(false))
        }
        assertEquals(ArrangementKey.entries.toSet(), resolved.toSet())
    }

    /**
     * The leak the mode axis exists to close: a linked landscape re-derives from — and writes back into — whatever
     * this returns, so answering with an independent key would rebuild the shared layout out of one the user is
     * arranging separately, and overwrite that one in the bargain.
     */
    @Test
    fun `the reference of a key is always in that key's own mode`() {
        ArrangementKey.entries.forEach { key ->
            assertEquals("$key crosses modes to reach its reference", key.isLinked, key.portraitOfPair.isLinked)
        }
    }

    @Test
    fun `a portrait key is its own reference`() {
        assertEquals(ArrangementKey.PHONE_PORTRAIT, ArrangementKey.PHONE_PORTRAIT.portraitOfPair)
        assertEquals(ArrangementKey.PHONE_PORTRAIT_LINKED, ArrangementKey.PHONE_PORTRAIT_LINKED.portraitOfPair)
    }

    @Test
    fun `a reference never crosses form factors`() {
        ArrangementKey.entries.forEach { key ->
            assertEquals(
                "$key crosses form factors to reach its reference",
                key.name.startsWith("PHONE"),
                key.portraitOfPair.name.startsWith("PHONE"),
            )
        }
    }

    /** One direction only: the linked pair is where a launcher starts, so it is never the thing being seeded. */
    @Test
    fun `only an independent key has a linked counterpart, and it is the same posture`() {
        ArrangementKey.entries.forEach { key ->
            if (key.isLinked) {
                assertNull("$key is linked and should have no counterpart to seed from", key.linkedCounterpart)
            } else {
                val counterpart = key.linkedCounterpart
                assertNotNull("$key has nothing to seed from", counterpart)
                assertTrue("$key seeds from an independent key", counterpart!!.isLinked)
                assertEquals("$key seeds from a different posture", "${key.name}_LINKED", counterpart.name)
            }
        }
    }
}
