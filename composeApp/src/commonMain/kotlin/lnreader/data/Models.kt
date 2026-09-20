package lnreader.data

data class LibraryNovelRecord(
    val pluginId: String,
    val novelPath: String,
    val name: String,
    val cover: String?,
    val addedAt: Long,
    val knownChapterCount: Int,
)

data class HistoryEntryRecord(
    val pluginId: String,
    val novelPath: String,
    val novelName: String,
    val chapterPath: String,
    val chapterName: String,
    val readAt: Long,
)

data class NovelUpdate(
    val pluginId: String,
    val novelPath: String,
    val name: String,
    val cover: String?,
    val previousChapterCount: Int,
    val latestChapterCount: Int,
) {
    val newChapters: Int get() = latestChapterCount - previousChapterCount
}
