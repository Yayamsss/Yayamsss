package lnreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.Instant
import lnreader.data.AppDatabase
import lnreader.data.HistoryEntryRecord
import lnreader.data.LibraryNovelRecord
import lnreader.data.NovelUpdate
import lnreader.service.LNReaderService
import lnreader.service.NovelDetails
import lnreader.service.NovelSummary

@Composable
fun LNReaderApp() {
    val service = remember { LNReaderService() }
    val appDatabase = remember { AppDatabase() }
    val scope = rememberCoroutineScope()
    val appState = remember { LNReaderAppState(service, appDatabase, scope) }

    DisposableEffect(Unit) {
        onDispose { service.close() }
    }

    LaunchedEffect(Unit) {
        appState.loadManifestSuggestions()
    }

    LNReaderTheme {
        LNReaderAppContent(appState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LNReaderAppContent(appState: LNReaderAppState) {
    val library by appState.libraryRepository.observeLibrary().collectAsState(initial = emptyList())
    val history by appState.historyRepository.observeHistory().collectAsState(initial = emptyList())
    val currentScreen = appState.currentScreen

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle(currentScreen)) },
                navigationIcon = {
                    if (currentScreen !is AppScreen.Root) {
                        TextButton(onClick = appState::popScreen) {
                            Text("Back")
                        }
                    }
                },
                actions = {
                    if (appState.isBusy) {
                        appState.busyLabel?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(8.dp))
                        }
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentScreen.tab == tab,
                        onClick = { appState.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.Library -> Icons.Filled.Bookmark
                                    AppTab.Updates -> Icons.Filled.Update
                                    AppTab.History -> Icons.Filled.History
                                    AppTab.Browse -> Icons.Filled.Search
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            appState.errorMessage?.let {
                ErrorBanner(message = it, onDismiss = appState::clearError)
                Spacer(Modifier.height(12.dp))
            }
            when (currentScreen) {
                is AppScreen.Root -> when (currentScreen.tab) {
                    AppTab.Library -> LibraryRoot(
                        library = library,
                        onOpenNovel = appState::openLibraryNovel,
                        databasePath = appState.databasePath,
                    )
                    AppTab.Updates -> UpdatesRoot(
                        library = library,
                        updates = appState.updates,
                        lastCheckedAt = appState.lastUpdateCheckAt,
                        isBusy = appState.isBusy,
                        onCheckNow = appState::checkUpdates,
                        onOpenNovel = appState::openUpdateNovel,
                    )
                    AppTab.History -> HistoryRoot(history = history, onOpenHistory = appState::openHistoryChapter)
                    AppTab.Browse -> BrowseRoot(
                        pluginId = appState.pluginId,
                        onPluginIdChange = { appState.pluginId = it },
                        pluginLabel = appState.pluginLabel,
                        novels = appState.browseNovels,
                        manifestEntries = appState.manifestEntries,
                        isBusy = appState.isBusy,
                        onLoadPopular = appState::loadPopularNovels,
                        onSelectNovel = appState::openBrowseNovel,
                    )
                }
                is AppScreen.NovelDetail -> NovelDetailScreen(
                    screen = currentScreen,
                    inLibrary = library.any { it.pluginId == currentScreen.pluginId && it.novelPath == currentScreen.novelPath },
                    onToggleLibrary = { appState.toggleLibrary(currentScreen, library) },
                    onOpenChapter = { appState.openChapter(currentScreen, it) },
                )
                is AppScreen.Reader -> ReaderScreen(screen = currentScreen)
            }
        }
    }
}

