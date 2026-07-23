package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.ComponentKey

/**
 * Per-app icon customisation, keyed by [component]. Every field is nullable: a null means "inherit the global
 * icon style for this property", so a row only stores the properties the user actually overrode.
 */
@Entity(tableName = "icon_override")
data class IconOverrideEntity(
    @PrimaryKey val component: ComponentKey,
    val iconPackPackage: String? = null,
    val drawableName: String? = null,
    val customImagePath: String? = null,
    val shapeChoice: String? = null,
    val shapeParam: Float? = null,
    val backgroundChoice: String? = null,
    val backgroundArgb: Int? = null,
    val foregroundScale: Float? = null,
    val legacyScale: Float? = null,
    val normalize: Boolean? = null,
    val monochrome: Boolean? = null,
    val tintArgb: Int? = null,
    val foregroundShaped: Boolean? = null,
    val foregroundUniform: Boolean? = null,
    val iconSizes: String? = null,
    val offsetX: Float? = null,
    val offsetY: Float? = null,
    val zoom: Float? = null,
    val layerSet: String? = null,
)
