package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

internal enum class OnlineAiProvider(
    val providerId: String,
    val protocol: OnlineAiProtocol,
) {
    OPENAI(
        providerId = "openai",
        protocol = OnlineAiProtocol.OPENAI_COMPATIBLE,
    ),
    ANTHROPIC(
        providerId = "anthropic",
        protocol = OnlineAiProtocol.ANTHROPIC_MESSAGES,
    ),
    GEMINI(
        providerId = "gemini",
        protocol = OnlineAiProtocol.GEMINI_GENERATE_CONTENT,
    ),
    DEEPSEEK(
        providerId = "deepseek",
        protocol = OnlineAiProtocol.OPENAI_COMPATIBLE,
    ),
    OPENROUTER(
        providerId = "openrouter",
        protocol = OnlineAiProtocol.OPENAI_COMPATIBLE,
    ),
    OPENAI_COMPATIBLE(
        providerId = "openai-compatible",
        protocol = OnlineAiProtocol.OPENAI_COMPATIBLE,
    ),
    ;

    companion object {
        fun fromProviderId(providerId: String): OnlineAiProvider = entries.singleOrNull {
            it.providerId == providerId
        } ?: throw IllegalArgumentException("Online AI provider is unsupported")
    }
}

internal enum class OnlineAiProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE_CONTENT,
}

/** Non-secret configuration for one remote inference destination. */
internal data class OnlineAiProfile(
    val profileId: String,
    val displayName: String,
    val provider: OnlineAiProvider,
    val baseUrl: String,
    val modelId: String,
) {
    val declaredHttpsOrigin: String
        get() = OnlineAiProfileUrls.origin(baseUrl)

    companion object {
        fun create(
            displayName: String,
            provider: OnlineAiProvider,
            baseUrl: String,
            modelId: String,
        ): OnlineAiProfile = OnlineAiProfilePolicy.normalizeProfile(
            OnlineAiProfile(
                profileId = UUID.randomUUID().toString(),
                displayName = displayName,
                provider = provider,
                baseUrl = baseUrl,
                modelId = modelId,
            ),
        )
    }
}

internal data class OnlineAiProfileDocument(
    val revision: Long,
    val profiles: List<OnlineAiProfile>,
    val defaultProfileId: String? = null,
    val allowMeteredNetwork: Boolean = false,
)

internal data class OnlineAiServiceSettings(
    val defaultProfileId: String?,
    val allowMeteredNetwork: Boolean,
)

internal data class OnlineAiProfileUpdate(
    val document: OnlineAiProfileDocument,
    val profile: OnlineAiProfile,
    val changed: Boolean,
)

internal data class OnlineAiProfileDeletion(
    val document: OnlineAiProfileDocument,
    val profile: OnlineAiProfile?,
    val changed: Boolean,
)

internal object OnlineAiProfilePolicy {
    const val SCHEMA = 2
    const val MAXIMUM_PROFILES = 100
    const val MAXIMUM_MODEL_ID_BYTES = 256

    fun empty(): OnlineAiProfileDocument = OnlineAiProfileDocument(
        revision = 1L,
        profiles = emptyList(),
        defaultProfileId = null,
        allowMeteredNetwork = false,
    )

    fun normalize(document: OnlineAiProfileDocument): OnlineAiProfileDocument {
        require(document.revision >= 1L) { "Online AI profile revision is invalid" }
        require(document.profiles.size <= MAXIMUM_PROFILES) { "There are too many online AI profiles" }
        val profiles = document.profiles.map(::normalizeProfile).sortedBy(OnlineAiProfile::profileId)
        require(profiles.map(OnlineAiProfile::profileId).distinct().size == profiles.size) {
            "Online AI profile IDs must be unique"
        }
        require(profiles.map { it.displayName.lowercase(Locale.ROOT) }.distinct().size == profiles.size) {
            "Online AI profile display names must be unique"
        }
        val defaultProfileId = document.defaultProfileId?.let(::canonicalProfileId)
        require(defaultProfileId == null || profiles.any { it.profileId == defaultProfileId }) {
            "The default online AI profile must exist"
        }
        return document.copy(
            profiles = profiles,
            defaultProfileId = defaultProfileId,
        )
    }

    fun normalizeProfile(profile: OnlineAiProfile): OnlineAiProfile {
        val profileId = canonicalProfileId(profile.profileId)
        val displayName = Normalizer.normalize(profile.displayName.trim(), Normalizer.Form.NFC)
        requireSafeText(displayName, AiCommonLimits.MAX_DISPLAY_NAME_BYTES, "Online AI profile display name")
        val modelId = profile.modelId.trim()
        requireSafeText(modelId, MAXIMUM_MODEL_ID_BYTES, "Online AI model ID")
        return profile.copy(
            profileId = profileId,
            displayName = displayName,
            baseUrl = OnlineAiProfileUrls.normalize(profile.baseUrl),
            modelId = modelId,
        )
    }

