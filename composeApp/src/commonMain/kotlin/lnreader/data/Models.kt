package lnreader.data

import lnreader.service.PluginManifestEntry

data class RepositoryRecord(
    val url: String,
    val name: String?,
    val addedAt: Long,
)

data class InstalledPluginRecord(
    val pluginId: String,
    val name: String,
    val site: String,
    val lang: String,
    val version: String,
    val url: String,
    val iconUrl: String?,
    val repoUrl: String,
    val installedAt: Long,
)

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

fun InstalledPluginRecord.toManifestEntry() = PluginManifestEntry(
    id = pluginId,
    name = name,
    site = site,
    lang = lang,
    version = version,
    url = url,
    iconUrl = iconUrl,
)
