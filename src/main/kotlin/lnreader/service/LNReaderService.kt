package lnreader.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import lnreader.runtime.JsRuntime
import lnreader.runtime.awaitJsPromise
import lnreader.runtime.valueAsText
import okhttp3.OkHttpClient
import okhttp3.Request
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.Executors

/**
 * Default manifest = the official LNReader plugin repo, pinned to v3.0.0
 * (the tag baked into master/package.json at the time this was written —
 * check https://github.com/lnreader/lnreader-plugins for the current one,
 * or point this at your own fork's .dist/plugins.min.json).
 */
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

/**
 * Reusable runtime/service that wraps a single GraalJS plugin: fetching the
 * plugin manifest, loading a plugin by id, and calling into its
 * `popularNovels`/`parseNovel`/`parseChapter` API. All GraalJS interaction is
 * confined to a dedicated single-threaded dispatcher because a [Context] must
 * only ever be used from the thread that created it.
 */
class LNReaderService(private val manifestUrl: String = DEFAULT_MANIFEST_URL) : AutoCloseable {
    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private val jsExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lnreader-js").apply { isDaemon = true }
    }
    private val jsDispatcher = jsExecutor.asCoroutineDispatcher()

    private var context: Context? = null
    private var plugin: Value? = null
    private var manifestCache: List<PluginManifestEntry>? = null

    /** Fetches (and caches) the plugin manifest. */
    suspend fun listPlugins(): List<PluginManifestEntry> = withContext(Dispatchers.IO) {
        manifestCache ?: fetchManifest().also { manifestCache = it }
    }

    private fun fetchManifest(): List<PluginManifestEntry> {
        val json = httpClient.newCall(Request.Builder().url(manifestUrl).build())
            .execute().use { it.body?.string() ?: "[]" }
        return gson.fromJson(json, Array<PluginManifestEntry>::class.java).toList()
    }

    /** Downloads and loads the plugin with the given [pluginId], making it the active plugin. */
    suspend fun loadPlugin(pluginId: String): PluginManifestEntry = withContext(jsDispatcher) {
        val entries = manifestCache ?: fetchManifest().also { manifestCache = it }
        val entry = entries.firstOrNull { it.id == pluginId }
            ?: error("Plugin '$pluginId' not found in manifest (${entries.size} plugins available)")
        val pluginCode = httpClient.newCall(Request.Builder().url(entry.url).build())
            .execute().use { it.body?.string() ?: error("empty plugin body for ${entry.id}") }

        val ctx = context ?: JsRuntime.createContext().also { context = it }
        plugin = JsRuntime.loadPlugin(ctx, pluginCode)
        entry
    }

    /** Calls the active plugin's `popularNovels(page, { showLatestNovels: false, filters })`. */
    suspend fun popularNovels(page: Int = 1): List<NovelSummary> = withContext(jsDispatcher) {
        val ctx = requireContext()
        val pl = requirePlugin()
        val optionsArg = ctx.eval("js", "(function(f) { return { showLatestNovels: false, filters: f || {} }; })")
            .execute(pl.getMember("filters"))
        val result = awaitJsPromise(ctx, pl.invokeMember("popularNovels", page, optionsArg))
        toJsonElement(ctx, result).asJsonArray.map { it.asJsonObject.toNovelSummary() }
    }

    /** Calls the active plugin's `parseNovel(path)` and returns its metadata + chapter list. */
    suspend fun parseNovel(path: String): NovelDetails = withContext(jsDispatcher) {
        val ctx = requireContext()
        val pl = requirePlugin()
        val result = awaitJsPromise(ctx, pl.invokeMember("parseNovel", path))
        toJsonElement(ctx, result).asJsonObject.toNovelDetails()
    }

    /**
     * Calls the active plugin's `parseChapter(path)` and returns the chapter content as
     * plain, readable text: the plugin's raw result is HTML (e.g. `<p>` per paragraph), so
     * it's parsed and converted to text with blank lines between paragraphs.
     */
    suspend fun parseChapter(path: String): String = withContext(jsDispatcher) {
        val ctx = requireContext()
        val pl = requirePlugin()
        val result = awaitJsPromise(ctx, pl.invokeMember("parseChapter", path))
        htmlToReadableText(valueAsText(ctx, result))
    }

    private fun requireContext(): Context = context ?: error("Call loadPlugin() before using the service.")
    private fun requirePlugin(): Value = plugin ?: error("Call loadPlugin() before using the service.")

    private fun toJsonElement(ctx: Context, value: Value) =
        JsonParser.parseString(
            ctx.eval("js", "(function(v){return JSON.stringify(v === undefined ? null : v);})")
                .execute(value).asString() ?: "null"
        )

    override fun close() {
        context?.close()
        jsExecutor.shutdown()
    }
}

/**
 * Converts a chapter's raw HTML (as returned by plugins' `parseChapter()`) into plain text,
 * preserving paragraph breaks (`<p>`, `<br>`, block elements) as blank lines.
 */
private fun htmlToReadableText(html: String): String {
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

private fun JsonObject.stringOrNull(key: String): String? {
    val el = get(key) ?: return null
    if (el.isJsonNull) return null
    return if (el.isJsonPrimitive) el.asString else el.toString()
}

private fun JsonObject.doubleOrNull(key: String): Double? {
    val el = get(key) ?: return null
    if (el.isJsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isNumber) return null
    return el.asDouble
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
    val genresEl = get("genres")
    val genres = when {
        genresEl == null || genresEl.isJsonNull -> null
        genresEl.isJsonArray -> genresEl.asJsonArray.joinToString(", ") { it.asString }
        else -> genresEl.asString
    }
    val chapters = get("chapters")?.takeIf { it.isJsonArray }?.asJsonArray
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
