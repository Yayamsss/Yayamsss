package lnreader.cli

import kotlinx.coroutines.runBlocking
import lnreader.data.AppDatabase
import lnreader.service.DEFAULT_MANIFEST_URL
import lnreader.service.LNReaderService

suspend fun runCliFlow(args: Array<String>) {
    val manifestUrl = args.getOrNull(0) ?: DEFAULT_MANIFEST_URL
    val pluginId = args.getOrNull(1) ?: "allnovel"
    val novelPathArg = args.getOrNull(2)
    val chapterPathArg = args.getOrNull(3)
    val searchQueryArg = args.getOrNull(4)

    val service = LNReaderService()
    val appDatabase = AppDatabase()
    try {
        appDatabase.repositoryRepository.saveRepository(manifestUrl)
        val repositories = appDatabase.repositoryRepository.snapshot()
        println("Repositories: ${repositories.map { it.url }}")

        println("Fetching manifest: $manifestUrl")
        val entry = service.refreshPlugins(manifestUrl).firstOrNull { it.id == pluginId }
            ?: error("Plugin '$pluginId' was not found in $manifestUrl")
        appDatabase.installedPluginRepository.install(entry, manifestUrl)
        println("Installed plugin: ${entry.name} (${entry.id}) v${entry.version} -> ${entry.url}")
        println("Installed snapshot size: ${appDatabase.installedPluginRepository.snapshot().size}")

        service.loadPlugin(entry)
        println("Loaded plugin instance: id=${entry.id} name=${entry.name}")

        if (!searchQueryArg.isNullOrBlank()) {
            val searchResults = service.searchNovels(searchQueryArg, 1)
            println("searchNovels($searchQueryArg) result:")
            println(searchResults)
        }

        val popular = service.popularNovels(1)
        println("popularNovels() result:")
        println(popular)

        val novelPath = novelPathArg ?: popular.firstOrNull()?.path
        if (novelPath != null) {
            val novel = service.parseNovel(novelPath)
            println("parseNovel($novelPath) result:")
            println(novel)

            appDatabase.libraryRepository.saveNovel(
                pluginId = entry.id,
                novelPath = novelPath,
                name = novel.name ?: popular.firstOrNull { it.path == novelPath }?.name ?: "(untitled)",
                cover = novel.cover ?: popular.firstOrNull { it.path == novelPath }?.cover,
                knownChapterCount = novel.chapters.size,
            )
            println("Library save ok -> ${appDatabase.path}")
            println("Library snapshot size: ${appDatabase.libraryRepository.snapshot().size}")

            val chapterPath = chapterPathArg ?: novel.chapters.firstOrNull()?.path
            val chapterName = novel.chapters.firstOrNull { it.path == chapterPath }?.name ?: chapterPathArg ?: "(unknown chapter)"
            if (chapterPath != null) {
                val chapter = service.parseChapter(chapterPath)
                println("parseChapter($chapterPath) result:")
                println(chapter)

                appDatabase.historyRepository.recordChapterOpen(
                    pluginId = entry.id,
                    novelPath = novelPath,
                    novelName = novel.name ?: "(untitled)",
                    chapterPath = chapterPath,
                    chapterName = chapterName,
                )
                println("History snapshot size: ${appDatabase.historyRepository.snapshot().size}")

                val updates = appDatabase.updatesChecker.checkNow(service)
                println("Updates check result count: ${updates.size}")
            } else if (chapterPathArg != null) {
                error("parseNovel() returned no chapter path; cannot call parseChapter().")
            }

            appDatabase.libraryRepository.removeNovel(entry.id, novelPath)
            println("Library cleanup size: ${appDatabase.libraryRepository.snapshot().size}")
        } else if (novelPathArg != null) {
            error("popularNovels() returned no novel path; cannot call parseNovel().")
        }
    } finally {
        service.close()
    }
}

fun main(args: Array<String>): Unit = runBlocking {
    runCliFlow(args)
}
