package lnreader.data

import lnreader.service.LNReaderService

class UpdatesChecker(
    private val libraryRepository: LibraryRepository,
    private val installedPluginRepository: InstalledPluginRepository,
) {
    suspend fun checkNow(service: LNReaderService): List<NovelUpdate> {
        val updates = mutableListOf<NovelUpdate>()
        val library = libraryRepository.snapshot()
        for (novel in library) {
            val plugin = installedPluginRepository.find(novel.pluginId) ?: continue
            if (service.activePlugin()?.id != plugin.pluginId || service.activePlugin()?.url != plugin.url) {
                service.loadPlugin(plugin.toManifestEntry())
            }
            val latest = service.parseNovel(novel.novelPath)
            val latestCount = latest.chapters.size
            if (latestCount > novel.knownChapterCount) {
                updates += NovelUpdate(
                    pluginId = novel.pluginId,
                    novelPath = novel.novelPath,
                    name = latest.name ?: novel.name,
                    cover = latest.cover ?: novel.cover,
                    previousChapterCount = novel.knownChapterCount,
                    latestChapterCount = latestCount,
                )
            }
            libraryRepository.updateMetadata(
                pluginId = novel.pluginId,
                novelPath = novel.novelPath,
                name = latest.name ?: novel.name,
                cover = latest.cover ?: novel.cover,
                knownChapterCount = latestCount,
            )
        }
        return updates
    }
}
