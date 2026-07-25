package inkspire.morphic.core.designsystem.drag

/**
 * Stable identity of a registered drop zone (home main, dock, a side surface, an open folder). The coordinator
 * keys its registry by this and reports the active target as a [ZoneId], so nothing downstream depends on a
 * zone's list index or screen position — a zone can re-measure or move without invalidating references to it.
 */
@JvmInline
value class ZoneId(val value: String)
