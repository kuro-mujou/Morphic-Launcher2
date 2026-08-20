package inkspire.morphic.data.icons.internal

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.IconOverrideDao
import inkspire.morphic.core.database.entity.IconOverrideEntity
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.data.icons.IconOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [IconOverrideRepository] — one table, two columns, so this is the codec and nothing else.
 *
 * **An unreadable row is skipped, not fatal and not deleted.** Skipping it means the app renders from the global
 * default, which is a state the user can see and fix by editing that icon again; deleting it would throw away a
 * recipe that a later build — one that understands whatever the blob contains — could well read. That is the same
 * position `data:layout` takes on a placement whose app is missing: an entry the current build cannot resolve is
 * not evidence that the user wants it gone.
 *
 * Writes hop to [AppDispatchers.io]; the DAO's `Flow` stays on Room's own executor.
 *
 * `internal` so only Koin constructs it; consumers depend on [IconOverrideRepository].
 */
internal class IconOverrideRepositoryImpl(
    private val dao: IconOverrideDao,
    private val dispatchers: AppDispatchers,
) : IconOverrideRepository {

    override val overrides: Flow<Map<ComponentKey, IconAppearance>> =
        dao.observeAll().map { rows ->
            rows.mapNotNull { row ->
                IconAppearanceCodec.decode(row.appearance)?.let { row.component to it }
            }.toMap()
        }

    override suspend fun set(component: ComponentKey, appearance: IconAppearance) {
        withContext(dispatchers.io) {
            dao.upsert(IconOverrideEntity(component, IconAppearanceCodec.encode(appearance)))
        }
    }

    override suspend fun clear(component: ComponentKey) {
        withContext(dispatchers.io) { dao.delete(component) }
    }
}
