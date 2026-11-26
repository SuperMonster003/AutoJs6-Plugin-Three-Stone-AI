package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialUnavailableException
import java.io.Closeable
import java.util.Collections

internal sealed interface OnlineAiCredentialUpdate {
    data object Keep : OnlineAiCredentialUpdate
    data object Clear : OnlineAiCredentialUpdate

    /** Takes ownership of [value]; the registry clears it before returning or throwing. */
    class Replace private constructor(
        internal val value: CharArray,
    ) : OnlineAiCredentialUpdate {
        override fun toString(): String = "Replace([REDACTED])"

        companion object {
            fun takingOwnership(value: CharArray): Replace = Replace(value)
        }
    }
}

internal class OnlineAiCredentialReentryRequiredException : IllegalArgumentException(
    "Changing an online AI credential destination requires replacing or clearing its credential",
)

internal class OnlineAiProfileChangedException : IllegalStateException(
    "The online AI profile changed after the target was selected",
)

internal data class ConfiguredOnlineAiProfile(
    val profile: OnlineAiProfile,
    val configured: Boolean,
)

internal class OnlineAiProfileRegistrySnapshot(
    val revision: Long,
    profiles: List<ConfiguredOnlineAiProfile>,
    val defaultProfileId: String?,
    val allowMeteredNetwork: Boolean,
) {
    val profiles: List<ConfiguredOnlineAiProfile> = Collections.unmodifiableList(ArrayList(profiles))
}

/** Credential access bound to an exact non-secret profile snapshot. */
internal class OnlineAiProfileCredentialAccess internal constructor(
    private val registry: OnlineAiProfileRegistry,
    val profile: OnlineAiProfile,
) {
    fun <T> withCredential(action: (ByteArray) -> T): T =
        registry.withCredential(profile, action)

    /** The API Key and the secret header values of the bound profile, captured together (T72). */
    fun <T> withCredentials(action: (ByteArray, OnlineAiHeaderSecrets) -> T): T =
        registry.withCredentials(profile, action)

    override fun toString(): String =
        "OnlineAiProfileCredentialAccess(profileId=${profile.profileId}, credential=[REDACTED])"
}

/**
 * Coordinates non-secret metadata with profile-bound encrypted credentials. Mutations and
 * execution snapshots pair both stores while the profile transaction is locked; a captured
 * credential is then used only after every storage lock has been released.
 */
