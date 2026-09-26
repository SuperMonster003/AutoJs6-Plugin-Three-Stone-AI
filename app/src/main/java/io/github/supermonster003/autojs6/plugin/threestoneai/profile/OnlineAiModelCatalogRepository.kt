package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal data class OnlineAiModelCatalogCacheRecord(
    val payload: ByteArray? = null,
    val etag: String? = null,
    val lastCheckedAtMillis: Long = 0L,
    val lastAttemptAtMillis: Long = 0L,
    val lastAttemptFailed: Boolean = false,
    val attemptId: String? = null,
)

internal interface OnlineAiModelCatalogStorage {
    fun <T> withExclusiveAccess(action: (OnlineAiModelCatalogCacheAccess) -> T): T
}

internal interface OnlineAiModelCatalogCacheAccess {
    fun read(): OnlineAiModelCatalogCacheRecord?
    fun write(record: OnlineAiModelCatalogCacheRecord)
}

internal data class OnlineAiModelCatalogHttpResponse(
    val statusCode: Int,
    val payload: ByteArray? = null,
    val etag: String? = null,
)

internal interface OnlineAiModelCatalogTransport {
    /** A rejected network policy must not be persisted as a failed server request. */
    fun requireAccess() = Unit
    fun fetch(etag: String?): OnlineAiModelCatalogHttpResponse
    fun cancel() = Unit
}

