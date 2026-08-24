package inkspire.morphic.data.layout

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.HomeListItemDao
import inkspire.morphic.core.database.entity.HomeListItemEntity
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [HomeListRepository] — one table, one column of interest, so there is nothing here but the reading and
 * the writing.
 *
 * **The stored `sortOrder` is always dense and always the list index.** Every write rewrites the whole list from
 * index 0, which is cheap (a home list is tens of rows, not thousands) and removes the only thing that could go
 * wrong with a sparse ordinal: two rows sharing one, after which the list's order depends on Room's tie-break rather
 * than on anything the user did. `AppsOrderRepositoryImpl` re-derives its slots the same way.
 *
 * **Replace, not diff.** A reorder changes almost every row's ordinal anyway, so a diff would compute more than it
 * saved — and replacing is what makes a *removal* fall out of the same path instead of needing its own delete. The
 * one write that does not go through it is [remove], which deletes a single row and leaves the rest to be renumbered
 * by the next full write; the gap it leaves is harmless because `ORDER BY sortOrder` cares about sequence, not
 * contiguity.
 *
 * **The replace is one DAO call, and that is load-bearing** ([HomeListItemDao.replaceAll]). As a `clear()` followed
 * by an `upsert()` the clear is observable: the DAO's flow re-runs on the invalidation and emits an *empty* list, so
 * the surface blanks for a frame and a just-dropped row appears to snap back before the new order lands. This is the
 * one place in `data:layout` that does use a Room transaction, and it is not the general fix the sibling
 * repositories still need — those should take the database and use `withTransaction`, and it is worth doing for all
 * three at once.
 *
 * Writes hop to [AppDispatchers.io]; the DAO's `Flow` stays on Room's own executor.
 */
internal class HomeListRepositoryImpl(
    private val dao: HomeListItemDao,
    private val dispatchers: AppDispatchers,
) : HomeListRepository {

    override val order: Flow<List<ComponentKey>> = dao.observe().map { rows -> rows.map { it.component } }

    override suspend fun setOrder(reported: List<ComponentKey>) {
        withContext(dispatchers.io) {
            val stored = order.first()
            val next = reconcileReportedOrder(known = stored, reported = reported)
            // Nothing to do when the drop landed where the app already was — and skipping matters, since a write
            // re-emits the flow and re-renders the surface underneath the finger that just lifted.
            if (next == stored) return@withContext
            write(next)
        }
    }

    override suspend fun seedIfEmpty(apps: List<ComponentKey>) {
        withContext(dispatchers.io) {
            if (order.first().isNotEmpty()) return@withContext
            if (apps.isEmpty()) return@withContext
            write(apps.distinct())
        }
    }

    override suspend fun add(components: List<ComponentKey>) {
        withContext(dispatchers.io) {
            val stored = order.first()
            // Skipped rather than moved to the end: an app already in the list is one the user has already placed,
            // and re-adding it from a picker should not quietly reorder what they arranged. `distinct` guards the
            // other half — the same app cannot be ticked twice, but the list handed in is the caller's.
            val added = components.distinct().filterNot { it in stored }
            if (added.isEmpty()) return@withContext
            write(stored + added)
        }
    }

    override suspend fun remove(component: ComponentKey) {
        withContext(dispatchers.io) { dao.deleteByComponent(component) }
    }

    /** Rewrites the table as [components], densely numbered from zero, in one transaction. */
    private suspend fun write(components: List<ComponentKey>) {
        dao.replaceAll(components.mapIndexed { index, component -> HomeListItemEntity(component, index) })
    }
}
