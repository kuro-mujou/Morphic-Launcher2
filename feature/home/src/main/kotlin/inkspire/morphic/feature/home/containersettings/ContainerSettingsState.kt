package inkspire.morphic.feature.home.containersettings

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.WidgetContainerAxis
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.feature.home.ContainerIcon

/**
 * What a container's settings screen shows, **resolved** — its contents drawn rather than referenced, and whichever
 * settings its kind actually has.
 *
 * A sum type for `HomeMainSizing`'s reason, one layer over: the two containers do not configure the same thing
 * differently, they configure *different things*. An icon container has an arrangement and no axis; a widget
 * container has an axis and two behaviors and no arrangement. Neither could supply the other's value, which is
 * exactly what a sealed type says and what one class with four nullable fields would not.
 */
sealed interface ContainerSettings {

    /**
     * An icon container's: the [icons] it holds in container order, and the [arrangement] they are laid out by.
     *
     * [icons] is the same resolved type the cell draws from, so the row list and the container itself cannot
     * disagree about what is in it.
     */
    data class Icon(
        val icons: List<ContainerIcon>,
        val arrangement: IconArrangement,
    ) : ContainerSettings

    /**
     * A widget container's: the [widgets] it pages between, and its three settings.
     *
     * The three are held together because they are written together — see `LayoutChange.SetWidgetContainerOptions`
     * for why that is one op rather than three.
     */
    data class Widget(
        val widgets: List<WidgetInfo>,
        val axis: WidgetContainerAxis,
        val autoRotate: Boolean,
        val resetOnReturn: Boolean,
    ) : ContainerSettings
}

/**
 * The screen's whole state.
 *
 * @property settings null until the stores answer — and **also** when the container is gone, which is a real state
 *   rather than a loading artifact: it is what the screen shows for the frame between "Remove container" and the
 *   back that follows it. The screen draws its chrome either way, so neither case flashes an error.
 * @property availableApps every installed app the container does not already hold, in label order — what the "Add
 *   apps" picker offers. Empty for a widget container, which has no app picker; resolving it there would be work
 *   done for a control that is never drawn.
 */
data class ContainerSettingsState(
    val settings: ContainerSettings? = null,
    val availableApps: List<AppInfo> = emptyList(),
)
