package app.koma.data

import app.cash.sqldelight.Query
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Store(private val db: KomaDatabase, private val context: CoroutineContext) {
    private val writer = Mutex()

    /** Bumped at sign-out, so a sync still in flight drops what it fetched for the old session. */
    @Volatile
    var generation = 0
        private set

    /** Runs [work] in a transaction, one writer at a time. */
    suspend fun <T> write(work: KomaDatabase.() -> T): T = writer.withLock {
        db.transactionWithResult { db.work() }
    }

    /** Runs [work] unless the person signed out since [generation]; answers whether it ran. */
    suspend fun write(generation: Int, work: KomaDatabase.() -> Unit): Boolean = write {
        if (generation != this@Store.generation) return@write false
        work()
        true
    }

    fun <T> read(work: KomaDatabase.() -> T): T = db.work()

    fun meta(key: String): String? = db.metaQueries.value(key).executeAsOneOrNull()

    /** Forgets the signed-in person's library and history. */
    suspend fun clearUserData() = write {
        generation++
        libraryEntryQueries.clear()
        readEventQueries.clear()
        metaQueries.clear()
    }

    /** [read]'s answer now and after each write to a table one of [queries] reads. */
    fun <T> watch(queries: KomaDatabase.() -> List<Query<*>>, read: KomaDatabase.() -> T): Flow<T> {
        val watched = db.queries()
        return callbackFlow {
                val listener = Query.Listener { trySend(Unit) }
                watched.forEach { it.addListener(listener) }
                trySend(Unit)
                awaitClose { watched.forEach { it.removeListener(listener) } }
            }
            .conflate()
            .map { db.read() }
            .flowOn(context)
    }
}
