package lnreader.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import lnreader.data.db.LNReaderDatabase

class HistoryRepository(
    private val database: LNReaderDatabase,
) {
    private val queries = database.libraryHistoryQueries

    fun observeHistory(): Flow<List<HistoryEntryRecord>> =
        queries.selectHistoryEntries(::mapHistoryEntry)
            .asFlow()
            .mapToList(Dispatchers.Default)

    suspend fun snapshot(): List<HistoryEntryRecord> = withContext(Dispatchers.Default) {
        queries.selectHistoryEntries(::mapHistoryEntry).executeAsList()
    }

    suspend fun recordChapterOpen(
        pluginId: String,
        novelPath: String,
        novelName: String,
        chapterPath: String,
        chapterName: String,
        readAt: Long = Clock.System.now().toEpochMilliseconds(),
    ) = withContext(Dispatchers.Default) {
        queries.upsertHistoryEntry(
            pluginId = pluginId,
            novelPath = novelPath,
            novelName = novelName,
            chapterPath = chapterPath,
            chapterName = chapterName,
            readAt = readAt,
        )
    }

    private fun mapHistoryEntry(
        pluginId: String,
        novelPath: String,
        novelName: String,
        chapterPath: String,
        chapterName: String,
        readAt: Long,
    ) = HistoryEntryRecord(
        pluginId = pluginId,
        novelPath = novelPath,
        novelName = novelName,
        chapterPath = chapterPath,
        chapterName = chapterName,
        readAt = readAt,
    )
}
