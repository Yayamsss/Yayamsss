package lnreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import lnreader.service.ChapterInfo
import lnreader.service.LNReaderService
import lnreader.service.NovelDetails
import lnreader.service.NovelSummary

/** Which pane is currently shown: the popular-novels list, a novel's metadata/chapters, or a chapter's content. */
private sealed interface Screen {
    data object NovelList : Screen
    data class NovelDetail(val novel: NovelDetails) : Screen
    data class ChapterView(val novel: NovelDetails, val chapter: ChapterInfo, val content: String) : Screen
}

fun main() = application {
    val service = remember { LNReaderService() }
    DisposableEffect(Unit) { onDispose { service.close() } }

    Window(onCloseRequest = ::exitApplication, title = "LNReader Desktop") {
        MaterialTheme {
            LNReaderApp(service)
        }
    }
}

@Composable
private fun LNReaderApp(service: LNReaderService) {
    val scope = rememberCoroutineScope()

    var pluginId by remember { mutableStateOf("allnovel") }
    var pluginLabel by remember { mutableStateOf<String?>(null) }
    var novels by remember { mutableStateOf<List<NovelSummary>>(emptyList()) }
    var screen by remember { mutableStateOf<Screen>(Screen.NovelList) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun runTask(block: suspend () -> Unit) {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                errorMessage = e.message ?: e.toString()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadPopular() = runTask {
        val entry = service.loadPlugin(pluginId)
        pluginLabel = "${entry.name} (${entry.id})"
        novels = service.popularNovels(1)
        screen = Screen.NovelList
    }

    fun openNovel(path: String) = runTask {
        screen = Screen.NovelDetail(service.parseNovel(path))
    }

    fun openChapter(novel: NovelDetails, chapter: ChapterInfo) = runTask {
        val content = service.parseChapter(chapter.path)
        screen = Screen.ChapterView(novel, chapter, content)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pluginId,
                onValueChange = { pluginId = it },
                label = { Text("Plugin id") },
                singleLine = true,
                modifier = Modifier.width(220.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { loadPopular() }, enabled = !isLoading) { Text("Load popular novels") }
            Spacer(Modifier.width(8.dp))
            pluginLabel?.let { Text(it, style = MaterialTheme.typography.caption) }
            if (isLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(20.dp))
            }
        }

        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it", color = MaterialTheme.colors.error)
        }

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        when (val s = screen) {
            is Screen.NovelList -> NovelListPane(novels, onSelect = { openNovel(it.path) })
            is Screen.NovelDetail -> NovelDetailPane(
                novel = s.novel,
                onBack = { screen = Screen.NovelList },
                onOpenChapter = { chapter -> openChapter(s.novel, chapter) },
            )
            is Screen.ChapterView -> ChapterPane(
                novel = s.novel,
                chapter = s.chapter,
                content = s.content,
                onBack = { screen = Screen.NovelDetail(s.novel) },
            )
        }
    }
}

@Composable
private fun NovelListPane(novels: List<NovelSummary>, onSelect: (NovelSummary) -> Unit) {
    if (novels.isEmpty()) {
        Text("No novels loaded yet — pick a plugin id and click \"Load popular novels\".")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(novels) { novel ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(novel) }, elevation = 1.dp) {
                Text(novel.name, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun NovelDetailPane(
    novel: NovelDetails,
    onBack: () -> Unit,
    onOpenChapter: (ChapterInfo) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back") }
            Spacer(Modifier.width(8.dp))
            Text(novel.name ?: "(untitled)", style = MaterialTheme.typography.h6)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            novel.author?.let { Text("Author: $it") }
            novel.artist?.let { Text("Artist: $it") }
            novel.status?.let { Text("Status: $it") }
            novel.genres?.let { Text("Genres: $it") }
            novel.summary?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Chapters (${novel.chapters.size})", style = MaterialTheme.typography.subtitle1)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(novel.chapters) { chapter ->
                Text(
                    chapter.name,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChapter(chapter) }.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChapterPane(novel: NovelDetails, chapter: ChapterInfo, content: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< ${novel.name ?: "Back"}") }
            Spacer(Modifier.width(8.dp))
            Text(chapter.name, style = MaterialTheme.typography.h6)
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(content, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        }
    }
}
