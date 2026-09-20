package lnreader.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import lnreader.data.db.LNReaderDatabase
import lnreader.platform.AndroidRuntimeContext

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(LNReaderDatabase.Schema, AndroidRuntimeContext.requireContext(), DATABASE_NAME)

    actual fun databasePath(): String =
        AndroidRuntimeContext.requireContext().getDatabasePath(DATABASE_NAME).absolutePath

    private companion object {
        const val DATABASE_NAME = "lnreader.db"
    }
}
