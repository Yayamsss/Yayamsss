package lnreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LNReaderDarkColors = darkColorScheme()

@Composable
fun LNReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LNReaderDarkColors,
        content = content,
    )
}
