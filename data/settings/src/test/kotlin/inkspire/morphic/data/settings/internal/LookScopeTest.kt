package inkspire.morphic.data.settings.internal

import org.junit.Assert.assertEquals
import org.junit.Test

/** That every settings slice has been decided for looks, exactly once. */
class LookScopeTest {

    @Test
    fun `every slice is either carried or excluded`() {
        assertEquals(SettingsSlices.keys, LookScope.carried + LookScope.excluded)
    }

    @Test
    fun `no slice is both carried and excluded`() {
        assertEquals(emptySet<String>(), LookScope.carried intersect LookScope.excluded)
    }
}