/** Synchronous core. Only refresh performs I/O over the network; callers choose their executor. */
internal class OnlineAiModelCatalogRepository(
    bundledPayload: ByteArray,
    private val storage: OnlineAiModelCatalogStorage,
    private val transport: OnlineAiModelCatalogTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val bundled = OnlineAiModelCatalogCodec.decode(bundledPayload)
    private val bundledCanonical = OnlineAiModelCatalogCodec.encode(bundled)
    private val cancellationEpoch = AtomicLong()

    fun snapshot(): OnlineAiModelCatalogSnapshot = runCatching {
        storage.withExclusiveAccess { access -> snapshot(readState(access)) }
    }.getOrElse { OnlineAiModelCatalogSnapshot(bundled, false, 0L) }

    fun cancelRefresh() {
        cancellationEpoch.incrementAndGet()
        transport.cancel()
    }

    fun refresh(force: Boolean = false): OnlineAiModelCatalogRefreshResult {
        // An Activity may cancel a queued refresh before it acquires the shared refresh monitor.
        val epoch = cancellationEpoch.get()
        return synchronized(REFRESH_LOCK) {
            requireNotCancelled(epoch)
            val now = now()
            val initial = storage.withExclusiveAccess(::readState)
            if (!force) {
                if (initial.catalog != null && isRecent(initial.record.lastCheckedAtMillis, now, SUCCESS_TTL)) {
                    return@synchronized OnlineAiModelCatalogRefreshResult.SKIPPED_FRESH
                }
                if (initial.record.lastAttemptFailed && isRecent(initial.record.lastAttemptAtMillis, now, FAILURE_BACKOFF)) {
                    return@synchronized OnlineAiModelCatalogRefreshResult.SKIPPED_BACKOFF
                }
            }
            transport.requireAccess()
            requireNotCancelled(epoch)
            val attemptId = UUID.randomUUID().toString()
            val requested = storage.withExclusiveAccess { access ->
                val state = readState(access)
                requireNotCancelled(epoch)
                access.write(state.record.copy(lastAttemptAtMillis = now, lastAttemptFailed = true, attemptId = attemptId))
                state
            }
            try {
                requireNotCancelled(epoch)
                val response = transport.fetch(requested.record.etag.takeIf { requested.catalog != null })
                requireNotCancelled(epoch)
                when (response.statusCode) {
                    200 -> publish(response, requested, epoch)
                    304 -> revalidate(response, requested, epoch)
                    else -> throw IOException("Model catalog service returned HTTP ${response.statusCode}")
                }
            } catch (error: Exception) {
                // Cancellation is an abandoned UI task, not a one-hour network failure.
                if (cancelled(epoch) || error is CancellationException) {
                    runCatching {
                        storage.withExclusiveAccess { access ->
                            val latest = readState(access)
                            if (latest.record.attemptId == attemptId) {
                                access.write(latest.record.copy(lastAttemptAtMillis = 0L, lastAttemptFailed = false, attemptId = null))
                            }
                        }
                    }.exceptionOrNull()?.let(error::addSuppressed)
                }
                throw error
            }
        }
    }

    private fun publish(
        response: OnlineAiModelCatalogHttpResponse,
        requested: State,
        epoch: Long,
    ): OnlineAiModelCatalogRefreshResult {
        val candidate = OnlineAiModelCatalogCodec.decode(requireNotNull(response.payload) { "Model catalog response is empty" })
        val canonical = OnlineAiModelCatalogCodec.encode(candidate)
        requireNotCancelled(epoch)
        return storage.withExclusiveAccess { access ->
            val latest = readState(access)
            val current = latest.catalog ?: bundled
            requireNotCancelled(epoch)
            val requestedCatalog = requested.catalog ?: bundled
            // A newer response from another process wins even when this request finishes later.
            if (current.revision > requestedCatalog.revision && candidate.revision < current.revision) {
                return@withExclusiveAccess OnlineAiModelCatalogRefreshResult.UNCHANGED
            }
            require(candidate.revision >= current.revision && candidate.revision >= bundled.revision) {
                "Model catalog revision went backwards"
            }
            if (candidate.revision == current.revision) {
                require(canonical.contentEquals(OnlineAiModelCatalogCodec.encode(current))) {
                    "Model catalog content changed without a new revision"
                }
            }
            if (candidate.revision == bundled.revision) {
                require(canonical.contentEquals(bundledCanonical)) {
                    "Model catalog conflicts with the bundled revision"
                }
            }
            val checkedAt = now()
            requireNotCancelled(epoch)
            access.write(OnlineAiModelCatalogCacheRecord(
                payload = canonical,
                etag = validOnlineAiModelCatalogEtag(response.etag),
                lastCheckedAtMillis = checkedAt,
                lastAttemptAtMillis = checkedAt,
                lastAttemptFailed = false,
            ))
            if (candidate.revision > current.revision) OnlineAiModelCatalogRefreshResult.UPDATED
            else OnlineAiModelCatalogRefreshResult.UNCHANGED
        }
    }

    private fun revalidate(
        response: OnlineAiModelCatalogHttpResponse,
        requested: State,
        epoch: Long,
    ): OnlineAiModelCatalogRefreshResult {
        require(requested.catalog != null && requested.record.etag != null) {
            "Model catalog returned 304 without a valid conditional cache"
        }
        return storage.withExclusiveAccess { access ->
            val latest = readState(access)
            requireNotCancelled(epoch)
            require(latest.catalog != null) { "Model catalog cache disappeared during revalidation" }
            val unchanged = OnlineAiModelCatalogCodec.encode(latest.catalog)
                .contentEquals(OnlineAiModelCatalogCodec.encode(requested.catalog))
            if (unchanged) {
                val checkedAt = now()
                requireNotCancelled(epoch)
                access.write(latest.record.copy(
                    etag = validOnlineAiModelCatalogEtag(response.etag) ?: requested.record.etag,
                    lastCheckedAtMillis = checkedAt,
                    lastAttemptAtMillis = checkedAt,
                    lastAttemptFailed = false,
                    attemptId = null,
                ))
            }
            OnlineAiModelCatalogRefreshResult.UNCHANGED
        }
    }

    private fun readState(access: OnlineAiModelCatalogCacheAccess): State {
        val record = runCatching(access::read).getOrNull() ?: return State(OnlineAiModelCatalogCacheRecord(), null)
        if (record.lastCheckedAtMillis < 0L || record.lastAttemptAtMillis < 0L) return State(OnlineAiModelCatalogCacheRecord(), null)
        val catalog = record.payload?.let { runCatching { OnlineAiModelCatalogCodec.decode(it) }.getOrNull() }
        if (record.payload != null && catalog == null) return State(OnlineAiModelCatalogCacheRecord(), null)
        // An APK update may ship a newer baseline while the old cache still has a fresh TTL.
        if (catalog != null && (catalog.revision < bundled.revision ||
                (catalog.revision == bundled.revision && !OnlineAiModelCatalogCodec.encode(catalog).contentEquals(bundledCanonical)))) {
            return State(OnlineAiModelCatalogCacheRecord(), null)
        }
        return State(record.copy(
            etag = if (catalog == null) null else validOnlineAiModelCatalogEtag(record.etag),
            lastCheckedAtMillis = if (catalog == null) 0L else record.lastCheckedAtMillis,
        ), catalog)
    }

    private fun snapshot(state: State) = OnlineAiModelCatalogSnapshot(
        state.catalog ?: bundled,
        state.catalog != null,
        state.record.lastCheckedAtMillis,
    )

    private fun now(): Long = clock().also { require(it > 0L) { "Model catalog clock is invalid" } }
    private fun cancelled(epoch: Long): Boolean = cancellationEpoch.get() != epoch || Thread.currentThread().isInterrupted
    private fun requireNotCancelled(epoch: Long) {
        if (cancelled(epoch)) throw CancellationException("Model catalog refresh was cancelled")
    }

    private data class State(val record: OnlineAiModelCatalogCacheRecord, val catalog: OnlineAiModelCatalog?)

    private companion object {
        val REFRESH_LOCK = Any()
        val SUCCESS_TTL = TimeUnit.HOURS.toMillis(24)
        val FAILURE_BACKOFF = TimeUnit.HOURS.toMillis(1)
        fun isRecent(timestamp: Long, now: Long, interval: Long): Boolean =
            timestamp > 0L && now >= timestamp && now - timestamp < interval
    }
}

internal fun validOnlineAiModelCatalogEtag(value: String?): String? = value?.takeIf {
    it.length in 1..1024 && it.all { character -> character.code in 0x21..0x7e }
}
