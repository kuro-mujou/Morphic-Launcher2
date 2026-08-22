package inkspire.morphic.data.apps.category

import inkspire.morphic.core.model.AppCategory
import inkspire.morphic.core.model.AppInfo

/** Where a token was found, and how much that is worth. A package name is written by a developer; a label by a
 *  marketer — so `com.foo.weather` is better evidence than "Foo — Your Daily Companion". */
private const val PACKAGE_MATCH = 3
private const val LABEL_MATCH = 2

/**
 * Tokens this short must match a word **exactly**; longer ones may match a word's prefix. Three-letter tokens are
 * initialisms (`gps`, `sms`, `rpg`, `nba`, `zip`) where a prefix match is nearly always an accident.
 */
private const val PREFIX_MIN_LENGTH = 4

/**
 * Last-resort guess for apps the curated map and the platform category both miss — which on a real device is most
 * of them, since few apps declare `android:appCategory`.
 *
 * **Words, not substrings.** The package and label are split into words (`com.foo.brain_trainer` →
 * `com, foo, brain, trainer`) and a token matches a *word*, not any position in the string. Raw `contains` reads
 * plausibly and misfiles constantly: `"line"` matches "air**line**s", `"class"` matches "**class**ic solitaire",
 * `"rain"` matches "b**rain** trainer", `"grab"` matches "screen**grab**", `"action"` matches "trans**action**".
 * Every one of those is a wrong category on a real device.
 *
 * A word matches a token when it *is* the token or **begins with** it, so `weatherapp` still matches `weather` —
 * package segments are routinely concatenated words, and demanding equality would lose more than substrings gained.
 * Short tokens are the exception (see [PREFIX_MIN_LENGTH]).
 *
 * **A rule scores its best single match, never a sum.** Summing across tokens rewards whichever rule has the
 * longest token list and double-counts redundant entries — `com.paypal.android` would score `pay` *and* `paypal`
 * for FINANCE — so a category's confidence would depend on how many synonyms someone happened to type. Best-match
 * makes a 6-token rule and a 30-token rule directly comparable.
 *
 * **Ties fall to declaration order**, which is why [RULES] is ordered by how distinctive its tokens are rather
 * than alphabetically: "Music Player" matches `music` (AUDIO) and `player` (VIDEO) equally, and AUDIO is declared
 * first because a thing called a music player is a music player.
 *
 * **Brands are not here.** `com.spotify.music` belongs in the curated map — a keyword rule naming a product is
 * curated data wearing a heuristic's clothes, and it would need a recompile to correct rather than an asset swap.
 * These rules are for apps nobody has curated.
 */
internal object CategoryHeuristics {

    /** The best-scoring category for [app], or null when no rule matches at all. */
    fun guess(app: AppInfo): AppCategory? {
        val packageWords = wordsOf(app.componentKey.packageName)
        val labelWords = wordsOf(app.label)
        return RULES
            .map { rule -> rule to rule.score(packageWords, labelWords) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?.category
    }

    /** Lowercased alphanumeric runs — the words of a package name or a label, separators discarded. */
    private fun wordsOf(text: String): List<String> =
        text.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }

    private val SEPARATORS = Regex("[^a-z0-9]+")

    /**
     * @param tokens matched against a word as an exact value or a prefix (see [matchesToken]).
     * @param compounds tokens that may *also* match as a **suffix**, because they are routinely glued to the end of
     *   a name: `vietinbank`, `techcombank`, `gmail`, `gpay`, `bookshop`. Opt-in per token rather than a blanket
     *   rule, because suffix matching is where `contains` went wrong — allowing it everywhere would let `rain` match
     *   "b**rain**" again. A token earns it only when English offers no common word ending that way.
     */
    private class Rule(
        val category: AppCategory,
        val tokens: List<String>,
        val compounds: List<String> = emptyList(),
    ) {

        /** [PACKAGE_MATCH] if any token matches a package word, else [LABEL_MATCH] for a label word, else 0. */
        fun score(packageWords: List<String>, labelWords: List<String>): Int = when {
            matches(packageWords) -> PACKAGE_MATCH
            matches(labelWords) -> LABEL_MATCH
            else -> 0
        }

        private fun matches(words: List<String>): Boolean = words.any { word ->
            tokens.any { token -> word.matchesToken(token) } || compounds.any { word.endsWith(it) }
        }
    }

    /** A word *is* the token, or begins with it — unless the token is short, where only equality counts. */
    private fun String.matchesToken(token: String): Boolean =
        if (token.length < PREFIX_MIN_LENGTH) this == token else startsWith(token)

