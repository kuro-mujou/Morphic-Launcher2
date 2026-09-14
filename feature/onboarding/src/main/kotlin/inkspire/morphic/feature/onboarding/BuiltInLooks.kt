package inkspire.morphic.feature.onboarding

import android.content.Context
import inkspire.morphic.data.settings.Look

/**
 * The looks the first-run screen offers, read from this module's assets in the order they are offered.
 *
 * **Files, not code.** Each is a [Look] captured by `LookCaptureHarness` from a store configured through the settings
 * setters — the harness holds the recipe each was made with — so a file here is the store's own format and cannot drift
 * from the slices it writes the way a hand-kept description of an arrangement would.
 *
 * The summary is this screen's copy, a sentence saying what the arrangement is and how the apps are reached. It sits
 * beside the file name rather than inside the look, which carries no presentation.
 */
internal class BuiltInLooks(private val context: Context) {

    /**
     * Reads every offered look. Throws on a missing or unreadable file: a shipped look that does not parse is a build
     * fault.
     */
    fun load(): List<LookOption> = Offered.map { (file, summary) ->
        val json = context.assets.open("looks/$file.json").bufferedReader().use { it.readText() }
        val look = Look.parse(json)
        LookOption(id = file, title = look.name, look = look, summary = summary)
    }

    private companion object {
        /** File name under `assets/looks`, and the line said under the look's name. Placeholder names: plan question 1. */
        val Offered = listOf(
            "classic" to "Home screen pages with a dock. Swipe up for all your apps.",
            "library" to "Home screen pages with a dock. Swipe left for your apps by category.",
            "minimal" to "A list of apps on the home screen, with room for widgets. Swipe up for the rest.",
            "index" to "Home screen pages with a dock. Swipe up for every app, A to Z.",
        )
    }
}

/** One look the first-run screen offers: where it came from, what its row is called, the look itself, and its summary. */
internal data class LookOption(val id: String, val title: String, val look: Look, val summary: String)
