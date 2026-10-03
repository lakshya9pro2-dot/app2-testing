package com.liteweb.extractor

import com.liteweb.extractor.server.LocalServer
import com.liteweb.extractor.store.HlsUrlStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Unit/integration tests for the local NanoHTTPD server.
 * Runs on JVM (no Android device required).
 */
class LocalServerTest {

    private lateinit var server: LocalServer
    private var lastLoadedUrl: String? = null

    @Before
    fun setUp() {
        HlsUrlStore.clear()
        server = LocalServer(8081) { url -> lastLoadedUrl = url }
        server.start()
        Thread.sleep(200) // Give server time to bind
    }

    @After
    fun tearDown() {
        server.stop()
        HlsUrlStore.clear()
        lastLoadedUrl = null
    }

    // ── Status endpoint ─────────────────────────────────────────────────────

    @Test
    fun `status endpoint returns success`() {
        val (code, body) = get("http://127.0.0.1:8081/status")
        assertEquals(200, code)
        assertTrue(body.contains("\"success\":true"))
    }

    // ── Load URL endpoint ───────────────────────────────────────────────────

    @Test
    fun `load valid URL triggers callback`() {
        val (code, body) = get("http://127.0.0.1:8081/?url=https://example.com")
        assertEquals(200, code)
        assertTrue(body.contains("\"success\":true"))
        assertEquals("https://example.com", lastLoadedUrl)
    }

    @Test
    fun `load URL path style triggers callback`() {
        val (code, body) = get("http://127.0.0.1:8081/url=https://example.com/newvideo")
        assertEquals(200, code)
        assertTrue(body.contains("\"success\":true"))
        assertEquals("https://example.com/newvideo", lastLoadedUrl)
    }

    @Test
    fun `missing URL parameter returns error`() {
        val (code, body) = get("http://127.0.0.1:8081/")
        assertEquals(400, code)
        assertTrue(body.contains("\"success\":false"))
    }

    @Test
    fun `invalid URL scheme returns error`() {
        val (_, body) = get("http://127.0.0.1:8081/?url=ftp://example.com")
        assertTrue(body.contains("\"success\":false"))
        assertTrue(body.contains("Invalid URL"))
    }

    @Test
    fun `javascript scheme is rejected`() {
        val (_, body) = get("http://127.0.0.1:8081/?url=javascript:alert(1)")
        assertTrue(body.contains("\"success\":false"))
    }

    // ── Extract endpoint ────────────────────────────────────────────────────

    @Test
    fun `extract returns not detected when store empty`() {
        val (code, body) = get("http://127.0.0.1:8081/extract?url=https://example.com/video")
        assertEquals(200, code)
        assertTrue(body.contains("\"success\":false"))
        assertTrue(body.contains("HLS stream not detected"))
    }

    @Test
    fun `extract detects m3u8 URL directly`() {
        val (code, body) = get("http://127.0.0.1:8081/extract?url=https://example.com/master.m3u8")
        assertEquals(200, code)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("\"type\":\"hls\""))
        assertTrue(body.contains("master.m3u8"))
    }

    @Test
    fun `extract returns stored HLS URL`() {
        HlsUrlStore.set("https://cdn.example.com/stream/index.m3u8", "application/vnd.apple.mpegurl")
        val (code, body) = get("http://127.0.0.1:8081/extract?url=https://example.com")
        assertEquals(200, code)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("index.m3u8"))
    }

    @Test
    fun `loading new URL clears HLS store`() {
        HlsUrlStore.set("https://old.example.com/old.m3u8")
        get("http://127.0.0.1:8081/?url=https://example.com/newpage")
        assertFalse(HlsUrlStore.hasUrl())
    }

    // ── HLS Store ────────────────────────────────────────────────────────────

    @Test
    fun `HlsUrlStore is thread safe`() {
        val threads = (1..10).map { i ->
            Thread { HlsUrlStore.set("https://example.com/stream$i.m3u8") }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertNotNull(HlsUrlStore.get())
        assertTrue(HlsUrlStore.get()!!.endsWith(".m3u8"))
    }

    @Test
    fun `HlsUrlStore clear works`() {
        HlsUrlStore.set("https://example.com/video.m3u8")
        assertTrue(HlsUrlStore.hasUrl())
        HlsUrlStore.clear()
        assertFalse(HlsUrlStore.hasUrl())
        assertNull(HlsUrlStore.get())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun get(urlStr: String): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        return try {
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
