package lnreader.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import lnreader.data.db.LNReaderDatabase
import lnreader.service.DEFAULT_MANIFEST_URL

class RepositoryRepository(
    private val database: LNReaderDatabase,
) {
    private val queries = database.libraryHistoryQueries

    fun seedDefaults() {
        if (queries.selectAppMeta(DEFAULT_REPOSITORY_SEEDED_KEY).executeAsOneOrNull() != null) return
        queries.upsertRepository(
            url = DEFAULT_MANIFEST_URL,
            name = "LNReader official",
            addedAt = Clock.System.now().toEpochMilliseconds(),
        )
        queries.upsertAppMeta(DEFAULT_REPOSITORY_SEEDED_KEY, "true")
    }

    fun observeRepositories(): Flow<List<RepositoryRecord>> =
        queries.selectRepositories(::mapRepository)
            .asFlow()
            .mapToList(Dispatchers.Default)

    suspend fun snapshot(): List<RepositoryRecord> = withContext(Dispatchers.Default) {
        queries.selectRepositories(::mapRepository).executeAsList()
    }

    suspend fun find(url: String): RepositoryRecord? = withContext(Dispatchers.Default) {
        queries.selectRepository(url, ::mapRepository).executeAsOneOrNull()
    }

    suspend fun saveRepository(
        url: String,
        name: String? = null,
        addedAt: Long = Clock.System.now().toEpochMilliseconds(),
    ) = withContext(Dispatchers.Default) {
        val existing = queries.selectRepository(url, ::mapRepository).executeAsOneOrNull()
        queries.upsertRepository(
            url = url,
            name = name ?: existing?.name,
            addedAt = existing?.addedAt ?: addedAt,
        )
    }

    suspend fun removeRepository(url: String) = withContext(Dispatchers.Default) {
        queries.deleteRepository(url)
    }

    private fun mapRepository(
        url: String,
        name: String?,
        addedAt: Long,
    ) = RepositoryRecord(
        url = url,
        name = name,
        addedAt = addedAt,
    )

    private companion object {
        const val DEFAULT_REPOSITORY_SEEDED_KEY = "default_repository_seeded"
    }
}
