package lnreader.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import lnreader.data.HistoryEntryRecord
import lnreader.data.LibraryNovelRecord
import lnreader.data.NovelUpdate
import lnreader.service.ChapterInfo
import lnreader.service.LNReaderService
import lnreader.service.NovelDetails
import lnreader.service.NovelSummary
import lnreader.service.PluginManifestEntry

internal enum class AppTab(val label: String) {
    Library("Library"),
    Updates("Updates"),
    History("History"),
    Browse("Browse"),
}

internal sealed interface AppScreen {
    val tab: AppTab

    data class Root(override val tab: AppTab) : AppScreen

    data class NovelDetail(
        override val tab: AppTab,
        val pluginId: String,
        val novelPath: String,
        val title: String,
        val cover: String?,
        val novel: NovelDetails,
    ) : AppScreen

    data class Reader(
        override val tab: AppTab,
        val pluginId: String,
        val novelPath: String,
        val novelName: String,
        val chapter: ChapterInfo,
        val content: String,
    ) : AppScreen
}

@Stable
internal class LNReaderAppState(
    private val service: LNReaderService,
    private val appDatabase: lnreader.data.AppDatabase,
    private val scope: CoroutineScope,
) {
    var backStack by mutableStateOf(listOf<AppScreen>(AppScreen.Root(AppTab.Browse)))
        private set
    var pluginId by mutableStateOf("allnovel")
    var pluginLabel by mutableStateOf<String?>(null)
    var browseNovels by mutableStateOf<List<NovelSummary>>(emptyList())
    var manifestEntries by mutableStateOf<List<PluginManifestEntry>>(emptyList())
    var updates by mutableStateOf<List<NovelUpdate>>(emptyList())
    var lastUpdateCheckAt by mutableStateOf<Long?>(null)
    var isBusy by mutableStateOf(false)
    var busyLabel by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    val currentScreen: AppScreen get() = backStack.last()
    val currentTab: AppTab get() = currentScreen.tab
    val databasePath: String get() = appDatabase.path
    val libraryRepository get() = appDatabase.libraryRepository
    val historyRepository get() = appDatabase.historyRepository
    val updatesChecker get() = appDatabase.updatesChecker

    fun loadManifestSuggestions() {
        if (manifestEntries.isNotEmpty()) return
        launchTask("Loading plugins") {
            manifestEntries = service.listPlugins().sortedBy { it.name.lowercase() }
        }
    }

    fun selectTab(tab: AppTab) {
        errorMessage = null
        backStack = listOf(AppScreen.Root(tab))
    }

    fun popScreen() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
            errorMessage = null
        }
    }

    fun loadPopularNovels() = launchTask("Loading popular novels") {
        ensurePluginLoaded(pluginId)
        pluginLabel = service.activePlugin()?.let { "${it.name} (${it.id})" }
        browseNovels = service.popularNovels(1)
        backStack = listOf(AppScreen.Root(AppTab.Browse))
    }

    fun openBrowseNovel(novel: NovelSummary) = openNovelDetail(
        tab = AppTab.Browse,
        pluginId = pluginId,
        novelPath = novel.path,
        fallbackTitle = novel.name,
        fallbackCover = novel.cover,
    )

    fun openLibraryNovel(novel: LibraryNovelRecord) = openNovelDetail(
        tab = AppTab.Library,
        pluginId = novel.pluginId,
        novelPath = novel.novelPath,
        fallbackTitle = novel.name,
        fallbackCover = novel.cover,
    )

    fun openUpdateNovel(novel: NovelUpdate) = openNovelDetail(
        tab = AppTab.Updates,
        pluginId = novel.pluginId,
        novelPath = novel.novelPath,
        fallbackTitle = novel.name,
        fallbackCover = novel.cover,
    )

    fun openHistoryChapter(entry: HistoryEntryRecord) = launchTask("Opening chapter") {
        ensurePluginLoaded(entry.pluginId)
        val chapter = ChapterInfo(
            name = entry.chapterName,
            path = entry.chapterPath,
            releaseTime = null,
            chapterNumber = null,
        )
        val content = service.parseChapter(entry.chapterPath)
        historyRepository.recordChapterOpen(
            pluginId = entry.pluginId,
            novelPath = entry.novelPath,
            novelName = entry.novelName,
            chapterPath = entry.chapterPath,
            chapterName = entry.chapterName,
        )
        backStack = backStack + AppScreen.Reader(
            tab = AppTab.History,
            pluginId = entry.pluginId,
            novelPath = entry.novelPath,
            novelName = entry.novelName,
            chapter = chapter,
            content = content,
        )
    }

    fun openChapter(screen: AppScreen.NovelDetail, chapter: ChapterInfo) = launchTask("Opening chapter") {
        ensurePluginLoaded(screen.pluginId)
        val content = service.parseChapter(chapter.path)
        historyRepository.recordChapterOpen(
            pluginId = screen.pluginId,
            novelPath = screen.novelPath,
            novelName = screen.novel.name ?: screen.title,
            chapterPath = chapter.path,
            chapterName = chapter.name,
        )
        backStack = backStack + AppScreen.Reader(
            tab = screen.tab,
            pluginId = screen.pluginId,
            novelPath = screen.novelPath,
            novelName = screen.novel.name ?: screen.title,
            chapter = chapter,
            content = content,
        )
    }

    fun toggleLibrary(screen: AppScreen.NovelDetail, libraryNovels: List<LibraryNovelRecord>) = launchTask(
        if (isInLibrary(screen.pluginId, screen.novelPath, libraryNovels)) "Removing from library" else "Adding to library",
    ) {
        val existing = libraryNovels.firstOrNull { it.pluginId == screen.pluginId && it.novelPath == screen.novelPath }
        if (existing != null) {
            libraryRepository.removeNovel(screen.pluginId, screen.novelPath)
        } else {
            libraryRepository.saveNovel(
                pluginId = screen.pluginId,
                novelPath = screen.novelPath,
                name = screen.novel.name ?: screen.title,
                cover = screen.novel.cover ?: screen.cover,
                knownChapterCount = screen.novel.chapters.size,
            )
        }
    }

    fun checkUpdates() = launchTask("Checking updates") {
        updates = updatesChecker.checkNow(service)
        lastUpdateCheckAt = Clock.System.now().toEpochMilliseconds()
    }

    fun clearError() {
        errorMessage = null
    }

    private fun openNovelDetail(
        tab: AppTab,
        pluginId: String,
        novelPath: String,
        fallbackTitle: String,
        fallbackCover: String?,
    ) = launchTask("Loading novel") {
        ensurePluginLoaded(pluginId)
        val novel = service.parseNovel(novelPath)
        backStack = backStack + AppScreen.NovelDetail(
            tab = tab,
            pluginId = pluginId,
            novelPath = novelPath,
            title = novel.name ?: fallbackTitle,
            cover = novel.cover ?: fallbackCover,
            novel = novel,
        )
    }

    private suspend fun ensurePluginLoaded(pluginId: String) {
        if (service.activePlugin()?.id != pluginId) {
            val entry = service.loadPlugin(pluginId)
            pluginLabel = "${entry.name} (${entry.id})"
        }
    }

    private fun launchTask(label: String, block: suspend () -> Unit) {
        if (isBusy) return
        isBusy = true
        busyLabel = label
        errorMessage = null
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                errorMessage = e.message ?: e.toString()
            } finally {
                isBusy = false
                busyLabel = null
            }
        }
    }

    private fun isInLibrary(pluginId: String, novelPath: String, libraryNovels: List<LibraryNovelRecord>): Boolean =
        libraryNovels.any { it.pluginId == pluginId && it.novelPath == novelPath }
}
