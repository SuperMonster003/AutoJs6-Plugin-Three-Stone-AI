package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialStore
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
        )
    }

    fun save(
        profile: OnlineAiProfile,
        credentialUpdate: OnlineAiCredentialUpdate = OnlineAiCredentialUpdate.Keep,
    ): ConfiguredOnlineAiProfile {
        val replacement = (credentialUpdate as? OnlineAiCredentialUpdate.Replace)?.value
        return try {
            val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
            repository.withTransaction { transaction ->
                val existing = transaction.find(normalized.profileId)
                val destinationChanged = existing != null &&
                    !OnlineAiProfileUrls.sameCredentialDestination(existing, normalized)
                if (destinationChanged && credentialUpdate === OnlineAiCredentialUpdate.Keep) {
                    throw OnlineAiCredentialReentryRequiredException()
                }

                if (
                    credentialUpdate === OnlineAiCredentialUpdate.Clear ||
                    credentialUpdate is OnlineAiCredentialUpdate.Replace && destinationChanged
                ) {
                    credentialStore.clear(normalized.profileId)
                }

                val saved = transaction.save(normalized).profile
                when (credentialUpdate) {
                    OnlineAiCredentialUpdate.Keep -> Unit
                    OnlineAiCredentialUpdate.Clear -> Unit
                    is OnlineAiCredentialUpdate.Replace -> credentialStore.put(
                        saved.profileId,
                        credentialUpdate.value,
                    )
                }
                ConfiguredOnlineAiProfile(
                    profile = saved,
                    configured = credentialStore.isConfigured(saved.profileId),
                )
            }
        } finally {
            replacement?.fill('\u0000')
        }
    }

    fun delete(profileId: String): Boolean = repository.withTransaction { transaction ->
        val normalizedId = OnlineAiProfilePolicy.canonicalProfileId(profileId)
        credentialStore.clear(normalizedId)
        transaction.delete(normalizedId).changed
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

    /**
     * Captures an exact metadata/credential pair while the profile transaction is locked, then
     * releases every storage lock before invoking a potentially long-running HTTPS operation.
     */
    private fun acquireCredential(
        profileId: String? = null,
        expectedProfile: OnlineAiProfile? = null,
    ): CredentialSnapshot {
        require((profileId == null) != (expectedProfile == null))
        var copiedCredential: ByteArray? = null
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
                current
            }
            CredentialSnapshot(profile, requireNotNull(copiedCredential)).also {
                copiedCredential = null
            }
        } finally {
            copiedCredential?.fill(0)
        }
    }

    private class CredentialSnapshot(
        val profile: OnlineAiProfile,
        val credential: ByteArray,
    ) : Closeable {
        override fun close() {
            credential.fill(0)
        }

        override fun toString(): String =
            "CredentialSnapshot(profileId=${profile.profileId}, credential=[REDACTED])"
    }
}