    fun upsert(
        document: OnlineAiProfileDocument,
        profile: OnlineAiProfile,
    ): OnlineAiProfileUpdate {
        val current = normalize(document)
        val candidate = normalizeProfile(profile)
        val existing = current.profiles.singleOrNull { it.profileId == candidate.profileId }
        if (existing == null) {
            require(current.profiles.size < MAXIMUM_PROFILES) { "Online AI profile storage is full" }
        }
        val proposed = normalize(
            current.copy(
                profiles = current.profiles.filterNot { it.profileId == candidate.profileId } + candidate,
            ),
        )
        if (proposed.profiles == current.profiles) {
            return OnlineAiProfileUpdate(current, candidate, changed = false)
        }
        return OnlineAiProfileUpdate(
            document = proposed.copy(revision = Math.addExact(current.revision, 1L)),
            profile = candidate,
            changed = true,
        )
    }

    fun delete(
        document: OnlineAiProfileDocument,
        profileId: String,
    ): OnlineAiProfileDeletion {
        val current = normalize(document)
        val normalizedId = canonicalProfileId(profileId)
        val existing = current.profiles.singleOrNull { it.profileId == normalizedId }
            ?: return OnlineAiProfileDeletion(current, null, changed = false)
        return OnlineAiProfileDeletion(
            document = normalize(
                current.copy(
                    revision = Math.addExact(current.revision, 1L),
                    profiles = current.profiles.filterNot { it.profileId == normalizedId },
                    defaultProfileId = current.defaultProfileId.takeUnless { it == normalizedId },
                ),
            ),
            profile = existing,
            changed = true,
        )
    }

    fun updateSettings(
        document: OnlineAiProfileDocument,
        settings: OnlineAiServiceSettings,
    ): OnlineAiProfileDocument {
        val current = normalize(document)
        val proposed = normalize(
            current.copy(
                defaultProfileId = settings.defaultProfileId,
                allowMeteredNetwork = settings.allowMeteredNetwork,
            ),
        )
        if (
            proposed.defaultProfileId == current.defaultProfileId &&
            proposed.allowMeteredNetwork == current.allowMeteredNetwork
        ) {
            return current
        }
        return proposed.copy(revision = Math.addExact(current.revision, 1L))
    }

    fun canonicalProfileId(profileId: String): String {
        val normalized = profileId.trim().lowercase(Locale.ROOT)
        val parsed = runCatching { UUID.fromString(normalized) }
            .getOrElse { throw IllegalArgumentException("Online AI profile ID must be a canonical UUID", it) }
        require(parsed.toString() == normalized) { "Online AI profile ID must be a canonical UUID" }
        return normalized
    }

    private fun requireSafeText(value: String, maximumBytes: Int, label: String) {
        require(
            value.none { character ->
                Character.isISOControl(character) || character == '\u2028' || character == '\u2029'
            },
        ) { "$label contains forbidden control characters" }
        AiValidation.requireUtf8(value, 1, maximumBytes, label)
    }
}

internal object OnlineAiProfileUrls {
    fun normalize(value: String): String {
        val input = value.trim()
        AiValidation.requireUtf8(input, 1, AiCommonLimits.MAX_ORIGIN_BYTES, "Online AI base URL")
        require(input.none(Character::isISOControl)) { "Online AI base URL contains control characters" }
        val parsed = runCatching { URI(input) }
            .getOrElse { throw IllegalArgumentException("Online AI base URL is invalid", it) }
        require(!parsed.isOpaque) { "Online AI base URL must be hierarchical" }
        require(parsed.scheme.equals("https", ignoreCase = true)) { "Online AI base URL must use HTTPS" }
        require(parsed.rawUserInfo == null) { "Online AI base URL must not contain user information" }
        require(parsed.rawQuery == null) { "Online AI base URL must not contain a query" }
        require(parsed.rawFragment == null) { "Online AI base URL must not contain a fragment" }
        val host = parsed.host?.lowercase(Locale.ROOT)?.removeSurrounding("[", "]")
        require(!host.isNullOrBlank()) { "Online AI base URL must include a valid host" }
        require(parsed.port == -1 || parsed.port in 1..65535) { "Online AI base URL port is invalid" }
        val port = parsed.port.takeUnless { it == 443 }
        val origin = buildOrigin(host, port)
        AiValidation.requireHttpsOrigin(origin)
        val normalizedPath = parsed.normalize().rawPath.orEmpty().trimEnd('/')
        require(normalizedPath.isEmpty() || normalizedPath.startsWith('/')) {
            "Online AI base URL path is invalid"
        }
        return URI("$origin$normalizedPath").toASCIIString()
    }

    fun origin(baseUrl: String): String {
        val parsed = URI(normalize(baseUrl))
        val host = requireNotNull(parsed.host).lowercase(Locale.ROOT).removeSurrounding("[", "]")
        return buildOrigin(host, parsed.port.takeUnless { it == 443 })
            .also(AiValidation::requireHttpsOrigin)
    }

    fun sameCredentialDestination(first: OnlineAiProfile, second: OnlineAiProfile): Boolean =
        first.provider == second.provider && origin(first.baseUrl) == origin(second.baseUrl)

    private fun buildOrigin(host: String, port: Int?): String {
        val authorityHost = if (':' in host) "[$host]" else host
        return "https://$authorityHost${port?.takeIf { it != -1 }?.let { ":$it" }.orEmpty()}"
    }
}
