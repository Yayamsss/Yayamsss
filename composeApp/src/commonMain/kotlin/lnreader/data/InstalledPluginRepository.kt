package lnreader.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import lnreader.data.db.LNReaderDatabase
import lnreader.service.PluginManifestEntry

class InstalledPluginRepository(
    private val database: LNReaderDatabase,
) {
    private val queries = database.libraryHistoryQueries

    fun observeInstalledPlugins(): Flow<List<InstalledPluginRecord>> =
        queries.selectInstalledPlugins(::mapInstalledPlugin)
            .asFlow()
            .mapToList(Dispatchers.Default)

    suspend fun snapshot(): List<InstalledPluginRecord> = withContext(Dispatchers.Default) {
        queries.selectInstalledPlugins(::mapInstalledPlugin).executeAsList()
    }

    suspend fun find(pluginId: String): InstalledPluginRecord? = withContext(Dispatchers.Default) {
        queries.selectInstalledPlugin(pluginId, ::mapInstalledPlugin).executeAsOneOrNull()
    }

    suspend fun install(
        entry: PluginManifestEntry,
        repoUrl: String,
        installedAt: Long = Clock.System.now().toEpochMilliseconds(),
    ) = withContext(Dispatchers.Default) {
        val existing = queries.selectInstalledPlugin(entry.id, ::mapInstalledPlugin).executeAsOneOrNull()
        queries.upsertInstalledPlugin(
            pluginId = entry.id,
            name = entry.name,
            site = entry.site,
            lang = entry.lang,
            version = entry.version,
            url = entry.url,
            iconUrl = entry.iconUrl,
            repoUrl = repoUrl,
            installedAt = existing?.installedAt ?: installedAt,
        )
    }

    suspend fun uninstall(pluginId: String) = withContext(Dispatchers.Default) {
        queries.deleteInstalledPlugin(pluginId)
    }

    private fun mapInstalledPlugin(
        pluginId: String,
        name: String,
        site: String,
        lang: String,
        version: String,
        url: String,
        iconUrl: String?,
        repoUrl: String,
        installedAt: Long,
    ) = InstalledPluginRecord(
        pluginId = pluginId,
        name = name,
        site = site,
        lang = lang,
        version = version,
        url = url,
        iconUrl = iconUrl,
        repoUrl = repoUrl,
        installedAt = installedAt,
    )
}
