package lnreader.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import lnreader.data.db.LNReaderDatabase
import java.io.File

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val databaseFile = File(databasePath())
        databaseFile.parentFile?.mkdirs()
        val isNew = !databaseFile.exists() || databaseFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        if (isNew) {
            LNReaderDatabase.Schema.create(driver)
        }
        return driver
    }

    actual fun databasePath(): String = File(File(System.getProperty("user.home")), ".lnreader/lnreader.db").absolutePath
}
