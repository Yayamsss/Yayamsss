package lnreader.cli

import kotlinx.coroutines.runBlocking
import lnreader.service.DEFAULT_MANIFEST_URL
import lnreader.service.LNReaderService

suspend fun runCliFlow(args: Array<String>) {
    val manifestUrl = args.getOrNull(0) ?: DEFAULT_MANIFEST_URL
    val pluginId = args.getOrNull(1) ?: "allnovel"
    val novelPathArg = args.getOrNull(2)
    val chapterPathArg = args.getOrNull(3)

    val service = LNReaderService(manifestUrl)
    try {
        println("Fetching manifest: $manifestUrl")
        val entry = service.loadPlugin(pluginId)
        println("Found plugin: ${entry.name} (${entry.id}) v${entry.version} -> ${entry.url}")
        println("Loaded plugin instance: id=${entry.id} name=${entry.name}")

        val popular = service.popularNovels(1)
        println("popularNovels() result:")
        println(popular)

        val novelPath = novelPathArg ?: popular.firstOrNull()?.path
        if (novelPath != null) {
            val novel = service.parseNovel(novelPath)
            println("parseNovel($novelPath) result:")
            println(novel)

            val chapterPath = chapterPathArg ?: novel.chapters.firstOrNull()?.path
            if (chapterPath != null) {
                val chapter = service.parseChapter(chapterPath)
                println("parseChapter($chapterPath) result:")
                println(chapter)
            } else if (chapterPathArg != null) {
                error("parseNovel() returned no chapter path; cannot call parseChapter().")
            }
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
