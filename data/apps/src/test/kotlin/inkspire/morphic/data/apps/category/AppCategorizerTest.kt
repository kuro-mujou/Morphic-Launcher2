package inkspire.morphic.data.apps.category

import android.content.pm.ApplicationInfo
import inkspire.morphic.core.model.AppCategory
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.CategoryGroup
import inkspire.morphic.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior spec for [AppCategorizer] — the first-run guess at where an app belongs.
 *
 * The interesting part is the **priority chain**, because each step is a weaker guess than the one before and the
 * order is the whole design: a curated fact beats a platform declaration beats a keyword hunch. These pin that
 * order, plus the two fallbacks a user actually notices (the Utilities / System split).
 *
 * No Android needed: [CategoryMapping] is a `fun interface`, so the curated half is supplied inline instead of
 * standing up an asset — which is why it is an injected interface rather than a file read inside the categorizer.
 */
class AppCategorizerTest {

    private fun app(
        packageName: String,
        label: String = "App",
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
        isSystem: Boolean = false,
    ) = AppInfo(
        componentKey = ComponentKey(packageName, "Main"),
        label = label,
        isWorkProfile = false,
        isSuspended = false,
        isSystem = isSystem,
        category = category,
    )

    private fun categorizer(vararg curated: Pair<String, String>): AppCategorizer {
        val map = curated.toMap()
        return AppCategorizer { packageName -> map[packageName] }
    }

    // ── Priority: curated → platform → heuristic → fallback ──

    @Test
    fun `the curated map wins over a platform category`() {
        // The platform says game; the curated table says this specific package is social. Curation is a fact
        // someone checked, so it outranks a self-declaration.
        val subject = categorizer("com.example.thing" to AppCategory.SOCIAL.name)
        val info = app("com.example.thing", category = ApplicationInfo.CATEGORY_GAME)
        assertEquals(AppCategory.SOCIAL, subject.fineCategoryOf(info))
    }

    @Test
    fun `a platform category wins over a keyword guess`() {
        // "weather" in the package would guess Weather; the app declares itself a game, which is a stronger signal
        // than a substring.
        val info = app("com.example.weatherwars", category = ApplicationInfo.CATEGORY_GAME)
        assertEquals(AppCategory.GAME, categorizer().fineCategoryOf(info))
    }

    @Test
    fun `the keyword guess applies when the platform declares nothing`() {
        // Which is the common case: very few apps set ApplicationInfo.category at all.
        assertEquals(AppCategory.WEATHER, categorizer().fineCategoryOf(app("com.example.weather")))
    }

    @Test
    fun `the label is searched as well as the package name`() {
        assertEquals(AppCategory.AUDIO, categorizer().fineCategoryOf(app("com.acme.xyz", label = "Podcast Player")))
    }

    @Test
    fun `a curated id that is not in the taxonomy is ignored rather than trusted`() {
        // Guards the data, which is hand-maintained: a typo'd heading must not become a category id.
        val subject = categorizer("com.example.weather" to "NOT_A_CATEGORY")
        assertEquals(AppCategory.WEATHER, subject.fineCategoryOf(app("com.example.weather")))
    }

    @Test
    fun `nothing matching is OTHER`() {
        assertEquals(AppCategory.OTHER, categorizer().fineCategoryOf(app("com.acme.zzz", label = "Zzz")))
    }

    // ── Words, not substrings: every one of these misfiled on a `contains` match ──

    @Test
    fun `an airline is not a messaging app`() {
        // "line" appears inside "airlines".
        assertEquals(AppCategory.TRAVEL, categorizer().fineCategoryOf(app("com.aa.android", "American Airlines")))
    }

    @Test
    fun `a solitaire game is not an education app`() {
        // "class" appears inside "classic".
        assertEquals(AppCategory.GAME, categorizer().fineCategoryOf(app("com.foo.classicsolitaire", "Classic Solitaire")))
    }

    @Test
    fun `a brain trainer is not a weather app`() {
        // "rain" appears inside "brain". Nothing here classifies it, and OTHER is the honest answer.
        assertEquals(AppCategory.OTHER, categorizer().fineCategoryOf(app("com.foo.braintrainer", "Brain Trainer")))
    }

    @Test
    fun `a screenshot tool is not a travel app`() {
        // "grab" appears inside "screengrab".
        assertEquals(AppCategory.OTHER, categorizer().fineCategoryOf(app("com.foo.screengrab", "Screen Grab")))
    }

