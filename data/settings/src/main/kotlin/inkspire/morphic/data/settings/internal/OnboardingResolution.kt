package inkspire.morphic.data.settings.internal

import inkspire.morphic.data.settings.Onboarding

/**
 * Reads the onboarding flag, answering an **absent** one from whether the surface register has ever been written.
 *
 * **Absent is not the same as fresh.** An install that bound its edges before the flag existed has no `onboarding`
 * key, and reading that as "not set up" would open the picker over a working launcher — whose one action overwrites
 * the register the user built. A stored register is the evidence that someone has been here.
 *
 * Only absence is answered this way. A stored `completed = false` means what it says — which is what lets starting over
 * re-open the picker on an install that has a register, and what the first-run screen stamps before it writes the
 * register to preview a look. **Without that stamp the screen's own preview reads as a finished setup** and the gate
 * closes on its first write.
 */
internal fun SettingsSlice<Onboarding>.resolve(stored: String?, surfaceRegisterStored: Boolean): Onboarding =
    if (stored == null && surfaceRegisterStored) Onboarding.Completed else decode(stored)
