package io.github.supermonster003.autojs6.plugin.threestoneai.download

import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelTestHttpServer.Answer
import org.autojs.plugin.downloadkit.DownloadFailure
import org.autojs.plugin.downloadkit.DownloadFailureKind
import org.autojs.plugin.downloadkit.DownloadStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The model download on the shared download library (plugin-center P13.3): resuming a cut
 * transfer, the next mirror, partial files of the older downloader (Q51), hash and content checks,
 * the redirect policy and the one-download rule, against a loopback HTTP server.
 */
class ModelDownloadStorageTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var server: ModelTestHttpServer
    private lateinit var modelDirectory: File
    private lateinit var downloadDirectory: File
    private val content = validContent(256 * 1024)
    private val model = modelFor("test-model", content)

    @Before
    fun setUp() {
        server = ModelTestHttpServer()
        modelDirectory = temporary.newFolder("models")
        downloadDirectory = File(temporary.root, "model-downloads")
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun aCutTransferIsRetriedByItselfAndContinuesFromTheCut() {
        server.script("/m", Answer.Serve(content, cutAfter = CUT), Answer.Serve(content))
        val storage = storage(listOf(server.url("/m")))
        val events = RecordingEvents()

        val ready = fetchReady(storage, events)

        val exchanges = server.awaitExchanges(2)
        assertEquals(2, exchanges.size)
        assertEquals(Triple(null, 200, CUT), exchanges[0].let { Triple(it.range, it.status, it.sentBytes) })
        assertEquals("bytes=$CUT-", exchanges[1].range)
        assertEquals("Same server: the validator guards the resume", ModelTestHttpServer.ETAG, exchanges[1].ifRange)
        assertEquals(206, exchanges[1].status)
        assertEquals(content.size - CUT, exchanges[1].sentBytes)
        assertEquals(1, events.reconnects)
        assertMonotonic(events.progress)
        assertEquals(content.size.toLong(), events.progress.last())
        assertArrayEquals(content, ready.file.readBytes())
    }

    @Test
    fun theNextMirrorContinuesThePartialFileWithoutIfRange() {
        server.script("/a", Answer.Serve(content, cutAfter = CUT), Answer.Status(503))
        server.script("/b", Answer.Serve(content))
        val storage = storage(listOf(server.url("/a"), server.url("/b")))
        val events = RecordingEvents()

        fetchReady(storage, events)

        val exchanges = server.awaitExchanges(4)
        assertEquals(listOf("/a", "/a", "/a", "/b"), exchanges.map { it.path })
        assertEquals(listOf(200, 503, 503, 206), exchanges.map { it.status })
        assertEquals("bytes=$CUT-", exchanges[3].range)
        assertNull("Another server: no If-Range, the SHA-256 decides", exchanges[3].ifRange)
        assertEquals(content.size - CUT, exchanges[3].sentBytes)
        assertEquals("Two retries on the first mirror, then the second one", 3, events.reconnects)
        assertMonotonic(events.progress)
    }

    @Test
    fun aPartialFileOfTheOlderDownloaderIsAdoptedAndContinued() {
        val legacy = storage(listOf(server.url("/m"))).legacyFile(model.expectedSha256)
        legacy.writeBytes(content.copyOf(LEGACY_BYTES.toInt()))
        server.script("/m", Answer.Serve(content))
        val storage = storage(listOf(server.url("/m")))

        assertEquals(LEGACY_BYTES, storage.prepare(model))
        assertFalse("The older file moved into the store", legacy.exists())
        assertEquals(LEGACY_BYTES, storage.partFile(model).length())
        val ready = fetchReady(storage, RecordingEvents())

        val exchanges = server.awaitExchanges(1)
        assertEquals(1, exchanges.size)
        assertEquals("bytes=$LEGACY_BYTES-", exchanges[0].range)
        assertEquals(206, exchanges[0].status)
        assertEquals(content.size - LEGACY_BYTES, exchanges[0].sentBytes)
        assertArrayEquals(content, ready.file.readBytes())
    }

    @Test
    fun aCompleteFileLeftForTheImportIsVerifiedWithoutARequest() {
        val storage = storage(listOf(server.url("/m")))
        storage.legacyFile(model.expectedSha256).writeBytes(content)
        val events = RecordingEvents()

        assertEquals(content.size.toLong(), storage.prepare(model))
        val ready = fetchReady(storage, events)

        assertTrue(server.exchanges.isEmpty())
        assertTrue(events.verifying > 0)
        val handOff = storage.handOff(model, ready)
        assertEquals(storage.legacyFile(model.expectedSha256), handOff)
        assertArrayEquals(content, handOff.readBytes())
        assertTrue("The store was only staging", storage.store.entries().isEmpty())
        assertEquals(content.size.toLong(), storage.storedBytes(model))
    }

    @Test
    fun aFileWithTheWrongHashIsRejectedAndNothingIsKept() {
        val corrupted = content.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        server.script("/m", Answer.Serve(corrupted))
        val storage = storage(listOf(server.url("/m")))

        val result = storage.fetch(model, ModelDownloadOperationControl(), AtomicBoolean(false), RecordingEvents())

        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, (result as ModelFetchResult.Failed).reason)
        assertFalse(ModelDownloadPolicy.keepsPartialFile(result.reason))
        assertEquals(0L, storage.storedBytes(model))
        assertEquals(1, server.awaitExchanges(1).size)
    }

    @Test
    fun aResumedFileWithTheWrongHashIsDownloadedWholeOnceThenRejected() {
        val corrupted = content.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        server.script("/m", Answer.Serve(corrupted))
        val storage = storage(listOf(server.url("/m")))
        storage.legacyFile(model.expectedSha256).writeBytes(content.copyOf(LEGACY_BYTES.toInt()))
        storage.prepare(model)

        val result = storage.fetch(model, ModelDownloadOperationControl(), AtomicBoolean(false), RecordingEvents())

        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, (result as ModelFetchResult.Failed).reason)
        val exchanges = server.awaitExchanges(2)
        assertEquals(listOf(206, 200), exchanges.map { it.status })
        assertEquals(0L, storage.storedBytes(model))
    }

    @Test
    fun contentThatIsNotAModelFailsFastAndAnOldPartWithoutTheHeaderIsRefused() {
        val page = "<html>..".toByteArray(Charsets.US_ASCII) + ByteArray(4 * 1024 * 1024 - 8)
        val wrong = modelFor("page", page)
        server.script("/m", Answer.Serve(page, chunkDelayMillis = 5L))
        val storage = storage(listOf(server.url("/m")))

        val result = storage.fetch(wrong, ModelDownloadOperationControl(), AtomicBoolean(false), RecordingEvents())

        assertEquals(ModelDownloadFailureReason.INVALID_CONTENT, (result as ModelFetchResult.Failed).reason)
        assertTrue("Stopped long before the end", server.awaitExchanges(1)[0].sentBytes < page.size / 2)
        storage.discard(wrong)

        storage.legacyFile(wrong.expectedSha256).writeBytes(page.copyOf(LEGACY_BYTES.toInt()))
        val refused = assertThrows(ModelDownloadFailureException::class.java) { storage.prepare(wrong) }
        assertEquals(ModelDownloadFailureReason.INVALID_CONTENT, refused.reason)
        assertEquals(0L, storage.storedBytes(wrong))
    }

    @Test
    fun aPauseStopsTheTransferAndTheNextFetchContinuesItsPart() {
        val large = validContent(2 * 1024 * 1024)
        val largeModel = modelFor("large", large)
        server.script("/m", Answer.Serve(large, chunkDelayMillis = 10L), Answer.Serve(large))
        val storage = storage(listOf(server.url("/m")))
        val cancel = AtomicBoolean(false)
        val pausing = object : ModelFetchEvents {
            override fun onProgress(downloadedBytes: Long) {
                if (downloadedBytes >= CUT) cancel.set(true)
            }
        }

        assertEquals(ModelFetchResult.Cancelled, storage.fetch(largeModel, ModelDownloadOperationControl(), cancel, pausing))
        assertFalse("The library's transfer stopped before fetch returned", storage.store.isInUse(largeModel.expectedSha256))
        val kept = storage.storedBytes(largeModel)
        assertTrue(kept >= CUT && kept < large.size)
        assertEquals(kept, storage.partFile(largeModel).length())

        val ready = fetchReady(storage, RecordingEvents(), largeModel)
        // The paused exchange is recorded when the server notices the closed connection, which
        // can be after the resumed one.
        val resumed = server.awaitExchanges(2).single { it.status == 206 }
        assertEquals("bytes=$kept-", resumed.range)
        assertEquals(ModelTestHttpServer.ETAG, resumed.ifRange)
        assertEquals(large.size - kept, resumed.sentBytes)
        assertArrayEquals(large, ready.file.readBytes())
    }

    @Test
    fun aRedirectToAnUnapprovedHostIsRefusedWithoutRetrying() {
        server.script("/m", Answer.Redirect("http://localhost:${server.port}/elsewhere"))
        val storage = storage(listOf(server.url("/m")))

        val result = storage.fetch(model, ModelDownloadOperationControl(), AtomicBoolean(false), RecordingEvents())

        assertEquals(ModelDownloadFailureReason.HTTP_ERROR, (result as ModelFetchResult.Failed).reason)
        Thread.sleep(200L)
        assertEquals(listOf("/m"), server.paths())
    }

    @Test
    fun anApprovedRedirectTargetReceivesTheRangeToo() {
        server.script("/m", Answer.Redirect(server.url("/cdn")))
        server.script("/cdn", Answer.Serve(content))
        val storage = storage(listOf(server.url("/m")))
        storage.legacyFile(model.expectedSha256).writeBytes(content.copyOf(LEGACY_BYTES.toInt()))
        storage.prepare(model)

        val ready = fetchReady(storage, RecordingEvents())

        val exchanges = server.awaitExchanges(2)
        assertEquals(listOf("/m" to 302, "/cdn" to 206), exchanges.map { it.path to it.status })
        assertEquals(listOf("bytes=$LEGACY_BYTES-", "bytes=$LEGACY_BYTES-"), exchanges.map { it.range })
        assertArrayEquals(content, ready.file.readBytes())
    }

    @Test
    fun theRedirectLimitEndsWithTheRedirectAsAnHttpError() {
        server.script("/m", Answer.Redirect(server.url("/m")))
        val storage = storage(listOf(server.url("/m")))

        val result = storage.fetch(model, ModelDownloadOperationControl(), AtomicBoolean(false), RecordingEvents())

        assertEquals(ModelDownloadFailureReason.HTTP_ERROR, (result as ModelFetchResult.Failed).reason)
        assertEquals(ModelDownloadPolicy.MAXIMUM_REDIRECTS + 1, server.awaitExchanges(9).size)
    }

    @Test
    fun aColdStartKeepsOnlyTheNewestDownloadOfAModelThatIsNotImported() {
        val other = modelFor("other-model", validContent(128 * 1024))
        val imported = modelFor("imported-model", validContent(64 * 1024))
        val storage = storage(listOf(server.url("/m")))
        val now = System.currentTimeMillis()
        storage.legacyFile(other.expectedSha256).apply {
            writeBytes(validContent(128 * 1024).copyOf(30_000))
            setLastModified(now - 120_000L)
        }
        storage.legacyFile(model.expectedSha256).apply {
            writeBytes(content.copyOf(LEGACY_BYTES.toInt()))
            setLastModified(now - 60_000L)
        }
        val importedFile = storage.legacyFile(imported.expectedSha256).apply { writeBytes(validContent(1_000)) }
        val unknown = storage.legacyFile("f".repeat(64)).apply { writeBytes(validContent(1_000)) }

        val restored = storage.findResumable(listOf(model, other, imported), setOf(imported.expectedSha256))

        assertEquals(model to LEGACY_BYTES, restored)
        storage.retainOnly(model.expectedSha256)
        assertEquals(listOf(model.expectedSha256), storage.store.entries().map { it.key })
        assertEquals(0L, storage.storedBytes(other))
        assertFalse(importedFile.exists())
        assertFalse(unknown.exists())
        assertTrue(storage.legacyFiles().isEmpty())
        assertEquals(LEGACY_BYTES, storage.storedBytes(model))
    }

    @Test
    fun anOlderFileShorterThanTheStoredPartIsDropped() {
        val storage = storage(listOf(server.url("/m")))
        storage.legacyFile(model.expectedSha256).writeBytes(content.copyOf(LEGACY_BYTES.toInt()))
        assertEquals(DownloadStore.PartialImport.IMPORTED, storage.adoptLegacyFile(model))
        storage.legacyFile(model.expectedSha256).writeBytes(content.copyOf(1_000))

        assertEquals(DownloadStore.PartialImport.KEPT_EXISTING, storage.adoptLegacyFile(model))
        assertFalse(storage.legacyFile(model.expectedSha256).exists())
        assertEquals(LEGACY_BYTES, storage.storedBytes(model))
    }

    @Test
    fun libraryFailuresMapToTheReasonsTheCardShows() {
        fun reason(kind: DownloadFailureKind, message: String = "", expected: Long? = null, actual: Long? = null) =
            ModelDownloadStorage.reasonOf(
                DownloadFailure(kind, message, "https://huggingface.co/x", "huggingface.co", expectedSize = expected, actualSize = actual),
            )
        assertEquals(ModelDownloadFailureReason.HTTP_ERROR, reason(DownloadFailureKind.HTTP_STATUS))
        assertEquals(ModelDownloadFailureReason.HTTP_ERROR, reason(DownloadFailureKind.SECURITY))
        assertEquals(ModelDownloadFailureReason.NETWORK_UNAVAILABLE, reason(DownloadFailureKind.NETWORK, "SocketTimeoutException"))
        assertEquals(
            ModelDownloadFailureReason.INSUFFICIENT_STORAGE,
            reason(DownloadFailureKind.NETWORK, "IOException: write failed: ENOSPC (No space left on device)"),
        )
        assertEquals(ModelDownloadFailureReason.NETWORK_UNAVAILABLE, reason(DownloadFailureKind.SIZE_MISMATCH, expected = 10, actual = 4))
        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, reason(DownloadFailureKind.SIZE_MISMATCH, expected = 10, actual = 12))
        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, reason(DownloadFailureKind.CHECKSUM_MISMATCH))
        assertEquals(ModelDownloadFailureReason.UNKNOWN, reason(DownloadFailureKind.OTHER))
    }

    private fun storage(urls: List<String>) = ModelDownloadStorage(
        modelDirectory = modelDirectory,
        downloadDirectory = downloadDirectory,
        transport = LoopbackTransport(urls),
        retryDelaysMillis = longArrayOf(0L, 0L),
    )

    private fun fetchReady(
        storage: ModelDownloadStorage,
        events: ModelFetchEvents,
        target: RecommendedModel = model,
    ): org.autojs.plugin.downloadkit.DownloadOutcome.Ready {
        val result = storage.fetch(target, ModelDownloadOperationControl(), AtomicBoolean(false), events)
        val ready = (result as? ModelFetchResult.Ready)?.outcome ?: throw AssertionError("Not ready: $result")
        assertEquals(target.expectedSha256, ready.sha256)
        assertEquals(target.expectedSizeBytes, ready.sizeBytes)
        return ready
    }

    private fun assertMonotonic(values: List<Long>) {
        values.zipWithNext().forEach { (a, b) -> assertTrue("Progress went back: $values", b >= a) }
    }

    /** Plain HTTP to 127.0.0.1 only, so a redirect to "localhost" counts as another host. */
    private class LoopbackTransport(private val urls: List<String>) : ModelDownloadTransport {
        override fun urls(model: RecommendedModel): List<String> = urls

        override fun requireAllowed(uri: URI) {
            require(uri.scheme == "http" && uri.host == "127.0.0.1") { "Host is not approved" }
        }

        override fun openHop(url: URL): HttpURLConnection = url.openConnection() as HttpURLConnection
    }

    private class RecordingEvents : ModelFetchEvents {
        val progress = mutableListOf<Long>()
        var reconnects = 0
        var verifying = 0

        override fun onVerifying() {
            verifying += 1
        }

        override fun onProgress(downloadedBytes: Long) {
            progress += downloadedBytes
        }

        override fun onReconnecting() {
            reconnects += 1
        }
    }

    private companion object {
        const val CUT = 64L * 1024L
        const val LEGACY_BYTES = 100L * 1024L
        const val BASE = "https://huggingface.co/litert-community/test"
        const val REVISION = "0123456789abcdef0123456789abcdef01234567"

        fun validContent(size: Int): ByteArray =
            "LITERTLM".toByteArray(Charsets.US_ASCII) + ByteArray(size - 8) { (it * 31 + 7).toByte() }

        fun modelFor(id: String, bytes: ByteArray) = RecommendedModel(
            id = id,
            displayName = id,
            fileName = "$id.litertlm",
            sourceUrl = "$BASE/blob/$REVISION/$id.litertlm",
            downloadUrl = "$BASE/resolve/$REVISION/$id.litertlm?download=true",
            expectedSizeBytes = bytes.size.toLong(),
            expectedSha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) },
            license = "Apache-2.0",
        )
    }
}
