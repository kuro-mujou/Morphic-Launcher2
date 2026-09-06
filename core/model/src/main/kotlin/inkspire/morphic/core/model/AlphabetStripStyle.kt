package inkspire.morphic.core.model

/**
 * How the A–Z index strip renders and reacts to a finger running down it.
 *
 * **A look, not a behavior** — both styles scroll the same list to the same place and dim the same rows. What differs
 * is only what the strip itself does while the finger is on it, which is why this is one enum on the strip rather
 * than two components.
 */
enum class AlphabetStripStyle {
    /** Even letters in a column; the one under the finger is emphasized and nothing moves. */
    STANDARD,

    /**
     * The letters bow toward the finger and swell, falling off smoothly either side, with the current one repeated
     * in a badge beside the strip. Niagara Launcher's, which is where the shape is from.
     */
    CURVED,
}
