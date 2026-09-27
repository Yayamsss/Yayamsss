package lnreader.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import lnreader.data.HistoryEntryRecord
import lnreader.data.InstalledPluginRecord
import lnreader.data.LibraryNovelRecord
import lnreader.data.NovelUpdate
import lnreader.data.RepositoryRecord
import lnreader.data.toManifestEntry
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
    Extensions("Extensions"),
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

internal data class RepositoryCatalogState(
    val repository: RepositoryRecord,
    val plugins: List<PluginManifestEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

internal data class AvailablePlugin(
    val repository: RepositoryRecord,
    val entry: PluginManifestEntry,
)

internal enum class BrowseContentMode {
    Empty,
    Popular,
    Search,
}

internal data class BrowseNovelRecord(
    val pluginId: String,
    val pluginName: String,
    val novel: NovelSummary,
)

@Stable
internal class LNReaderAppState(
    private val service: LNReaderService,
    private val appDatabase: lnreader.data.AppDatabase,
    private val scope: CoroutineScope,
) {
    var backStack by mutableStateOf(listOf<AppScreen>(AppScreen.Root(AppTab.Browse)))
        private set
    var selectedPluginId by mutableStateOf<String?>(null)
        private set
    var browseShowingPicker by mutableStateOf(true)
        private set
    var browseItems by mutableStateOf<List<BrowseNovelRecord>>(emptyList())
        private set
    var browseContentMode by mutableStateOf(BrowseContentMode.Empty)
        private set
    var browseQuery by mutableStateOf("")
    var selectedSearchPluginIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var repositoryInput by mutableStateOf("")
    var repositoryCatalogs by mutableStateOf<List<RepositoryCatalogState>>(emptyList())
        private set
    var updates by mutableStateOf<List<NovelUpdate>>(emptyList())
    var lastUpdateCheckAt by mutableStateOf<Long?>(null)
    var isBusy by mutableStateOf(false)
    var busyLabel by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    val currentScreen: AppScreen get() = backStack.last()
    val currentTab: AppTab get() = currentScreen.tab
    val databasePath: String get() = appDatabase.path
    val repositoryRepository get() = appDatabase.repositoryRepository
    val installedPluginRepository get() = appDatabase.installedPluginRepository
    val libraryRepository get() = appDatabase.libraryRepository
    val historyRepository get() = appDatabase.historyRepository
    val updatesChecker get() = appDatabase.updatesChecker

    fun syncInstalledPlugins(installedPlugins: List<InstalledPluginRecord>) {
        val installedIds = installedPlugins.map { it.pluginId }.toSet()
        when {
            installedPlugins.isEmpty() -> {
                selectedPluginId = null
                browseItems = emptyList()
                selectedSearchPluginIds = emptySet()
                browseContentMode = BrowseContentMode.Empty
                browseShowingPicker = true
            }
            selectedPluginId == null || installedPlugins.none { it.pluginId == selectedPluginId } -> {
                selectedPluginId = installedPlugins.first().pluginId
                if (browseContentMode == BrowseContentMode.Popular) {
                    browseItems = emptyList()
                    browseContentMode = BrowseContentMode.Empty
                }
            }
        }
        if (installedIds.isNotEmpty()) {
            val currentSelection = selectedSearchPluginIds.intersect(installedIds)
            selectedSearchPluginIds = if (currentSelection.isEmpty()) installedIds else currentSelection
        }
        if (browseItems.any { it.pluginId !in installedIds }) {
            browseItems = browseItems.filter { it.pluginId in installedIds }
            if (browseItems.isEmpty()) {
                browseContentMode = BrowseContentMode.Empty
            }
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

    fun selectInstalledPlugin(pluginId: String) {
        if (selectedPluginId != pluginId) {
            selectedPluginId = pluginId
            if (browseContentMode == BrowseContentMode.Popular) {
                browseItems = emptyList()
                browseContentMode = BrowseContentMode.Empty
            }
        }
    }
    
    fun openSource(pluginId: String) {
        selectedPluginId = pluginId
        selectedSearchPluginIds = setOf(pluginId)
        browseShowingPicker = false
        browseQuery = ""
        loadPopularNovels()
    }

    fun closeSource() {
        browseShowingPicker = true
        browseItems = emptyList()
        browseContentMode = BrowseContentMode.Empty
        browseQuery = ""
    }

    fun toggleSearchPlugin(pluginId: String) {
        selectedSearchPluginIds = if (pluginId in selectedSearchPluginIds) {
            selectedSearchPluginIds - pluginId
        } else {
            selectedSearchPluginIds + pluginId
        }
    }

    fun refreshExtensions() = launchTask("Refreshing extensions") {
        val repositories = repositoryRepository.snapshot()
        refreshRepositoryCatalogs(repositories = repositories)
    }

    fun refreshRepository(url: String) = launchTask("Refreshing repository") {
        val repositories = repositoryRepository.snapshot()
        refreshRepositoryCatalogs(repositories = repositories, onlyUrl = url)
    }

    fun addRepository() = launchTask("Adding repository") {
        val url = repositoryInput.trim()
        require(url.isNotBlank()) { "Enter a repository URL." }
        repositoryRepository.saveRepository(url)
        repositoryInput = ""
        refreshRepositoryCatalogs(repositoryRepository.snapshot(), onlyUrl = url)
    }

    fun removeRepository(url: String) = launchTask("Removing repository") {
        repositoryRepository.removeRepository(url)
        repositoryCatalogs = repositoryCatalogs.filterNot { it.repository.url == url }
    }

    fun installPlugin(repository: RepositoryRecord, entry: PluginManifestEntry) = launchTask("Installing extension") {
        installedPluginRepository.install(entry, repository.url)
        if (selectedPluginId == null) {
            selectedPluginId = entry.id
        }
    }

    fun uninstallPlugin(pluginId: String) = launchTask("Removing extension") {
        installedPluginRepository.uninstall(pluginId)
        if (selectedPluginId == pluginId) {
            val remaining = installedPluginRepository.snapshot()
            selectedPluginId = remaining.firstOrNull()?.pluginId
            if (browseContentMode == BrowseContentMode.Popular) {
                browseItems = emptyList()
                browseContentMode = BrowseContentMode.Empty
            }
            selectedSearchPluginIds = selectedSearchPluginIds - pluginId
            if (selectedSearchPluginIds.isEmpty() && remaining.isNotEmpty()) {
                selectedSearchPluginIds = remaining.map { it.pluginId }.toSet()
            }
        } else {
            browseItems = browseItems.filterNot { it.pluginId == pluginId }
            if (browseItems.isEmpty()) {
                browseContentMode = BrowseContentMode.Empty
            }
            selectedSearchPluginIds = selectedSearchPluginIds - pluginId
        }
    }

    fun loadPopularNovels() = launchTask("Loading popular novels") {
        val plugin = requireSelectedPlugin()
        ensurePluginLoaded(plugin)
        browseItems = service.popularNovels(1).map {
            BrowseNovelRecord(
                pluginId = plugin.pluginId,
                pluginName = plugin.name,
                novel = it,
            )
        }
        browseContentMode = BrowseContentMode.Popular
        errorMessage = null
        backStack = listOf(AppScreen.Root(AppTab.Browse))
    }

    fun searchInstalledSources() = launchTask("Searching installed sources") {
        val query = browseQuery.trim()
        require(query.isNotBlank()) { "Enter a search term." }
        val targets = installedPluginRepository.snapshot().filter { it.pluginId in selectedSearchPluginIds }
        require(targets.isNotEmpty()) { "Select at least one installed extension to search." }
        val results = mutableListOf<BrowseNovelRecord>()
        val failures = mutableListOf<String>()
        for (plugin in targets) {
            runCatching {
                ensurePluginLoaded(plugin)
                service.searchNovels(query, page = 1)
            }.onSuccess { novels ->
                results += novels.map {
                    BrowseNovelRecord(
                        pluginId = plugin.pluginId,
                        pluginName = plugin.name,
                        novel = it,
                    )
                }
            }.onFailure { error ->
                failures += "${plugin.name}: ${error.message ?: error::class.simpleName ?: "unknown error"}"
            }
        }
        browseItems = results.sortedWith(compareBy({ it.novel.name.lowercase() }, { it.pluginName.lowercase() }))
        browseContentMode = BrowseContentMode.Search
        if (failures.isEmpty()) {
            errorMessage = null
        } else {
            errorMessage = "Some sources failed during search: ${failures.joinToString(" | ")}"
        }
        backStack = listOf(AppScreen.Root(AppTab.Browse))
    }

    fun openBrowseNovel(item: BrowseNovelRecord) = launchTask("Loading novel") {
        openNovelDetail(
            tab = AppTab.Browse,
            pluginId = item.pluginId,
            novelPath = item.novel.path,
            fallbackTitle = item.novel.name,
            fallbackCover = item.novel.cover,
        )
    }

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

    fun availablePlugins(): List<AvailablePlugin> =
        repositoryCatalogs.flatMap { catalog -> catalog.plugins.map { AvailablePlugin(catalog.repository, it) } }
            .sortedWith(compareBy({ it.entry.name.lowercase() }, { it.entry.lang.lowercase() }, { it.repository.url.lowercase() }))

    private suspend fun refreshRepositoryCatalogs(repositories: List<RepositoryRecord>, onlyUrl: String? = null) {
        val current = repositoryCatalogs.associateBy { it.repository.url }
        repositoryCatalogs = repositories.map { repository ->
            val existing = current[repository.url]
            if (onlyUrl == null || onlyUrl == repository.url) {
                RepositoryCatalogState(
                    repository = repository,
                    plugins = existing?.plugins.orEmpty(),
                    isLoading = true,
                    errorMessage = null,
                )
            } else {
                existing?.copy(repository = repository)
                    ?: RepositoryCatalogState(repository = repository)
            }
        }
        val targets = repositories.filter { onlyUrl == null || it.url == onlyUrl }
        for (repository in targets) {
            val state = runCatching {
                RepositoryCatalogState(
                    repository = repository,
                    plugins = service.refreshPlugins(repository.url).sortedBy { it.name.lowercase() },
                    isLoading = false,
                    errorMessage = null,
                )
            }.getOrElse { error ->
                RepositoryCatalogState(
                    repository = repository,
                    plugins = current[repository.url]?.plugins.orEmpty(),
                    isLoading = false,
                    errorMessage = error.message ?: error.toString(),
                )
            }
            repositoryCatalogs = repositoryCatalogs.map {
                if (it.repository.url == repository.url) state else it
            }
        }
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

    private suspend fun requireSelectedPlugin(): InstalledPluginRecord {
        val pluginId = selectedPluginId ?: error("Install a plugin from Extensions first.")
        return installedPluginRepository.find(pluginId)
            ?: error("Selected plugin '$pluginId' is no longer installed.")
    }

    private suspend fun ensurePluginLoaded(pluginId: String) {
        val plugin = installedPluginRepository.find(pluginId)
            ?: error("Plugin '$pluginId' is not installed. Install it from Extensions first.")
        ensurePluginLoaded(plugin)
    }

    private suspend fun ensurePluginLoaded(plugin: InstalledPluginRecord) {
        if (service.activePlugin()?.id != plugin.pluginId || service.activePlugin()?.url != plugin.url) {
            service.loadPlugin(plugin.toManifestEntry())
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
