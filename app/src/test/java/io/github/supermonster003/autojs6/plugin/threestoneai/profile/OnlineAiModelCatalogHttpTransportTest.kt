package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiNetworkAccess
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OnlineAiModelCatalogHttpTransportTest {
    @Test
    fun clientUsesBoundedAnonymousRequestsAndDoesNotFollowRedirects() {
        val client = OnlineAiModelCatalogHttpTransport.createClient()
        try {
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
            assertFalse(client.retryOnConnectionFailure)
            assertSame(Authenticator.NONE, client.authenticator)
            assertSame(Authenticator.NONE, client.proxyAuthenticator)
            assertSame(CookieJar.NO_COOKIES, client.cookieJar)
            assertNull(client.cache)
            assertTrue(client.interceptors.isEmpty())
            assertTrue(client.networkInterceptors.isEmpty())
            assertEquals(15_000, client.connectTimeoutMillis)
            assertEquals(20_000, client.readTimeoutMillis)
            assertEquals(30_000, client.callTimeoutMillis)
            assertEquals("https", OnlineAiModelCatalogHttpTransport.PUBLISHED_URL.toHttpUrl().scheme)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    @Test
    fun localServerReceivesOnlyPublicHeadersAndConditionalValidator() {
        val payload = modelCatalogPayload(2)
        SingleResponseServer("200 OK", payload, mapOf("ETag" to "\"v2\"")).use { server ->
            val transport = OnlineAiModelCatalogHttpTransport(OnlineAiNetworkAccess.UNRESTRICTED, server.url.toHttpUrl())
            try {
                val response = transport.fetch("\"v1\"")
                assertEquals(200, response.statusCode)
                assertEquals("\"v2\"", response.etag)
                assertArrayEquals(payload, response.payload)
                val request = server.request.get(5, TimeUnit.SECONDS).lowercase()
                assertTrue(request.startsWith("get /catalog.json http/1.1"))
                assertTrue(request.contains("if-none-match: \"v1\""))
                assertFalse(request.contains("authorization:"))
                assertFalse(request.contains("x-api-key:"))
                assertFalse(request.contains("cookie:"))
            } finally { close(transport) }
        }
    }

    @Test
    fun redirectsAreReturnedToTheRepositoryWithoutFollowingTheirLocation() {
        SingleResponseServer("302 Found", byteArrayOf(), mapOf("Location" to "http://127.0.0.1:1/forbidden")).use { server ->
            val transport = OnlineAiModelCatalogHttpTransport(OnlineAiNetworkAccess.UNRESTRICTED, server.url.toHttpUrl())
            try {
                assertEquals(302, transport.fetch(null).statusCode)
            } finally { close(transport) }
        }
    }

    @Test
    fun notModifiedDoesNotTryToDecodeAnEmptyBody() {
        SingleResponseServer("304 Not Modified", byteArrayOf()).use { server ->
            val transport = OnlineAiModelCatalogHttpTransport(OnlineAiNetworkAccess.UNRESTRICTED, server.url.toHttpUrl())
            try {
                val response = transport.fetch("\"v1\"")
                assertEquals(304, response.statusCode)
                assertNull(response.payload)
            } finally { close(transport) }
        }
    }

    @Test
    fun boundedReaderRejectsDeclaredAndUndeclaredOversizedBodies() {
        val maximum = OnlineAiModelCatalogCodec.MAXIMUM_DOCUMENT_BYTES
        assertThrows(Exception::class.java) {
            readOnlineAiModelCatalogBytes(ByteArrayInputStream(byteArrayOf()), maximum.toLong() + 1L)
        }
        assertThrows(Exception::class.java) {
            readOnlineAiModelCatalogBytes(ByteArrayInputStream(ByteArray(maximum + 1)))
        }
        assertEquals(maximum, readOnlineAiModelCatalogBytes(ByteArrayInputStream(ByteArray(maximum))).size)
    }

    @Test
    fun rejectsCredentialBearingAndNonLocalCleartextUrls() {
        for (url in listOf("https://user:secret@example.com/catalog.json", "http://example.com/catalog.json")) {
            assertThrows(Exception::class.java) {
                OnlineAiModelCatalogHttpTransport(OnlineAiNetworkAccess.UNRESTRICTED, url.toHttpUrl())
            }
        }
    }

    @Test
    fun cancellationInterruptsAnActiveSocketRead() {
        val release = CountDownLatch(1)
        SingleResponseServer("200 OK", modelCatalogPayload(), beforeResponse = { release.await(5, TimeUnit.SECONDS) }).use { server ->
            val transport = OnlineAiModelCatalogHttpTransport(OnlineAiNetworkAccess.UNRESTRICTED, server.url.toHttpUrl())
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<OnlineAiModelCatalogHttpResponse> { transport.fetch(null) }
                server.request.get(5, TimeUnit.SECONDS)
                transport.cancel()
                assertThrows(Exception::class.java) { future.get(5, TimeUnit.SECONDS) }
            } finally {
                release.countDown()
                executor.shutdownNow()
                close(transport)
            }
        }
    }

    private fun close(transport: OnlineAiModelCatalogHttpTransport) {
        transport.cancel()
        transport.client.connectionPool.evictAll()
        transport.client.dispatcher.executorService.shutdownNow()
    }

    private class SingleResponseServer(
        status: String,
        payload: ByteArray,
        headers: Map<String, String> = emptyMap(),
        beforeResponse: () -> Unit = {},
    ) : Closeable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/catalog.json"
        val request = CompletableFuture<String>()
        private val worker = Thread({
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    val lines = ArrayList<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        lines += line
                    }
                    request.complete(lines.joinToString("\n"))
                    beforeResponse()
                    val head = buildString {
                        append("HTTP/1.1 $status\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n")
                        headers.forEach { (name, value) -> append("$name: $value\r\n") }
                        append("\r\n")
                    }
                    socket.getOutputStream().apply {
                        write(head.toByteArray(StandardCharsets.US_ASCII))
                        write(payload)
                        flush()
                    }
                }
            } catch (error: Exception) {
                request.completeExceptionally(error)
            }
        }, "model-catalog-test-server").apply { isDaemon = true; start() }

        override fun close() {
            server.close()
            worker.join(6000L)
        }
    }
}
