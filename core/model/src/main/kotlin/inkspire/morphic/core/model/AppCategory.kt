package inkspire.morphic.core.model

/**
 * Fine-grained classification taxonomy for apps.
 *
 * Each [AppCategory] represents a specific type of application and folds into a broader [CategoryGroup].
 * The [name] is used as a stable identifier for persistence.
 *
 * @property displayName The human-readable name of the category.
 * @property group The [CategoryGroup] this category belongs to.
 */
enum class AppCategory(val displayName: String, val group: CategoryGroup) {
    SOCIAL("Social", CategoryGroup.SOCIAL),
    COMMUNICATION("Communication", CategoryGroup.COMMUNICATION),
    IMAGE("Photography", CategoryGroup.MEDIA),
    MAPS("Navigation", CategoryGroup.UTILITIES),
    NEWS("News & Reading", CategoryGroup.INTERNET),
    AUDIO("Music & Audio", CategoryGroup.MEDIA),
    VIDEO("Video", CategoryGroup.MEDIA),
    GAME("Games", CategoryGroup.GAMES),
    PRODUCTIVITY("Productivity", CategoryGroup.UTILITIES),
    TOOLS("Tools", CategoryGroup.UTILITIES),
    BROWSER("Browsers", CategoryGroup.INTERNET),
    SHOPPING("Shopping", CategoryGroup.INTERNET),
    FINANCE("Finance", CategoryGroup.INTERNET),
    PERSONALIZATION("Personalization", CategoryGroup.UTILITIES),
    BUSINESS("Business", CategoryGroup.INTERNET),
    EDUCATION("Education", CategoryGroup.INTERNET),
    TRAVEL("Travel", CategoryGroup.INTERNET),
    LIFESTYLE("Lifestyle", CategoryGroup.INTERNET),
    HEALTH("Health & Fitness", CategoryGroup.UTILITIES),
    SPORTS("Sports", CategoryGroup.INTERNET),
    WEATHER("Weather", CategoryGroup.UTILITIES),
    MEDICAL("Medical", CategoryGroup.UTILITIES),
    ACCESSIBILITY("Accessibility", CategoryGroup.UTILITIES),
    OTHER("Other", CategoryGroup.UTILITIES),
}

/** Stable string id for persistence — the enum constant [name]. */
fun AppCategory.categoryId(): String = name

/** Converts to a display [Category] (id = enum name, name = [displayName], order = [ordinal]). */
fun AppCategory.toCategory(): Category = Category(id = name, name = displayName, order = ordinal)
