package lnreader.platform

import android.content.Context

object AndroidRuntimeContext {
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun requireContext(): Context =
        context ?: error("AndroidRuntimeContext is not initialized.")
}

actual object PlatformIO {
    actual suspend fun executeJsRequest(url: String, initJson: String): String =
        JvmHttpSupport.executeJsRequest(url, initJson)

    actual suspend fun fetchText(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): String = JvmHttpSupport.fetchText(url, method, headers, body)

    actual fun readResourceText(path: String): String {
        val assetName = path.removePrefix("/")
        return AndroidRuntimeContext.requireContext().assets.open(assetName).bufferedReader().use { it.readText() }
    }
}
