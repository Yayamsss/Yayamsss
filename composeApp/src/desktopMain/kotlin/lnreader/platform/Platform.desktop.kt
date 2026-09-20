package lnreader.platform

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
        val normalized = if (path.startsWith('/')) path else "/$path"
        return checkNotNull(object {}.javaClass.getResourceAsStream(normalized)) {
            "Missing bundled resource: $normalized"
        }.bufferedReader().use { it.readText() }
    }
}
