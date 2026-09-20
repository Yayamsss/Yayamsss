package lnreader.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import lnreader.data.db.LNReaderDatabase
import java.io.File
import java.sql.DriverManager

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val databaseFile = File(databasePath())
        databaseFile.parentFile?.mkdirs()
        val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
        val isNew = !databaseFile.exists() || databaseFile.length() == 0L
        val driver = JdbcSqliteDriver(jdbcUrl)
        val schemaVersion = LNReaderDatabase.Schema.version
        val detectedVersion = currentVersion(jdbcUrl)
        val tables = tableNames(jdbcUrl)

        when {
            isNew -> {
                LNReaderDatabase.Schema.create(driver)
                setUserVersion(jdbcUrl, schemaVersion)
            }
            detectedVersion in 1 until schemaVersion -> {
                LNReaderDatabase.Schema.migrate(driver, detectedVersion, schemaVersion)
                setUserVersion(jdbcUrl, schemaVersion)
            }
            detectedVersion == 0L && "AppMeta" in tables -> {
                setUserVersion(jdbcUrl, schemaVersion)
            }
            detectedVersion == 0L && "LibraryNovel" in tables && "HistoryEntry" in tables -> {
                LNReaderDatabase.Schema.migrate(driver, 1, schemaVersion)
                setUserVersion(jdbcUrl, schemaVersion)
            }
        }
        return driver
    }

    actual fun databasePath(): String = File(File(System.getProperty("user.home")), ".lnreader/lnreader.db").absolutePath

    private fun currentVersion(jdbcUrl: String): Long =
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { resultSet ->
                    if (resultSet.next()) resultSet.getLong(1) else 0L
                }
            }
        }

    private fun setUserVersion(jdbcUrl: String, version: Long) {
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = $version")
            }
        }
    }

    private fun tableNames(jdbcUrl: String): Set<String> =
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { resultSet ->
                    buildSet {
                        while (resultSet.next()) {
                            add(resultSet.getString(1))
                        }
                    }
                }
            }
        }
}
