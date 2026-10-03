package com.liteweb.extractor.server

import com.liteweb.extractor.store.HlsUrlStore
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Lightweight NanoHTTPD local HTTP server.
 * Binds to 127.0.0.1:8080 (localhost only) by default.
 *
 * Endpoints:
 *   GET /?url=https://example.com        → load URL in WebView
 *   GET /url=https://example.com         → legacy path-style (same effect)
 *   GET /extract?url=https://example.com → return detected HLS info as JSON
 *   GET /status                          → server health check
 */
class LocalServer(
    port: Int = DEFAULT_PORT,
    private val onLoadUrl: (String) -> Unit
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        const val DEFAULT_PORT = 8080
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            handleRequest(session)
        } catch (e: Exception) {
            errorResponse("Internal server error: ${e.message}")
        }
    }

    private fun handleRequest(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val params = session.parameters ?: emptyMap()

        return when {
            // /extract?url=... → return HLS info as JSON
            uri.startsWith("/extract") -> handleExtract(params)

            // /status → health check
            uri == "/status" -> handleStatus()

            // /?url=... or /url=... → load URL in WebView
            else -> handleLoad(uri, params)
        }
    }

    private fun handleLoad(uri: String, params: Map<String, List<String>>): Response {
        // Support both /?url=... and /url=... formats
        val url = params["url"]?.firstOrNull()
            ?: extractUrlFromPath(uri)
            ?: return errorResponse("Missing 'url' parameter")

        if (!isValidUrl(url)) {
            return errorResponse("Invalid URL: must start with http:// or https://")
        }

        // Clear previous HLS detection when loading a new URL
        HlsUrlStore.clear()

        // Invoke on main thread via callback
        onLoadUrl(url)

        val json = JSONObject().apply {
            put("success", true)
            put("url", url)
            put("message", "URL loaded in WebView")
        }
        return jsonResponse(json.toString())
    }

    private fun handleExtract(params: Map<String, List<String>>): Response {
        val requestedUrl = params["url"]?.firstOrNull()

        // If a specific URL was requested, check if it's an HLS URL itself
        if (requestedUrl != null) {
            if (!isValidUrl(requestedUrl)) {
                return errorResponse("Invalid URL: must start with http:// or https://")
            }
            // If the URL itself is .m3u8, return it directly
            if (requestedUrl.endsWith(".m3u8") || requestedUrl.contains(".m3u8?")) {
                val json = JSONObject().apply {
                    put("success", true)
                    put("type", "hls")
                    put("url", requestedUrl)
                    put("contentType", "application/vnd.apple.mpegurl")
                    put("source", "url_pattern")
                }
                return jsonResponse(json.toString())
            }
        }

        // Return the latest detected HLS URL from the store
        val hlsUrl = HlsUrlStore.get()
        return if (hlsUrl != null) {
            val json = JSONObject().apply {
                put("success", true)
                put("type", "hls")
                put("url", hlsUrl)
                put("contentType", HlsUrlStore.getContentType() ?: "application/vnd.apple.mpegurl")
                put("source", "webview_detection")
            }
            jsonResponse(json.toString())
        } else {
            val json = JSONObject().apply {
                put("success", false)
                put("url", JSONObject.NULL)
                put("error", "HLS stream not detected")
            }
            jsonResponse(json.toString())
        }
    }

    private fun handleStatus(): Response {
        val json = JSONObject().apply {
            put("success", true)
            put("server", "LiteWebExtractor")
            put("version", "1.0.0")
            put("port", DEFAULT_PORT)
            put("hlsDetected", HlsUrlStore.hasUrl())
        }
        return jsonResponse(json.toString())
    }

    private fun jsonResponse(json: String): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json
        ).also { it.addHeader("Access-Control-Allow-Origin", "*") }
    }

    private fun errorResponse(message: String): Response {
        val json = JSONObject().apply {
            put("success", false)
            put("error", message)
        }
        return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "application/json",
            json.toString()
        )
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    /** Extract URL from path like /url=https://example.com */
    private fun extractUrlFromPath(uri: String): String? {
        val prefix = "/url="
        return if (uri.startsWith(prefix)) {
            uri.substring(prefix.length).trim()
        } else null
    }
}