private fun screenTitle(screen: AppScreen): String = when (screen) {
    is AppScreen.Root -> screen.tab.label
    is AppScreen.NovelDetail -> screen.novel.name ?: screen.title
    is AppScreen.Reader -> screen.chapter.name
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun BrowseRoot(
    pluginId: String,
    onPluginIdChange: (String) -> Unit,
    pluginLabel: String?,
    novels: List<NovelSummary>,
    manifestEntries: List<lnreader.service.PluginManifestEntry>,
    isBusy: Boolean,
    onLoadPopular: () -> Unit,
    onSelectNovel: (NovelSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pluginId,
                onValueChange = onPluginIdChange,
                label = { Text("Plugin id") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = onLoadPopular, enabled = !isBusy && pluginId.isNotBlank()) {
                Text("Load")
            }
        }
        Spacer(Modifier.height(8.dp))
        pluginLabel?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
        }
        val quickPicks = manifestEntries.filter {
            pluginId.isBlank() || it.id.contains(pluginId, ignoreCase = true) || it.name.contains(pluginId, ignoreCase = true)
        }.take(10)
        if (quickPicks.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickPicks.size) { index ->
                    val entry = quickPicks[index]
                    FilterChip(
                        selected = entry.id == pluginId,
                        onClick = { onPluginIdChange(entry.id) },
                        label = { Text(entry.id) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        if (novels.isEmpty()) {
            EmptyState(
                title = "Browse sources",
                message = "Pick a plugin id, load its popular feed, then open a novel to read or save it to your library.",
            )
        } else {
            NovelGrid(novels = novels, onSelectNovel = onSelectNovel)
        }
    }
}

@Composable
private fun LibraryRoot(
    library: List<LibraryNovelRecord>,
    onOpenNovel: (LibraryNovelRecord) -> Unit,
    databasePath: String,
) {
    if (library.isEmpty()) {
        EmptyState(
            title = "Your library is empty",
            message = "Add novels from Browse to save them locally. Database: $databasePath",
        )
        return
    }
    LibraryGrid(library = library, onOpenNovel = onOpenNovel)
}

@Composable
private fun UpdatesRoot(
    library: List<LibraryNovelRecord>,
    updates: List<NovelUpdate>,
    lastCheckedAt: Long?,
    isBusy: Boolean,
    onCheckNow: () -> Unit,
    onOpenNovel: (NovelUpdate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onCheckNow, enabled = library.isNotEmpty() && !isBusy) {
                Text("Check now")
            }
            Spacer(Modifier.width(12.dp))
            Text("Saved series: ${library.size}")
        }
        lastCheckedAt?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Last checked ${formatRelativeTime(it)} (${formatAbsoluteTime(it)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            library.isEmpty() -> EmptyState(
                title = "No tracked series",
                message = "Add novels to the library first so Updates can compare their chapter counts.",
            )
            updates.isEmpty() -> EmptyState(
                title = "No pending updates",
                message = "Run Check now to compare fresh chapter counts for everything in your library.",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = updates, key = { "${it.pluginId}:${it.novelPath}" }) { update ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpenNovel(update) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoverImage(url = update.cover, title = update.name, modifier = Modifier.width(72.dp).aspectRatio(0.7f))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(update.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "+${update.newChapters} chapters • ${update.previousChapterCount} → ${update.latestChapterCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRoot(history: List<HistoryEntryRecord>, onOpenHistory: (HistoryEntryRecord) -> Unit) {
    if (history.isEmpty()) {
        EmptyState(
            title = "No reading history yet",
            message = "Open a chapter from Browse or Library and it will appear here.",
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = history, key = { "${it.pluginId}:${it.chapterPath}" }) { entry ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpenHistory(entry) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(entry.novelName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(entry.chapterName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatRelativeTime(entry.readAt)} • ${formatAbsoluteTime(entry.readAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelDetailScreen(
    screen: AppScreen.NovelDetail,
    inLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    onOpenChapter: (lnreader.service.ChapterInfo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                CoverImage(
                    url = screen.novel.cover ?: screen.cover,
                    title = screen.novel.name ?: screen.title,
                    modifier = Modifier.width(120.dp).aspectRatio(0.7f),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(screen.novel.name ?: screen.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    MetadataLine("Author", screen.novel.author)
                    MetadataLine("Artist", screen.novel.artist)
                    MetadataLine("Status", screen.novel.status)
                    MetadataLine("Genres", screen.novel.genres)
                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = onToggleLibrary,
                        label = { Text(if (inLibrary) "Remove from Library" else "Add to Library") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (inLibrary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
        screen.novel.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Summary", style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("Chapters (${screen.novel.chapters.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(items = screen.novel.chapters, key = { it.path }) { chapter ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpenChapter(chapter) }) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(chapter.name, style = MaterialTheme.typography.bodyLarge)
                    chapter.releaseTime?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(screen: AppScreen.Reader) {
    Column(Modifier.fillMaxSize()) {
        Text(screen.novelName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(screen.chapter.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(
                text = screen.content,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NovelGrid(novels: List<NovelSummary>, onSelectNovel: (NovelSummary) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(novels, key = { it.path }) { novel ->
            NovelCard(title = novel.name, cover = novel.cover, onClick = { onSelectNovel(novel) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGrid(library: List<LibraryNovelRecord>, onOpenNovel: (LibraryNovelRecord) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(library, key = { "${it.pluginId}:${it.novelPath}" }) { novel ->
            NovelCard(title = novel.name, cover = novel.cover, onClick = { onOpenNovel(novel) })
        }
    }
}

@Composable
private fun NovelCard(title: String, cover: String?, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            CoverImage(url = cover, title = title, modifier = Modifier.fillMaxWidth().aspectRatio(0.7f))
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CoverImage(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String?) {
    value?.takeIf { it.isNotBlank() }?.let {
        Text("$label: $it", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

private fun formatRelativeTime(epochMillis: Long): String {
    val delta = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - epochMillis).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "just now"
        delta < hour -> "${delta / minute}m ago"
        delta < day -> "${delta / hour}h ago"
        delta < 7 * day -> "${delta / day}d ago"
        else -> "${delta / (7 * day)}w ago"
    }
}

private fun formatAbsoluteTime(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis).toString().replace('T', ' ').substringBefore('.')
