package lnreader.platform

import kotlinx.coroutines.CoroutineDispatcher
import lnreader.service.NovelDetails
import lnreader.service.NovelSummary
import lnreader.service.PluginManifestEntry

expect class JsThread(name: String) {
    val dispatcher: CoroutineDispatcher
    fun close()
}

expect object PlatformIO {
    suspend fun executeJsRequest(url: String, initJson: String): String

    suspend fun fetchText(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): String

    fun readResourceText(path: String): String
}

expect object PlatformJson {
    fun quoteString(value: String): String
    fun parseManifestEntries(json: String): List<PluginManifestEntry>
    fun parseNovelSummaries(json: String): List<NovelSummary>
    fun parseNovelDetails(json: String): NovelDetails
}

expect object PlatformHtml {
    fun toReadableText(html: String): String
}
