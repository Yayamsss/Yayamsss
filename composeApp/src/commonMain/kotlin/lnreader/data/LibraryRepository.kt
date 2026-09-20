package lnreader.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import lnreader.data.db.LNReaderDatabase

class LibraryRepository(
    private val database: LNReaderDatabase,
) {
    private val queries = database.libraryHistoryQueries

    fun observeLibrary(): Flow<List<LibraryNovelRecord>> =
        queries.selectLibraryNovels(::mapLibraryNovel)
            .asFlow()
            .mapToList(Dispatchers.Default)

    suspend fun snapshot(): List<LibraryNovelRecord> = withContext(Dispatchers.Default) {
        queries.selectLibraryNovels(::mapLibraryNovel).executeAsList()
    }

    suspend fun find(pluginId: String, novelPath: String): LibraryNovelRecord? = withContext(Dispatchers.Default) {
        queries.selectLibraryNovel(pluginId, novelPath, ::mapLibraryNovel).executeAsOneOrNull()
    }

    suspend fun saveNovel(
        pluginId: String,
        novelPath: String,
        name: String,
        cover: String?,
        knownChapterCount: Int,
        addedAt: Long = Clock.System.now().toEpochMilliseconds(),
    ) = withContext(Dispatchers.Default) {
        queries.upsertLibraryNovel(
            pluginId = pluginId,
            novelPath = novelPath,
            name = name,
            cover = cover,
            addedAt = addedAt,
            knownChapterCount = knownChapterCount.toLong(),
        )
    }

    suspend fun updateMetadata(
        pluginId: String,
        novelPath: String,
        name: String,
        cover: String?,
        knownChapterCount: Int,
    ) = withContext(Dispatchers.Default) {
        queries.updateLibraryNovelMetadata(
            name = name,
            cover = cover,
            knownChapterCount = knownChapterCount.toLong(),
            pluginId = pluginId,
            novelPath = novelPath,
        )
    }

    suspend fun updateKnownChapterCount(pluginId: String, novelPath: String, knownChapterCount: Int) =
        withContext(Dispatchers.Default) {
            queries.updateKnownChapterCount(
                knownChapterCount = knownChapterCount.toLong(),
                pluginId = pluginId,
                novelPath = novelPath,
            )
        }

    suspend fun removeNovel(pluginId: String, novelPath: String) = withContext(Dispatchers.Default) {
        queries.deleteLibraryNovel(pluginId, novelPath)
    }

    private fun mapLibraryNovel(
        pluginId: String,
        novelPath: String,
        name: String,
        cover: String?,
        addedAt: Long,
        knownChapterCount: Long,
    ) = LibraryNovelRecord(
        pluginId = pluginId,
        novelPath = novelPath,
        name = name,
        cover = cover,
        addedAt = addedAt,
        knownChapterCount = knownChapterCount.toInt(),
    )
}
