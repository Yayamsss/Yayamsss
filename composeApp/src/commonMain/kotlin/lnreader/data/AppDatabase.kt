package lnreader.data

import lnreader.data.db.LNReaderDatabase

class AppDatabase(
    private val driverFactory: DatabaseDriverFactory = DatabaseDriverFactory(),
) {
    val path: String = driverFactory.databasePath()
    val database: LNReaderDatabase by lazy { LNReaderDatabase(driverFactory.createDriver()) }
    val repositoryRepository: RepositoryRepository by lazy { RepositoryRepository(database).also { it.seedDefaults() } }
    val installedPluginRepository: InstalledPluginRepository by lazy { InstalledPluginRepository(database) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database) }
    val historyRepository: HistoryRepository by lazy { HistoryRepository(database) }
    val updatesChecker: UpdatesChecker by lazy { UpdatesChecker(libraryRepository, installedPluginRepository) }

    init {
        repositoryRepository
    }
}
