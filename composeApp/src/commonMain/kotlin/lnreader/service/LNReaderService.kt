package lnreader.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lnreader.platform.JsThread
import lnreader.platform.PlatformHtml
import lnreader.platform.PlatformIO
import lnreader.platform.PlatformJson
import lnreader.runtime.JsRuntime

const val DEFAULT_MANIFEST_URL =
    "https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json"

data class PluginManifestEntry(
    val id: String,
    val name: String,
    val site: String,
    val lang: String,
    val version: String,
    val url: String,
    val iconUrl: String?,
)

data class NovelSummary(
    val name: String,
    val path: String,
    val cover: String?,
)

data class ChapterInfo(
    val name: String,
    val path: String,
    val releaseTime: String?,
    val chapterNumber: Double?,
)

data class NovelDetails(
    val name: String?,
    val path: String?,
    val cover: String?,
    val summary: String?,
    val author: String?,
    val artist: String?,
    val status: String?,
    val genres: String?,
    val chapters: List<ChapterInfo>,
)

class LNReaderService(private val manifestUrl: String = DEFAULT_MANIFEST_URL) {
    private val jsThread = JsThread("lnreader-js")
    private var runtime: JsRuntime? = null
    private var manifestCache: List<PluginManifestEntry>? = null
    private var activePlugin: PluginManifestEntry? = null

    suspend fun listPlugins(): List<PluginManifestEntry> = withContext(Dispatchers.Default) {
        manifestCache ?: fetchManifest().also { manifestCache = it }
    }

    suspend fun loadPlugin(pluginId: String): PluginManifestEntry = withContext(jsThread.dispatcher) {
        val entries = manifestCache ?: fetchManifest().also { manifestCache = it }
        val entry = entries.firstOrNull { it.id == pluginId }
            ?: error("Plugin '$pluginId' not found in manifest (${entries.size} plugins available)")
        val pluginCode = PlatformIO.fetchText(entry.url)
        val jsRuntime = runtime ?: JsRuntime(jsThread.dispatcher).also {
            it.initialize()
            runtime = it
        }
        jsRuntime.loadPlugin(pluginCode)
        activePlugin = entry
        entry
    }

    suspend fun popularNovels(page: Int = 1): List<NovelSummary> = withContext(jsThread.dispatcher) {
        PlatformJson.parseNovelSummaries(requireRuntime().popularNovels(page))
    }

    suspend fun parseNovel(path: String): NovelDetails = withContext(jsThread.dispatcher) {
        PlatformJson.parseNovelDetails(requireRuntime().parseNovel(path))
    }

    suspend fun parseChapter(path: String): String = withContext(jsThread.dispatcher) {
        PlatformHtml.toReadableText(requireRuntime().parseChapter(path))
    }

    fun close() {
        runtime?.close()
        jsThread.close()
    }

    fun activePlugin(): PluginManifestEntry? = activePlugin

    private suspend fun fetchManifest(): List<PluginManifestEntry> =
        PlatformJson.parseManifestEntries(PlatformIO.fetchText(manifestUrl))

    private fun requireRuntime(): JsRuntime =
        runtime ?: error("Call loadPlugin() before using the service.")
}
