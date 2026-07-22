package inkspire.morphic.core.model

/**
 * Represents a displayable category in the launcher.
 *
 * A [Category] can be derived from either a [CategoryGroup] or an [AppCategory].
 * It contains the necessary information for rendering category-based UI elements,
 * such as drawer tabs.
 *
 * @property id Unique identifier for the category.
 * @property name Human-readable name displayed in the UI.
 * @property order Position of the category in a sorted list.
 */
data class Category(
    val id: String,
    val name: String,
    val order: Int,
)
