package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class OnlineAiModelCatalogRepositoryTest {
    @Test
    fun snapshotUsesBundledFallbackWithoutTouchingTransport() {
        val fixture = Fixture()
        assertFalse(fixture.repo.snapshot().isCached)
        assertEquals(1L, fixture.repo.snapshot().catalog.revision)
        assertEquals(0L, fixture.repo.snapshot().lastCheckedAtMillis)
        assertEquals(0, fixture.transport.calls)
        assertEquals(0, fixture.transport.accessChecks)
    }

    @Test
    fun successPersistsAcrossInstancesAndFreshnessExpiresAtTwentyFourHours() {
        val fixture = Fixture()
        assertEquals(OnlineAiModelCatalogRefreshResult.UPDATED, fixture.repo.refresh())
        val other = fixture.newRepository()
        assertTrue(other.snapshot().isCached)
        assertEquals(2L, other.snapshot().catalog.revision)
        assertEquals(fixture.now, other.snapshot().lastCheckedAtMillis)
        fixture.now += HOURS.toMillis(24) - 1L
        assertEquals(OnlineAiModelCatalogRefreshResult.SKIPPED_FRESH, other.refresh())
        assertEquals(1, fixture.transport.calls)
        fixture.now++
        assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, other.refresh())
        assertEquals(2, fixture.transport.calls)
    }

    @Test
    fun validEtagIsSentAndNotModifiedExtendsSuccessfulTtl() {
        val fixture = Fixture()
        fixture.repo.refresh()
        assertNull(fixture.transport.seenEtags.single())
        fixture.now += HOURS.toMillis(24)
        fixture.transport.reply = OnlineAiModelCatalogHttpResponse(304, etag = "\"v2-confirmed\"")
        assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, fixture.repo.refresh())
        assertEquals("\"v2\"", fixture.transport.seenEtags.last())
        assertEquals("\"v2-confirmed\"", fixture.storage.record?.etag)
        assertEquals(fixture.now, fixture.repo.snapshot().lastCheckedAtMillis)
        assertEquals(OnlineAiModelCatalogRefreshResult.SKIPPED_FRESH, fixture.repo.refresh())
    }

    @Test
    fun malformedCacheFallsBackAndCannotSupplyAnEtag() {
        val fixture = Fixture()
        fixture.storage.record = OnlineAiModelCatalogCacheRecord("broken".toByteArray(), "\"broken\"", fixture.now)
        assertFalse(fixture.repo.snapshot().isCached)
        fixture.repo.refresh()
        assertNull(fixture.transport.seenEtags.single())
        assertEquals(2L, fixture.repo.snapshot().catalog.revision)
        fixture.storage.readFailure = IOException("Corrupt envelope")
        assertFalse(fixture.newRepository().snapshot().isCached)
    }

    @Test
    fun apkUpgradeDiscardsOlderOrConflictingCacheAndItsFreshTtlAndEtag() {
        val fixture = Fixture()
        for (cached in listOf(modelCatalogPayload(2), modelCatalogPayload(3, "conflicting"))) {
            fixture.storage.record = OnlineAiModelCatalogCacheRecord(cached, "\"old-apk\"", fixture.now)
            val upgraded = OnlineAiModelCatalogRepository(modelCatalogPayload(3), fixture.storage, fixture.transport) { fixture.now }
            assertEquals(3L, upgraded.snapshot().catalog.revision)
            assertFalse(upgraded.snapshot().isCached)
            assertEquals(0L, upgraded.snapshot().lastCheckedAtMillis)
            fixture.transport.reply = OnlineAiModelCatalogHttpResponse(200, modelCatalogPayload(3), "\"current\"")
            assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, upgraded.refresh())
            assertNull(fixture.transport.seenEtags.last())
            assertTrue(upgraded.snapshot().isCached)
        }
    }

    @Test
    fun notModifiedWithoutValidConditionalCacheIsRejected() {
        val fixture = Fixture()
        fixture.transport.reply = OnlineAiModelCatalogHttpResponse(304)
        assertThrows(Exception::class.java) { fixture.repo.refresh() }
        assertFalse(fixture.repo.snapshot().isCached)
        fixture.storage.record = OnlineAiModelCatalogCacheRecord(modelCatalogPayload(2), null)
        assertThrows(Exception::class.java) { fixture.repo.refresh(force = true) }
        assertNull(fixture.transport.seenEtags.last())
    }

    @Test
    fun failedRequestKeepsValidCacheAndRetriesAfterOneHour() {
        val fixture = Fixture()
        fixture.repo.refresh()
        val original = fixture.storage.record!!.payload!!.clone()
        val checked = fixture.repo.snapshot().lastCheckedAtMillis
        fixture.now += HOURS.toMillis(24)
        fixture.transport.failure = IOException("offline")
        assertThrows(IOException::class.java) { fixture.repo.refresh() }
        assertArrayEquals(original, fixture.storage.record!!.payload)
        assertEquals(checked, fixture.repo.snapshot().lastCheckedAtMillis)
        fixture.now += HOURS.toMillis(1) - 1L
        assertEquals(OnlineAiModelCatalogRefreshResult.SKIPPED_BACKOFF, fixture.repo.refresh())
        fixture.now++
        fixture.transport.failure = null
        assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, fixture.repo.refresh())
    }

    @Test
    fun failuresBeforeFirstSuccessAlsoBackOffAndManualRefreshBypassesBothIntervals() {
        val fixture = Fixture()
        fixture.transport.failure = IOException("timeout")
        assertThrows(IOException::class.java) { fixture.repo.refresh() }
        assertEquals(OnlineAiModelCatalogRefreshResult.SKIPPED_BACKOFF, fixture.repo.refresh())
        fixture.transport.failure = null
        assertEquals(OnlineAiModelCatalogRefreshResult.UPDATED, fixture.repo.refresh(force = true))
        assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, fixture.repo.refresh(force = true))
        assertEquals(3, fixture.transport.calls)
    }

    @Test
    fun blockedNetworkDoesNotRecordBackoffAndWifiCanRetryImmediately() {
        val fixture = Fixture()
        fixture.transport.accessFailure = IOException("metered network disallowed")
        assertThrows(IOException::class.java) { fixture.repo.refresh() }
        assertNull(fixture.storage.record)
        fixture.transport.accessFailure = null
        assertEquals(OnlineAiModelCatalogRefreshResult.UPDATED, fixture.repo.refresh())
    }

    @Test
    fun rejectsHttpErrorsInvalidPayloadAndRevisionRollbackWithoutLosingCache() {
        val fixture = Fixture()
        fixture.repo.refresh()
        for (response in listOf(
            OnlineAiModelCatalogHttpResponse(503),
            OnlineAiModelCatalogHttpResponse(302),
            OnlineAiModelCatalogHttpResponse(200, "{}".toByteArray()),
            OnlineAiModelCatalogHttpResponse(200, modelCatalogPayload(1)),
            OnlineAiModelCatalogHttpResponse(200, modelCatalogPayload(2, "changed")),
            OnlineAiModelCatalogHttpResponse(200, null),
        )) {
            fixture.transport.reply = response
            assertThrows(Exception::class.java) { fixture.repo.refresh(force = true) }
            assertEquals(2L, fixture.repo.snapshot().catalog.revision)
            assertEquals("openai-model", fixture.repo.snapshot().catalog.defaultForProvider(OnlineAiProvider.OPENAI))
        }
    }

    @Test
    fun unchangedSemanticPayloadMayUseDifferentWhitespaceButNotDifferentRevisionContent() {
        val fixture = Fixture()
        fixture.transport.reply = OnlineAiModelCatalogHttpResponse(200, modelCatalogPayload() + "\n ".toByteArray())
        assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, fixture.repo.refresh())
        assertTrue(fixture.repo.snapshot().isCached)
        fixture.transport.reply = OnlineAiModelCatalogHttpResponse(200, modelCatalogPayload(1, "changed"))
        assertThrows(Exception::class.java) { fixture.repo.refresh(force = true) }
    }

    @Test
    fun requestDoesNotHoldStorageLockAndSlowOlderResponseCannotOverwriteNewerProcessResult() {
        val fixture = Fixture()
        fixture.transport.onFetch = {
            assertFalse(fixture.storage.locked)
            fixture.storage.withExclusiveAccess { access ->
                access.write(OnlineAiModelCatalogCacheRecord(modelCatalogPayload(3), "\"v3\"", fixture.now + 1L))
            }
        }
        assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, fixture.repo.refresh())
        assertEquals(3L, fixture.repo.snapshot().catalog.revision)
        assertEquals("\"v3\"", fixture.storage.record?.etag)
        assertEquals(fixture.now + 1L, fixture.repo.snapshot().lastCheckedAtMillis)
    }

    @Test
    fun notModifiedForOldEtagDoesNotChangeNewerConcurrentCache() {
        val fixture = Fixture()
        fixture.repo.refresh()
        fixture.transport.reply = OnlineAiModelCatalogHttpResponse(304, etag = "\"old-response\"")
        fixture.transport.onFetch = {
            fixture.storage.record = OnlineAiModelCatalogCacheRecord(modelCatalogPayload(3), "\"v3\"", fixture.now + 1L)
        }
        fixture.repo.refresh(force = true)
        assertEquals(3L, fixture.repo.snapshot().catalog.revision)
        assertEquals("\"v3\"", fixture.storage.record?.etag)
    }

    @Test
    fun cancellationCannotPublishLateResponseAndDoesNotPermanentlyDisableRefresh() {
        val fixture = Fixture()
        fixture.transport.onFetch = { fixture.repo.cancelRefresh() }
        assertThrows(CancellationException::class.java) { fixture.repo.refresh() }
        assertFalse(fixture.repo.snapshot().isCached)
        assertEquals(1, fixture.transport.cancellations)
        assertFalse(fixture.storage.record!!.lastAttemptFailed)
        fixture.transport.onFetch = null
        assertEquals(OnlineAiModelCatalogRefreshResult.UPDATED, fixture.repo.refresh())
    }

    @Test
    fun backwardsWallClockDoesNotMakeCacheFreshForever() {
        val fixture = Fixture()
        fixture.repo.refresh()
        fixture.now--
        assertEquals(OnlineAiModelCatalogRefreshResult.UNCHANGED, fixture.repo.refresh())
    }

    @Test
    fun successfulResponseWithoutSafeEtagClearsOldValidator() {
        val fixture = Fixture()
        fixture.repo.refresh()
        fixture.transport.reply = OnlineAiModelCatalogHttpResponse(200, modelCatalogPayload(3), "bad\r\nHeader")
        fixture.repo.refresh(force = true)
        assertNull(fixture.storage.record?.etag)
    }

    private class Fixture {
        var now = 100_000_000L
        val storage = MemoryCatalogStorage()
        val transport = FakeCatalogTransport()
        val repo = newRepository()
        fun newRepository() = OnlineAiModelCatalogRepository(modelCatalogPayload(), storage, transport) { now }
    }

    private class FakeCatalogTransport : OnlineAiModelCatalogTransport {
        var reply = OnlineAiModelCatalogHttpResponse(200, modelCatalogPayload(2), "\"v2\"")
        var failure: Exception? = null
        var accessFailure: Exception? = null
        var onFetch: (() -> Unit)? = null
        var calls = 0
        var accessChecks = 0
        var cancellations = 0
        val seenEtags = ArrayList<String?>()
        override fun requireAccess() {
            accessChecks++
            accessFailure?.let { throw it }
        }
        override fun fetch(etag: String?): OnlineAiModelCatalogHttpResponse {
            calls++
            seenEtags += etag
            onFetch?.invoke()
            failure?.let { throw it }
            return reply
        }
        override fun cancel() { cancellations++ }
    }

    private companion object { val HOURS = TimeUnit.HOURS }
}

internal class MemoryCatalogStorage : OnlineAiModelCatalogStorage, OnlineAiModelCatalogCacheAccess {
    var record: OnlineAiModelCatalogCacheRecord? = null
    var readFailure: Exception? = null
    var locked = false
    override fun <T> withExclusiveAccess(action: (OnlineAiModelCatalogCacheAccess) -> T): T = synchronized(this) {
        check(!locked)
        locked = true
        try { action(this) } finally { locked = false }
    }
    override fun read(): OnlineAiModelCatalogCacheRecord? {
        check(locked)
        readFailure?.let { throw it }
        return record
    }
    override fun write(record: OnlineAiModelCatalogCacheRecord) {
        check(locked)
        this.record = record.copy(payload = record.payload?.clone())
    }
}
