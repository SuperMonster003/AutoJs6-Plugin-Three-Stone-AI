package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import java.security.MessageDigest

internal data class ContextTokenCalibration(
    val tokensPerUtf8Byte: Double = ContextPolicy.INITIAL_TOKENS_PER_UTF8_BYTE,
    val sampleCount: Long = 0L,
) {
    init {
        require(
            tokensPerUtf8Byte.isFinite() &&
                tokensPerUtf8Byte in ContextPolicy.MINIMUM_TOKENS_PER_UTF8_BYTE..
                ContextPolicy.MAXIMUM_TOKENS_PER_UTF8_BYTE,
        )
        require(sampleCount >= 0L)
    }

    fun estimator(): ContextTokenEstimator = ContextTokenEstimator(tokensPerUtf8Byte)
}

internal data class ContextTokenCalibrationObservation(
    val utf8Bytes: Long,
    val messageCount: Int,
    val actualInputTokens: Long,
) {
    init {
        require(utf8Bytes >= 0L)
        require(messageCount >= 0)
        require(actualInputTokens >= 0L)
    }
}

/** Pure EMA policy; Android persistence is deliberately kept outside this object. */
internal object ContextTokenCalibrationPolicy {
    fun update(
        current: ContextTokenCalibration,
        observation: ContextTokenCalibrationObservation?,
        alpha: Double = ContextPolicy.CALIBRATION_EMA_ALPHA,
    ): ContextTokenCalibration {
        require(alpha.isFinite() && alpha > 0.0 && alpha <= 1.0)
        if (observation == null || observation.utf8Bytes == 0L) return current
        val roleTokens = observation.messageCount.toLong() *
            ContextPolicy.MESSAGE_ROLE_OVERHEAD_TOKENS.toLong()
        if (observation.actualInputTokens <= roleTokens) return current
        val observed = (observation.actualInputTokens - roleTokens).toDouble() /
            observation.utf8Bytes.toDouble()
        if (!observed.isFinite() || observed <= 0.0) return current
        val bounded = observed.coerceIn(
            ContextPolicy.MINIMUM_TOKENS_PER_UTF8_BYTE,
            ContextPolicy.MAXIMUM_TOKENS_PER_UTF8_BYTE,
        )
        val coefficient = (current.tokensPerUtf8Byte * (1.0 - alpha) + bounded * alpha).coerceIn(
            ContextPolicy.MINIMUM_TOKENS_PER_UTF8_BYTE,
            ContextPolicy.MAXIMUM_TOKENS_PER_UTF8_BYTE,
        )
        return ContextTokenCalibration(
            tokensPerUtf8Byte = coefficient,
            sampleCount = if (current.sampleCount == Long.MAX_VALUE) {
                Long.MAX_VALUE
            } else {
                current.sampleCount + 1L
            },
        )
    }
}

internal interface ContextTokenCalibrationPersistence {
    fun load(targetId: String): ContextTokenCalibration?
    fun save(targetId: String, calibration: ContextTokenCalibration)
}

/** Target-scoped calibration repository shared by UI telemetry and later context compilation. */
internal class ContextTokenCalibrationStore(
    private val persistence: ContextTokenCalibrationPersistence,
) {
    constructor(context: Context) : this(SharedPreferencesContextTokenCalibrationPersistence(context))

    fun current(targetId: String): ContextTokenCalibration {
        require(targetId.isNotBlank())
        return synchronized(lock) {
            persistence.load(targetId) ?: ContextTokenCalibration()
        }
    }

    fun record(
        targetId: String,
        observation: ContextTokenCalibrationObservation?,
    ): ContextTokenCalibration {
        require(targetId.isNotBlank())
        return synchronized(lock) {
            val current = persistence.load(targetId) ?: ContextTokenCalibration()
            val updated = ContextTokenCalibrationPolicy.update(current, observation)
            if (updated != current) persistence.save(targetId, updated)
            updated
        }
    }

    private val lock = Any()
}

private class SharedPreferencesContextTokenCalibrationPersistence(context: Context) :
    ContextTokenCalibrationPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(targetId: String): ContextTokenCalibration? {
        val suffix = targetKey(targetId)
        val coefficient = preferences.getString("$KEY_COEFFICIENT_PREFIX$suffix", null)
            ?.toDoubleOrNull()
            ?: return null
        val sampleCount = preferences.getLong("$KEY_SAMPLE_COUNT_PREFIX$suffix", 0L)
        return runCatching {
            ContextTokenCalibration(coefficient, sampleCount)
        }.getOrNull()
    }

    override fun save(targetId: String, calibration: ContextTokenCalibration) {
        val suffix = targetKey(targetId)
        preferences.edit()
            .putString("$KEY_COEFFICIENT_PREFIX$suffix", calibration.tokensPerUtf8Byte.toString())
            .putLong("$KEY_SAMPLE_COUNT_PREFIX$suffix", calibration.sampleCount)
            .apply()
    }

    private fun targetKey(targetId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(targetId.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private companion object {
        const val PREFERENCES_NAME = "context-token-calibration"
        const val KEY_COEFFICIENT_PREFIX = "tokens-per-byte."
        const val KEY_SAMPLE_COUNT_PREFIX = "sample-count."
    }
}
