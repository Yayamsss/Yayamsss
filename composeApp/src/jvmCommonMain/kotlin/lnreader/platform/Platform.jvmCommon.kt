package lnreader.platform

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import lnreader.service.ChapterInfo
import lnreader.service.NovelDetails
import lnreader.service.NovelSummary
import lnreader.service.PluginManifestEntry
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.Executors

actual class JsThread actual constructor(name: String) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    actual val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    actual fun close() {
        executor.shutdown()
    }
}

internal object JvmHttpSupport {
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    suspend fun executeJsRequest(url: String, initJson: String): String = withContext(Dispatchers.IO) {
        val init = gson.fromJson(initJson, Map::class.java) as? Map<String, Any?> ?: emptyMap()
        val method = (init["method"] as? String) ?: "GET"
        val headersMap = (init["headers"] as? Map<String, Any?>) ?: emptyMap()
        val bodyString = init["body"] as? String

        val builder = Request.Builder().url(url)
        headersMap.forEach { (key, value) ->
            if (value != null) {
                builder.addHeader(key, value.toString())
            }
        }

        val requestBody = bodyString?.toRequestBody("text/plain".toMediaTypeOrNull())
        when (method.uppercase()) {
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> builder.delete(requestBody)
            else -> builder.get()
        }

        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseText = response.body?.string() ?: ""
                val headersOut = response.headers.names().associateWith { name -> response.headers[name] }
                gson.toJson(
                    mapOf(
                        "ok" to response.isSuccessful,
                        "status" to response.code,
                        "headers" to headersOut.mapKeys { it.key.lowercase() },
                        "bodyText" to responseText,
                    )
                )
            }
        } catch (e: Exception) {
            gson.toJson(
                mapOf(
                    "ok" to false,
                    "status" to 0,
                    "headers" to emptyMap<String, String>(),
                    "bodyText" to "",
                    "error" to (e.message ?: e.toString()),
                )
            )
        }
    }

    suspend fun fetchText(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.addHeader(key, value) }

        val requestBody = body?.toRequestBody("application/json".toMediaTypeOrNull())
        when (method.uppercase()) {
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> builder.delete(requestBody)
            else -> builder.get()
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            response.body?.string() ?: ""
        }
    }
}

actual object PlatformJson {
    private val gson = Gson()

    actual fun quoteString(value: String): String = gson.toJson(value)

    actual fun parseManifestEntries(json: String): List<PluginManifestEntry> =
        gson.fromJson(json, Array<PluginManifestEntry>::class.java).orEmpty().toList()

    actual fun parseNovelSummaries(json: String): List<NovelSummary> =
        JsonParser.parseString(json).asJsonArray.map { it.asJsonObject.toNovelSummary() }

    actual fun parseNovelDetails(json: String): NovelDetails =
        JsonParser.parseString(json).asJsonObject.toNovelDetails()
}

actual object PlatformHtml {
    actual fun toReadableText(html: String): String {
        val document: Document = Jsoup.parse(html)
        document.select("br").append("\n")
        document.select("p, div").prepend("\n\n")
        return document.wholeText()
            .lineSequence()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

private fun JsonObject.stringOrNull(key: String): String? {
    val element = get(key) ?: return null
    if (element.isJsonNull) return null
    return if (element.isJsonPrimitive) element.asString else element.toString()
}

private fun JsonObject.doubleOrNull(key: String): Double? {
    val element = get(key) ?: return null
    if (element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
    return element.asDouble
}

private fun JsonObject.toNovelSummary() = NovelSummary(
    name = stringOrNull("name") ?: "(untitled)",
    path = stringOrNull("path") ?: "",
    cover = stringOrNull("cover"),
)

private fun JsonObject.toChapterInfo() = ChapterInfo(
    name = stringOrNull("name") ?: "(untitled chapter)",
    path = stringOrNull("path") ?: "",
    releaseTime = stringOrNull("releaseTime"),
    chapterNumber = doubleOrNull("chapterNumber"),
)

private fun JsonObject.toNovelDetails(): NovelDetails {
    val genresElement = get("genres")
    val genres = when {
        genresElement == null || genresElement.isJsonNull -> null
        genresElement.isJsonArray -> genresElement.asJsonArray.joinToString(", ") { it.asString }
        else -> genresElement.asString
    }
    val chapters = get("chapters")
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.map { it.asJsonObject.toChapterInfo() }
        ?: emptyList()

    return NovelDetails(
        name = stringOrNull("name"),
        path = stringOrNull("path"),
        cover = stringOrNull("cover"),
        summary = stringOrNull("summary"),
        author = stringOrNull("author"),
        artist = stringOrNull("artist"),
        status = stringOrNull("status"),
        genres = genres,
        chapters = chapters,
    )
}
