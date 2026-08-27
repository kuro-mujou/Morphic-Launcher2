package inkspire.morphic.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored form of an [IconArrangement], pinned — because the value is written to a user's database as JSON and
 * nothing else can tell them it moved.
 *
 * **The failure this exists for is silent both ways.** A container whose blob cannot be read falls back to
 * [IconArrangement.Grid] rather than taking a surface down with it (see `IconArrangementConverter`), so a broken
 * discriminator presents as "my containers reset themselves" rather than as a crash — and a `@SerialName` dropped
 * during a refactor breaks exactly that way, having compiled cleanly.
 */
class IconArrangementSerializationTest {

    private val json = Json

    private val all = listOf<IconArrangement>(
        IconArrangement.Grid(),
        IconArrangement.Grid(GridFill.Columns(3)),
        IconArrangement.Grid(GridFill.Rows(1)),
        IconArrangement.Circle,
        IconArrangement.Beehive,
        IconArrangement.Fan(FanAnchor.TOP_LEFT),
        IconArrangement.Fan(FanAnchor.TOP_RIGHT),
        IconArrangement.Fan(FanAnchor.BOTTOM_LEFT),
        IconArrangement.Fan(FanAnchor.BOTTOM_RIGHT),
    )

    @Test
    fun `every arrangement survives the round trip`() {
        for (arrangement in all) {
            val encoded = json.encodeToString(IconArrangement.serializer(), arrangement)
            assertEquals(encoded, arrangement, json.decodeFromString(IconArrangement.serializer(), encoded))
        }
    }

    /**
     * The discriminator is the short name and not the class's, which is what stops a subtype being moved or renamed
     * from orphaning every container stored under it.
     */
    @Test
    fun `each shape is stored under its own short name`() {
        val names = mapOf(
            IconArrangement.Grid() to "grid",
            IconArrangement.Circle to "circle",
            IconArrangement.Beehive to "beehive",
            IconArrangement.Fan(FanAnchor.TOP_LEFT) to "fan",
        )
        for ((arrangement, name) in names) {
            val encoded = json.encodeToString(IconArrangement.serializer(), arrangement)
            assertTrue("$arrangement stored as $encoded", encoded.contains("\"type\":\"$name\""))
        }
    }

    /**
     * **The blob written before the grid had a parameter still decodes**, which is the claim slice H made when it
     * declared `Grid` with none: a default is not encoded, so the object form and the defaulted class form are the
     * same bytes. If this ever fails, every container stored by an older build silently resets its fill.
     */
    @Test
    fun `a grid stored without a fill decodes as auto`() {
        val decoded = json.decodeFromString(IconArrangement.serializer(), """{"type":"grid"}""")
        assertEquals(IconArrangement.Grid(GridFill.Auto), decoded)
    }

    /** A pinned axis travels in the blob, count and all — [GridFill] is a shape of its own down there. */
    @Test
    fun `a grid keeps the axis it was pinned to`() {
        val pinned = IconArrangement.Grid(GridFill.Rows(1))
        val encoded = json.encodeToString(IconArrangement.serializer(), pinned)
        assertTrue(encoded, encoded.contains("\"type\":\"rows\"") && encoded.contains("\"count\":1"))
        assertEquals(pinned, json.decodeFromString(IconArrangement.serializer(), encoded))
    }

    /** A fan's corner travels with the fan, which is the whole of what carrying a parameter has to mean. */
    @Test
    fun `a fan keeps the corner it was stored with`() {
        val encoded = json.encodeToString(
            IconArrangement.serializer(),
            IconArrangement.Fan(FanAnchor.BOTTOM_RIGHT),
        )
        val decoded = json.decodeFromString(IconArrangement.serializer(), encoded)
        assertEquals(IconArrangement.Fan(FanAnchor.BOTTOM_RIGHT), decoded)
    }
}