internal class OnlineAiProfileRegistry(
    private val repository: OnlineAiProfileRepository,
    private val credentialStore: AiCredentialStore,
) {
    fun snapshot(): OnlineAiProfileRegistrySnapshot = repository.withTransaction { transaction ->
        val document = transaction.snapshot()
        OnlineAiProfileRegistrySnapshot(
            revision = document.revision,
            profiles = document.profiles.map { profile ->
                ConfiguredOnlineAiProfile(
                    profile = profile,
                    configured = credentialStore.isConfigured(profile.profileId),
                )
            },
            defaultProfileId = document.defaultProfileId,
            allowMeteredNetwork = document.allowMeteredNetwork,
        )
    }

    fun settings(): OnlineAiServiceSettings = repository.settings()

    fun setDefaultProfile(profileId: String?): OnlineAiServiceSettings =
        repository.withTransaction { transaction ->
            val current = transaction.settings()
            val normalizedId = profileId?.let(OnlineAiProfilePolicy::canonicalProfileId)
            if (normalizedId != null) {
                require(transaction.find(normalizedId) != null) {
                    "The default online AI profile does not exist"
                }
                require(credentialStore.isConfigured(normalizedId)) {
                    "The default online AI profile must have a credential"
                }
            }
            val document = transaction.saveSettings(current.copy(defaultProfileId = normalizedId))
            OnlineAiServiceSettings(
                defaultProfileId = document.defaultProfileId,
                allowMeteredNetwork = document.allowMeteredNetwork,
            )
        }

    fun setAllowMeteredNetwork(allow: Boolean): OnlineAiServiceSettings =
        repository.withTransaction { transaction ->
            val document = transaction.saveSettings(
                transaction.settings().copy(allowMeteredNetwork = allow),
            )
            OnlineAiServiceSettings(
                defaultProfileId = document.defaultProfileId,
                allowMeteredNetwork = document.allowMeteredNetwork,
            )
        }

    /**
     * Saves [profile] with its API Key and secret header values. Secret header values are dropped
     * together with the API Key when the provider or host changes, and a new profile (a copy or an
     * import) starts with none; clearing only the API Key leaves them alone (D162, T72).
     */
    fun save(
        profile: OnlineAiProfile,
        credentialUpdate: OnlineAiCredentialUpdate = OnlineAiCredentialUpdate.Keep,
        headerSecretsUpdate: OnlineAiHeaderSecretsUpdate = OnlineAiHeaderSecretsUpdate.Keep,
    ): ConfiguredOnlineAiProfile {
        val replacement = (credentialUpdate as? OnlineAiCredentialUpdate.Replace)?.value
        val secretInput = (headerSecretsUpdate as? OnlineAiHeaderSecretsUpdate.Put)?.values
        var incoming: Map<String, ByteArray> = emptyMap()
        return try {
            val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
            if (secretInput != null) incoming = OnlineAiHeaderSecretValues.encode(normalized.headers, secretInput)
            repository.withTransaction { transaction ->
                val existing = transaction.find(normalized.profileId)
                val destinationChanged = existing != null &&
                    !OnlineAiProfileUrls.sameCredentialDestination(existing, normalized)
                if (destinationChanged && credentialUpdate === OnlineAiCredentialUpdate.Keep) {
                    throw OnlineAiCredentialReentryRequiredException()
                }

                val existingSecretKeys = existing?.let { OnlineAiHeaderPolicy.secretKeys(it.headers) }.orEmpty()
                val secretKeys = OnlineAiHeaderPolicy.secretKeys(normalized.headers)
                val resetSecrets = existing == null || destinationChanged ||
                    headerSecretsUpdate === OnlineAiHeaderSecretsUpdate.Clear
                // A record exists only while the stored profile declares secret headers (D177).
                val stored = if (!resetSecrets && existingSecretKeys.isNotEmpty() && secretKeys.isNotEmpty()) {
                    readHeaderSecrets(normalized.profileId, unreadableAsEmpty = true)
                } else {
                    emptyMap()
                }
                try {
                    val merged = LinkedHashMap<String, ByteArray>()
                    secretKeys.forEach { key -> (incoming[key] ?: stored[key])?.let { merged[key] = it } }
                    OnlineAiHeaderPolicy.requireValid(
                        normalized.headers,
                        secretValueBytes = merged.mapValues { (_, value) -> value.size },
                    )
                    val pruned = stored.keys.any { key -> key !in merged }
                    val clearSecrets = existingSecretKeys.isNotEmpty() &&
                        (resetSecrets || merged.isEmpty() && (pruned || secretKeys.isEmpty()))
                    val writeSecrets = merged.isNotEmpty() &&
                        (headerSecretsUpdate is OnlineAiHeaderSecretsUpdate.Put || pruned)

                    if (
                        credentialUpdate === OnlineAiCredentialUpdate.Clear ||
                        credentialUpdate is OnlineAiCredentialUpdate.Replace && destinationChanged
                    ) {
                        credentialStore.clear(normalized.profileId)
                    }
                    if (clearSecrets) {
                        credentialStore.clear(OnlineAiHeaderSecretRecord.recordId(normalized.profileId))
                    }

                    val saved = transaction.save(normalized).profile
                    when (credentialUpdate) {
                        OnlineAiCredentialUpdate.Keep -> Unit
                        OnlineAiCredentialUpdate.Clear -> {
                            val settings = transaction.settings()
                            if (settings.defaultProfileId == saved.profileId) {
                                transaction.saveSettings(settings.copy(defaultProfileId = null))
                            }
                        }
                        is OnlineAiCredentialUpdate.Replace -> credentialStore.put(
                            saved.profileId,
                            credentialUpdate.value,
                        )
                    }
                    if (writeSecrets) {
                        credentialStore.put(
                            OnlineAiHeaderSecretRecord.recordId(saved.profileId),
                            OnlineAiHeaderSecretRecord.encode(merged),
                        )
                    }
                    ConfiguredOnlineAiProfile(
                        profile = saved,
                        configured = credentialStore.isConfigured(saved.profileId),
                    )
                } finally {
                    stored.values.forEach { value -> value.fill(0) }
                }
            }
        } finally {
            replacement?.fill('\u0000')
            secretInput?.values?.forEach { value -> value.fill('\u0000') }
            incoming.values.forEach { value -> value.fill(0) }
        }
    }

    fun delete(profileId: String): Boolean = repository.withTransaction { transaction ->
        val normalizedId = OnlineAiProfilePolicy.canonicalProfileId(profileId)
        val existing = transaction.find(normalizedId)
        credentialStore.clear(normalizedId)
        if (existing != null && OnlineAiHeaderPolicy.secretKeys(existing.headers).isNotEmpty()) {
            credentialStore.clear(OnlineAiHeaderSecretRecord.recordId(normalizedId))
        }
        transaction.delete(normalizedId).changed
    }

    /**
     * Lower-case names of the secret headers of [profileId] that have a stored value, for the
     * editor's "set / not set" state (P12). An unreadable record counts as not set, like
     * [AiCredentialStore.isConfigured]; values never leave the registry here.
     */
    fun configuredHeaderSecrets(profileId: String): Set<String> = repository.withTransaction { transaction ->
        val profile = transaction.find(OnlineAiProfilePolicy.canonicalProfileId(profileId))
            ?: return@withTransaction emptySet()
        val secretKeys = OnlineAiHeaderPolicy.secretKeys(profile.headers)
        if (secretKeys.isEmpty()) return@withTransaction emptySet()
        val stored = readHeaderSecrets(profile.profileId, unreadableAsEmpty = true)
        try {
            secretKeys.filterTo(LinkedHashSet()) { key -> key in stored }
        } finally {
            stored.values.forEach { value -> value.fill(0) }
        }
    }

    fun bindCredential(profile: OnlineAiProfile): OnlineAiProfileCredentialAccess =
        OnlineAiProfileCredentialAccess(
            registry = this,
            profile = OnlineAiProfilePolicy.normalizeProfile(profile),
        )

    fun <T> withCredential(
        profileId: String,
        action: (OnlineAiProfile, ByteArray) -> T,
    ): T = acquireCredential(profileId = profileId).use { snapshot ->
        action(snapshot.profile, snapshot.credential)
    }

    internal fun <T> withCredential(
        expectedProfile: OnlineAiProfile,
        action: (ByteArray) -> T,
    ): T = acquireCredential(expectedProfile = expectedProfile).use { snapshot ->
        action(snapshot.credential)
    }

    internal fun <T> withCredentials(
        expectedProfile: OnlineAiProfile,
        action: (ByteArray, OnlineAiHeaderSecrets) -> T,
    ): T = acquireCredential(expectedProfile = expectedProfile, includeHeaderSecrets = true).use { snapshot ->
        action(snapshot.credential, snapshot.headerSecrets)
    }

    /**
     * Captures an exact metadata/credential pair while the profile transaction is locked, then
     * releases every storage lock before invoking a potentially long-running HTTPS operation.
     * Secret header values come from the same transaction; only headers that are secret in the
     * captured profile are included, and an unreadable record fails like an unreadable API Key.
     */
    private fun acquireCredential(
        profileId: String? = null,
        expectedProfile: OnlineAiProfile? = null,
        includeHeaderSecrets: Boolean = false,
    ): CredentialSnapshot {
        require((profileId == null) != (expectedProfile == null))
        var copiedCredential: ByteArray? = null
        var copiedSecrets: Map<String, ByteArray> = emptyMap()
        return try {
            val profile = repository.withTransaction { transaction ->
                val current = if (expectedProfile == null) {
                    transaction.find(requireNotNull(profileId))
                        ?: throw IllegalArgumentException("Online AI profile does not exist")
                } else {
                    val expected = OnlineAiProfilePolicy.normalizeProfile(expectedProfile)
                    transaction.find(expected.profileId)
                        ?.takeIf { it == expected }
                        ?: throw OnlineAiProfileChangedException()
                }
                credentialStore.withCredential(current.profileId) { credential ->
                    copiedCredential = credential.copyOf()
                }
                val secretKeys = OnlineAiHeaderPolicy.secretKeys(current.headers)
                if (includeHeaderSecrets && secretKeys.isNotEmpty()) {
                    val stored = readHeaderSecrets(current.profileId, unreadableAsEmpty = false)
                    val selected = LinkedHashMap<String, ByteArray>()
                    stored.forEach { (key, value) -> if (key in secretKeys) selected[key] = value else value.fill(0) }
                    copiedSecrets = selected
                }
                current
            }
            CredentialSnapshot(
                profile = profile,
                credential = requireNotNull(copiedCredential),
                headerSecrets = OnlineAiHeaderSecrets.takingOwnership(copiedSecrets),
            ).also {
                copiedCredential = null
                copiedSecrets = emptyMap()
            }
        } finally {
            copiedCredential?.fill(0)
            copiedSecrets.values.forEach { value -> value.fill(0) }
        }
    }

    /**
     * Decrypts the secret header record of [profileId]; the caller owns the returned arrays. A
     * missing record is empty; an unreadable one is empty when [unreadableAsEmpty], otherwise it
     * throws [AiCredentialUnavailableException].
     */
    private fun readHeaderSecrets(profileId: String, unreadableAsEmpty: Boolean): Map<String, ByteArray> = try {
        credentialStore.withCredential(OnlineAiHeaderSecretRecord.recordId(profileId)) { plaintext ->
            try {
                OnlineAiHeaderSecretRecord.decode(plaintext)
            } catch (_: IllegalArgumentException) {
                throw AiCredentialUnavailableException(AiCredentialFailureReason.UNAVAILABLE)
            }
        }
    } catch (error: AiCredentialUnavailableException) {
        when {
            error.reason == AiCredentialFailureReason.NOT_CONFIGURED -> emptyMap()
            unreadableAsEmpty -> emptyMap()
            else -> throw error
        }
    }

    private class CredentialSnapshot(
        val profile: OnlineAiProfile,
        val credential: ByteArray,
        val headerSecrets: OnlineAiHeaderSecrets,
    ) : Closeable {
        override fun close() {
            credential.fill(0)
            headerSecrets.close()
        }

        override fun toString(): String =
            "CredentialSnapshot(profileId=${profile.profileId}, credential=[REDACTED], headerSecrets=[REDACTED])"
    }
}
