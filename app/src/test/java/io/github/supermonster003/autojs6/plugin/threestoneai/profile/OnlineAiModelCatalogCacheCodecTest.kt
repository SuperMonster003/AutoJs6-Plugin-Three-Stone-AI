package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineAiModelCatalogCacheCodecTest {
    @Test
    fun storesPayloadAndHttpValidatorInTheSameBoundedEnvelope() {
        val original = OnlineAiModelCatalogCacheRecord(
            payload = modelCatalogPayload(2),
            etag = "W/\"catalog-2\"",
            lastCheckedAtMillis = 1000L,
            lastAttemptAtMillis = 2000L,
            lastAttemptFailed = true,
            attemptId = "00000000-0000-4000-8000-000000000001",
        )
        val decoded = OnlineAiModelCatalogCacheCodec.decode(OnlineAiModelCatalogCacheCodec.encode(original))
        assertArrayEquals(original.payload, decoded.payload)
        assertEquals(original.copy(payload = null), decoded.copy(payload = null))
    }

    @Test
    fun canRememberFailureWithoutHavingAnyRemotePayload() {
        val record = OnlineAiModelCatalogCacheRecord(lastAttemptAtMillis = 1000L, lastAttemptFailed = true)
        val decoded = OnlineAiModelCatalogCacheCodec.decode(OnlineAiModelCatalogCacheCodec.encode(record))
        assertNull(decoded.payload)
        assertEquals(record, decoded)
        val empty = OnlineAiModelCatalogCacheCodec.decode(OnlineAiModelCatalogCacheCodec.encode(OnlineAiModelCatalogCacheRecord()))
        assertFalse(empty.lastAttemptFailed)
    }

    @Test
    fun rejectsTruncatedCorruptedVersionAndTrailingBytes() {
        val valid = OnlineAiModelCatalogCacheCodec.encode(OnlineAiModelCatalogCacheRecord(modelCatalogPayload()))
        for (bytes in listOf(byteArrayOf(), valid.copyOf(7), valid.copyOf(valid.size - 1), valid + 0,
            valid.clone().also { it[0] = 0 }, valid.clone().also { it[7] = 2 },
            ByteArray(OnlineAiModelCatalogCacheCodec.MAXIMUM_CACHE_BYTES + 1),
        )) {
            assertThrows(Exception::class.java) { OnlineAiModelCatalogCacheCodec.decode(bytes) }
        }
    }

    @Test
    fun rejectsInconsistentMetadataAndUnsafeValidators() {
        for (record in listOf(
            OnlineAiModelCatalogCacheRecord(etag = "\"orphan\""),
            OnlineAiModelCatalogCacheRecord(lastCheckedAtMillis = 1L),
            OnlineAiModelCatalogCacheRecord(lastAttemptAtMillis = -1L),
            OnlineAiModelCatalogCacheRecord(payload = byteArrayOf()),
            OnlineAiModelCatalogCacheRecord(modelCatalogPayload(), "bad\r\nInjected:header"),
            OnlineAiModelCatalogCacheRecord(modelCatalogPayload(), "x".repeat(1025)),
            OnlineAiModelCatalogCacheRecord(attemptId = "invalid"),
        )) {
            assertThrows(Exception::class.java) { OnlineAiModelCatalogCacheCodec.encode(record) }
        }
    }
}