    /**
     * Ordered by how distinctive the tokens are, because that order breaks ties (see the class KDoc). Tokens are
     * generic words only — anything naming a product belongs in `assets/app_categories.json`.
     */
    private val RULES = listOf(
        Rule(
            AppCategory.WEATHER,
            listOf("weather", "forecast", "radar", "climate", "storm", "humidity"),
        ),
        Rule(
            AppCategory.MEDICAL,
            listOf("medical", "medicine", "doctor", "hospital", "clinic", "pharmacy", "telehealth", "prescription"),
        ),
        Rule(
            AppCategory.HEALTH,
            listOf(
                "fitness", "health", "workout", "gym", "exercise", "running", "walking", "steps",
                "sleep", "calorie", "diet", "nutrition", "meditation", "mindfulness", "yoga",
            ),
        ),
        Rule(
            AppCategory.FINANCE,
            listOf(
                "bank", "banking", "wallet", "payment", "finance", "money", "cash", "credit", "debit",
                "loan", "mortgage", "insurance", "invest", "stocks", "broker", "trading", "crypto",
                "bitcoin", "ethereum",
            ),
            compounds = listOf("bank", "pay")
        ),
        Rule(
            AppCategory.MAPS,
            listOf("maps", "navigation", "gps", "route", "traffic", "compass", "offlinemaps"),
            compounds = listOf("maps")
        ),
        Rule(
            AppCategory.TRAVEL,
            listOf(
                "travel", "trip", "vacation", "flight", "airline", "hotel", "hostel", "booking",
                "itinerary", "boarding", "railway", "metro", "taxi",
            ),
        ),
        Rule(
            AppCategory.SHOPPING,
            listOf("shop", "shopping", "store", "market", "marketplace", "ecommerce", "coupon", "voucher", "retail"),
            compounds = listOf("shop")
        ),
        Rule(
            AppCategory.EDUCATION,
            listOf(
                "learn", "learning", "study", "education", "school", "college", "university",
                "course", "lesson", "classroom", "flashcard", "dictionary", "translate", "quiz",
            ),
        ),
        Rule(
            AppCategory.SPORTS,
            listOf(
                "sport", "sports", "football", "soccer", "basketball", "baseball", "cricket",
                "golf", "tennis", "nba", "nfl", "mlb", "fifa", "scoreboard", "league",
            ),
        ),
        Rule(
            AppCategory.COMMUNICATION,
            listOf(
                "messenger", "message", "messages", "chat", "sms", "mms", "dialer", "phone",
                "contacts", "email", "mail", "inbox", "voip", "call", "caller",
            ),
            compounds = listOf("mail", "chat")
        ),
        Rule(
            AppCategory.SOCIAL,
            listOf("social", "community", "friends", "followers", "dating", "forum"),
        ),
        Rule(
            AppCategory.IMAGE,
            listOf("camera", "photo", "photos", "gallery", "selfie", "collage", "photoeditor", "wallpapers"),
        ),
        Rule(
            AppCategory.AUDIO,
            listOf("music", "song", "songs", "audio", "podcast", "radio", "equalizer", "ringtone"),
        ),
        Rule(
            AppCategory.VIDEO,
            listOf("video", "movie", "movies", "cinema", "streaming", "player", "subtitle"),
        ),
        Rule(
            AppCategory.NEWS,
            listOf("news", "newspaper", "magazine", "headlines", "rss", "ebook", "ebooks", "audiobook", "comics"),
        ),
        Rule(
            AppCategory.BROWSER,
            listOf("browser", "incognito", "webview"),
        ),
        Rule(
            AppCategory.PRODUCTIVITY,
            listOf(
                "calendar", "notes", "notepad", "todo", "tasks", "office", "document", "documents",
                "spreadsheet", "slides", "scanner", "reminder", "checklist",
            ),
        ),
        Rule(
            AppCategory.PERSONALIZATION,
            listOf(
                "launcher",
                "theme",
                "themes",
                "wallpaper",
                "iconpack",
                "keyboard",
                "widget",
                "widgets",
                "lockscreen",
                "font"
            ),
        ),
        Rule(
            AppCategory.TOOLS,
            listOf(
                "calculator", "flashlight", "torch", "clock", "alarm", "stopwatch", "timer",
                "filemanager", "explorer", "cleaner", "compress", "unzip", "backup", "recorder", "vpn",
            ),
        ),
        Rule(
            AppCategory.GAME,
            listOf(
                "game", "games", "arcade", "puzzle", "casino", "solitaire", "sudoku", "racing",
                "shooter", "rpg", "moba", "sandbox", "tycoon",
            ),
        ),
    )
}
