package inkspire.morphic.core.model

/**
 * Fine-grained classification taxonomy for apps.
 *
 * Each [AppCategory] represents a specific type of application and folds into a broader [CategoryGroup].
 * The [name] is used as a stable identifier for persistence.
 *
 * **This taxonomy is how classification reasons; [CategoryGroup] is what gets displayed.** A curated table and the
 * keyword heuristics both answer in these terms — "this is an `AUDIO` app" — and [group] folds that answer into the
 * page it belongs on. The split is what lets a new fine category be added without adding a page, and it is why
 * several of these share a group: `BUSINESS` and `EDUCATION` are usefully different *classifications* and would be
 * two nearly-empty *pages*.
 *
 * @property displayName The human-readable name of the category.
 * @property group The [CategoryGroup] this category belongs to.
 */
enum class AppCategory(val displayName: String, val group: CategoryGroup) {
    SOCIAL("Social", CategoryGroup.SOCIAL),
    COMMUNICATION("Communication", CategoryGroup.COMMUNICATION),
    IMAGE("Photography", CategoryGroup.MEDIA),
    MAPS("Navigation", CategoryGroup.TRAVEL),
    NEWS("News & Reading", CategoryGroup.READING),
    AUDIO("Music & Audio", CategoryGroup.MEDIA),
    VIDEO("Video", CategoryGroup.MEDIA),
    GAME("Games", CategoryGroup.GAMES),
    PRODUCTIVITY("Productivity", CategoryGroup.PRODUCTIVITY),
    TOOLS("Tools", CategoryGroup.UTILITIES),
    BROWSER("Browsers", CategoryGroup.READING),
    SHOPPING("Shopping", CategoryGroup.SHOPPING),
    FINANCE("Finance", CategoryGroup.FINANCE),
    PERSONALIZATION("Personalization", CategoryGroup.UTILITIES),
    BUSINESS("Business", CategoryGroup.PRODUCTIVITY),
    EDUCATION("Education", CategoryGroup.PRODUCTIVITY),
    TRAVEL("Travel", CategoryGroup.TRAVEL),
    LIFESTYLE("Lifestyle", CategoryGroup.SHOPPING),
    HEALTH("Health & Fitness", CategoryGroup.HEALTH),
    SPORTS("Sports", CategoryGroup.READING),
    WEATHER("Weather", CategoryGroup.UTILITIES),
    MEDICAL("Medical", CategoryGroup.HEALTH),
    ACCESSIBILITY("Accessibility", CategoryGroup.UTILITIES),
    OTHER("Other", CategoryGroup.UTILITIES),
}

/** Stable string id for persistence — the enum constant [name]. */
fun AppCategory.categoryId(): String = name

/** Converts to a display [Category] (id = enum name, name = [displayName], order = [ordinal]). */
fun AppCategory.toCategory(): Category = Category(id = name, name = displayName, order = ordinal)
