package inkspire.morphic.data.settings.internal

import inkspire.morphic.data.settings.Onboarding

/**
 * Reads the onboarding flag, answering an **absent** one from whether the surface register has ever been written.
 *
 * **Absent is not the same as fresh.** An install that bound its edges before the flag existed has no `onboarding`
 * key, and reading that as "not set up" would open the picker over a working launcher — whose one action overwrites
 * the register the user built. A stored register is the evidence that someone has been here: only a user's choice
 * writes it, and the gate is what makes the first such choice.
 *
 * Only absence is answered this way. A stored `completed = false` means what it says, which is what lets starting over
 * re-open the picker on an install that has a register.
 */
internal fun SettingsSlice<Onboarding>.resolve(stored: String?, surfaceRegisterStored: Boolean): Onboarding =
    if (stored == null && surfaceRegisterStored) Onboarding.Completed else decode(stored)