    @Test
    fun `a transaction history is not a game`() {
        // "action" appears inside "transaction"; "bank" in the package is the real signal.
        assertEquals(AppCategory.FINANCE, categorizer().fineCategoryOf(app("com.bank.app", "Transaction History")))
    }

    @Test
    fun `a word may still match a token as a prefix`() {
        // The concatenated-package case that plain word equality would lose: "weatherapp" starts with "weather".
        assertEquals(AppCategory.WEATHER, categorizer().fineCategoryOf(app("com.example.weatherapp")))
    }

    @Test
    fun `a short token must match a whole word`() {
        // "gps" is an initialism: it should hit a GPS logger and not "gpsomething".
        assertEquals(AppCategory.MAPS, categorizer().fineCategoryOf(app("com.example.gps.logger")))
        assertEquals(AppCategory.OTHER, categorizer().fineCategoryOf(app("com.example.gpsomethingelse")))
    }

    @Test
    fun `a compound token also matches as a suffix`() {
        // How bank apps are actually named — the token is glued to the end, where a prefix match can't see it.
        listOf("com.vietinbank.ipay", "com.techcombank.bb.app", "com.acme.mybank").forEach { packageName ->
            assertEquals(packageName, AppCategory.FINANCE, categorizer().fineCategoryOf(app(packageName)))
        }
        assertEquals(AppCategory.COMMUNICATION, categorizer().fineCategoryOf(app("com.google.android.gmail")))
    }

    @Test
    fun `suffix matching stays opt-in, so a common word ending is not a category`() {
        // The whole reason `compounds` is per-token rather than blanket: "brain" ends with "rain", and a blanket
        // suffix rule would file a brain trainer under Weather — the exact class of bug this replaced.
        assertEquals(AppCategory.OTHER, categorizer().fineCategoryOf(app("com.foo.brain", "Brain")))
    }

    // ── Scoring: where a token was found decides, and a long token list is not an advantage ──

    @Test
    fun `a package match outranks a label match in another category`() {
        // The label says "photo" (Image), the package says weather. The package is the developer's word.
        val info = app("com.example.weather", label = "Photo of the Sky")
        assertEquals(AppCategory.WEATHER, categorizer().fineCategoryOf(info))
    }

    @Test
    fun `a rule with many tokens does not beat one with few`() {
        // COMMUNICATION carries far more tokens than WEATHER. Scoring each rule by its *best* match rather than
        // the sum is what keeps that from tipping the result; summing would also double-count synonyms.
        val info = app("com.example.weather", label = "Weather Forecast Radar Storm")
        assertEquals(AppCategory.WEATHER, categorizer().fineCategoryOf(info))
    }

    // ── The id that gets persisted: a group, and never "Other" ──

    @Test
    fun `the id is the broad group, not the fine category`() {
        // Podcast → AUDIO → Media. Pages are groups; the fine taxonomy is only how the guess is reasoned.
        val info = app("com.acme.xyz", label = "Podcast Player")
        assertEquals(CategoryGroup.MEDIA.name, categorizer().categoryIdOf(info))
    }

    @Test
    fun `an unclassifiable user app lands in Utilities`() {
        assertEquals(CategoryGroup.UTILITIES.name, categorizer().categoryIdOf(app("com.acme.zzz", label = "Zzz")))
    }

    @Test
    fun `an unclassifiable system app lands in System`() {
        // The split that keeps the leftovers page useful: system components are most of the unclassifiable pile,
        // and mixing them into the user's own leftovers ruins the one page they would actually scan.
        val info = app("com.android.zzz", label = "Zzz", isSystem = true)
        assertEquals(CategoryGroup.SYSTEM.name, categorizer().categoryIdOf(info))
    }

    @Test
    fun `a system app that classifies keeps its real group`() {
        // Being a system app is only a fallback, not an override — the stock camera belongs under Media.
        val info = app("com.android.camera", label = "Camera", isSystem = true)
        assertEquals(CategoryGroup.MEDIA.name, categorizer().categoryIdOf(info))
    }

    @Test
    fun `every platform category maps to something`() {
        // If the platform declares a category we understand, it must never fall through to a keyword guess.
        val platform = listOf(
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_ACCESSIBILITY,
        )
        platform.forEach { value ->
            val fine = categorizer().fineCategoryOf(app("com.acme.zzz", label = "Zzz", category = value))
            assertEquals("platform category $value should classify", true, fine != AppCategory.OTHER)
        }
    }
}
