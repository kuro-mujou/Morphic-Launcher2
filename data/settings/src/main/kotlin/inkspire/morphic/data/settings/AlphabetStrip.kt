package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AlphabetStripStyle
import kotlinx.serialization.Serializable

/**
 * The **A–Z index strip**: whether it is drawn, and which of its two looks it wears.
 *
 * **A slice of its own rather than a field on [AppsChrome], though today only APPS draws one.** Chrome is what a
 * *surface* draws around its grid, and this is not that: the strip belongs to **A–Z-ordered content**, which is a
 * property two surfaces can have. APPS' derived list and grid have it now; HOME's vertical list is the next one, and
 * a strip setting living in `AppsChrome` would either have to be read by HOME from a record named for another surface
 * or be renamed — and a rename is a setting the user loses.
 *
 * **One switch for both layouts, not a map keyed by layout.** `AppsChrome` states the rule that decides between the
 * two shapes, and this is the other answer to it: what the strip attaches to is the ordering, so a user who wants it
 * on the list and not the grid is asking for two answers to one question. The strip appears wherever the content is
 * A–Z, and nowhere else — the pager and the two category layouts are arranged by hand, where an alphabet indexes
 * nothing.
 *
 * @property enabled off by default, which is the same reasoning search's `Hidden` default has: a launcher's first
 *   run should not have chrome on it that nobody asked for.
 * @property style see [AlphabetStripStyle] — a look, not a behavior.
 */
@Serializable
data class AlphabetStrip(
    val enabled: Boolean = false,
    val style: AlphabetStripStyle = AlphabetStripStyle.STANDARD,
) {
    companion object {
        /** No strip, standard look — the state a launcher that has never been configured is in. */
        val Default = AlphabetStrip()
    }
}
