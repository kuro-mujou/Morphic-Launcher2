package inkspire.morphic.core.model

/**
 * Broad, top-level category groups for organizing applications.
 *
 * Every [AppCategory] maps to one of these groups. These are typically the
 * primary categories shown in the app drawer.
 *
 * @property displayName The human-readable name of the group.
 */
enum class CategoryGroup(val displayName: String) {
    COMMUNICATION("Communication"),
    SOCIAL("Social"),
    INTERNET("Internet"),
    MEDIA("Media"),
    GAMES("Games"),
    UTILITIES("Utilities"),
    SYSTEM("System"),
}

/** Stable string id for persistence — the enum constant [name]. */
fun CategoryGroup.categoryId(): String = name

/** Converts to a display [Category] (id = enum name, name = [displayName], order = [ordinal]). */
fun CategoryGroup.toCategory(): Category = Category(id = name, name = displayName, order = ordinal)

/** True for a built-in group id (a category the user may rename but not create or delete). */
fun isBuiltInCategoryId(id: String): Boolean = CategoryGroup.entries.any { it.name == id }