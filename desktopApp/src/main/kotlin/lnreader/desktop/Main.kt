package lnreader.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import lnreader.cli.runCliFlow
import lnreader.ui.LNReaderApp

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--smoke-test") {
        runBlocking {
            runCliFlow(args.drop(1).toTypedArray())
        }
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LNReader Desktop",
        ) {
            LNReaderApp()
        }
    }
}
