package inkspire.morphic.feature.settings.apps

import inkspire.morphic.core.model.DeviceConfiguration
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.data.settings.CardOverride
import inkspire.morphic.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Which of a card's chrome numbers a control is committing. */
internal enum class CardChromeField { TitleScale, CornerRadius, OuterPadding, InnerPadding }

/**
 * The write half of the **category card's** chrome controls — `IconSizingEdits` for the numbers that shape a tile
 * rather than the icons inside it.
 *
 * The same shape as its sibling and for the same reasons: every commit is one sparse field, so an untouched control
 * keeps following the blueprint; the slot and the device are read through lambdas, because both change while this
 * object does not; and "reset" is a plain write of an empty override, after which the store removes the entry rather
 * than keeping an empty one.
 *
 * Its own class rather than four methods on the section's ViewModel because the section already has one of these and
 * the pair reads as what it is — a card has two groups of settings, and each group owns its writes.
 */
internal class CardChromeEdits(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val slot: () -> GridSlot,
    private val device: () -> DeviceConfiguration?,
) {

    /** Commits one chrome value. The dp fields arrive already rounded by their sliders. */
    fun change(field: CardChromeField, value: Float) {
        edit {
            when (field) {
                CardChromeField.TitleScale -> copy(titleScale = value)
                CardChromeField.CornerRadius -> copy(cornerRadiusDp = value.toInt())
                CardChromeField.OuterPadding -> copy(outerPaddingDp = value.toInt())
                CardChromeField.InnerPadding -> copy(innerPaddingDp = value.toInt())
            }
        }
    }

    /**
     * Clears one field, returning it to the blueprint's all-zero chrome.
     *
     * **`null` rather than the default value**, which is what each control's reset writes: a zero written down is an
     * override saying what the blueprint already says, and it would outlive a change to that default. See
     * `IconSizingEdits.clear`.
     */
    fun clear(field: CardChromeField) {
        edit {
            when (field) {
                CardChromeField.TitleScale -> copy(titleScale = null)
                CardChromeField.CornerRadius -> copy(cornerRadiusDp = null)
                CardChromeField.OuterPadding -> copy(outerPaddingDp = null)
                CardChromeField.InnerPadding -> copy(innerPaddingDp = null)
            }
        }
    }

    private fun edit(transform: CardOverride.() -> CardOverride) {
        val configuration = device() ?: return
        scope.launch { settings.updateCard(slot(), configuration, transform) }
    }
}
