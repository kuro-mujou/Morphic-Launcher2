package inkspire.morphic.feature.apps

import inkspire.morphic.core.model.AppInfo

/**
 * The APPS surface's render state: every installed app, in the order the surface shows them.
 *
 * **One list, whatever the layout.** Every [inkspire.morphic.core.model.AppsLayout] renders the same collection
 * — the layout decides the *look*, not the contents — so there is one list here rather than a shape per layout.
 * The layouts that need more than an order (categories, an explicit page + slot) get that from their own store
 * when it exists; it will join this state as a separate field rather than replacing [apps], because even those
 * layouts still need the full collection.
 *
 * @property apps the apps in display order. Today that is always A–Z ([AppsViewModel]), because the built layout
 *   is a derived one — the vertical list and grid persist nothing and are re-derived from the app cache on every
 *   emission (see the arrangement model in the rewrite plan). A user-arranged order arrives with the pager.
 */
data class AppsState(val apps: List<AppInfo> = emptyList())
