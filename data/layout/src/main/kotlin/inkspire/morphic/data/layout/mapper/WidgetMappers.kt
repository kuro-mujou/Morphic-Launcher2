package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.WidgetEntity
import inkspire.morphic.core.model.widget.Widget
import inkspire.morphic.core.model.widget.WidgetRecipe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * How a recipe is stored: defaults left out, and a key this build does not know dropped rather than failing — so a
 * recipe written by a newer build still reads here, minus whatever it added.
 */
private val RecipeJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

/**
 * The widget this row holds, or null when its recipe cannot be read — a layer kind from a newer build is the case
 * `ignoreUnknownKeys` cannot cover. Null makes it absent from HOME rather than failing every widget with it; the row
 * itself is kept, so a build that can read it again gets it back.
 */
internal fun WidgetEntity.toWidget(): Widget? = try {
    Widget(id, RecipeJson.decodeFromString<WidgetRecipe>(recipe))
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** A new row for [this] recipe; Room assigns the id. */
internal fun WidgetRecipe.toEntity(): WidgetEntity = WidgetEntity(recipe = RecipeJson.encodeToString(this))
