package inkspire.morphic.data.apps.category

import android.content.Context
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * The real [CategoryMapping]: a curated `packageName → fine category id` table, read from the bundled
 * `assets/app_categories.json` and overridable at runtime by a `filesDir/app_categories.json` that a future
 * updater may download (present ⇒ it *replaces* the bundled one wholesale, rather than merging).
 *
 * The JSON is grouped by category id because that is the shape a human maintains — one heading, the packages under
 * it — and flattened on read to the shape lookups need:
 *
 * ```json
 * { "SOCIAL": ["com.facebook.katana"], "AUDIO": ["com.spotify.music"] }
 * ```
 *
 * **Loaded lazily and cached for the process.** It is read once on the first classification (first run, or the
 * first time a category layout is opened) and never again — the file cannot change under a running launcher
 * except via that override, which needs a restart to matter anyway.
 *
 * **A parse failure degrades rather than crashes**, and is logged. The override path is the reason: that file
 * arrives over a network from outside the app, so malformed content there must not take the launcher down. The
 * bundled asset failing is a build mistake instead, which is why it is worth a log — the symptom otherwise is
 * "every app is in Utilities", which looks like a categorizer bug and is not one.
 */
class AssetCategoryMapping(private val context: Context) : CategoryMapping {

    private val map: Map<String, String> by lazy { load() }

    override fun categoryId(packageName: String): String? = map[packageName]

    private fun load(): Map<String, String> = runCatching {
        val json = downloadedOverride() ?: context.assets.open(ASSET).use { it.readBytes().decodeToString() }
        parse(json)
    }.onFailure { Timber.w(it, "Could not read $ASSET; falling back to platform categories + heuristics") }
        .getOrDefault(emptyMap())

    private fun downloadedOverride(): String? =
        File(context.filesDir, ASSET).takeIf { it.isFile }?.readText()

    /** Flattens `{ id: [packages] }` to `package → id`, skipping anything malformed rather than failing the lot. */
    private fun parse(json: String): Map<String, String> {
        val root = JSONObject(json)
        val result = HashMap<String, String>()
        root.keys().forEach { categoryId ->
            val packages = root.optJSONArray(categoryId) ?: return@forEach
            for (index in 0 until packages.length()) {
                val packageName = packages.optString(index).takeIf { it.isNotBlank() } ?: continue
                result[packageName] = categoryId
            }
        }
        return result
    }

    private companion object {
        const val ASSET = "app_categories.json"
    }
}
