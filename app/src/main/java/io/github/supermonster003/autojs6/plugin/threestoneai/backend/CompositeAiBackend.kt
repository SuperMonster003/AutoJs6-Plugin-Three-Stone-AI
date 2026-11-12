package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Deterministic union of independently implemented local and remote target catalogs. */
internal class CompositeAiBackend(
    backends: List<AiBackend>,
    private val preferredDefaultBackendId: String? = null,
    /** The user's one global default (roadmap 1.7.0 P5.3, D114); without it the merged default stands. */
    private val defaultTargetStore: DefaultTargetStore? = null,
) : AiBackend {
    private val backends = backends.toList()

    init {
        require(this.backends.isNotEmpty()) { "At least one AI backend is required" }
        require(this.backends.map(AiBackend::backendId).distinct().size == this.backends.size) {
            "AI backend IDs must be unique"
        }
        require(
            preferredDefaultBackendId == null ||
                this.backends.any { it.backendId == preferredDefaultBackendId },
        ) { "The preferred default AI backend must exist" }
    }

    override val backendId: String = BACKEND_ID

    override fun ownsTarget(targetId: String): Boolean = backends.any { it.ownsTarget(targetId) }

    override fun catalog(): AiTargetCatalog {
        val merged = AiTargetCatalogs.merge(
            catalogs = backends.map { backend -> backend.backendId to backend.catalog() },
            preferredDefaultSourceId = preferredDefaultBackendId,
        )
        val store = defaultTargetStore ?: return merged
        val stored = try {
            store.readOrMigrate { DefaultTargetPolicy.migrated(merged) }
        } catch (_: IOException) {
            null
        }
        return AiTargetCatalogs.withDefault(merged, DefaultTargetPolicy.effective(stored, merged))
    }

    override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
        val owners = backends.filter { backend -> backend.ownsTarget(request.targetId) }
        if (owners.isEmpty()) throw AiTargetUnavailableException(request.targetId)
        check(owners.size == 1) { "AI target ownership is ambiguous" }
        return owners.single().createSession(request)
    }

    companion object {
        const val BACKEND_ID = "unified"
    }
}

internal object AiTargetCatalogs {
    fun merge(
        catalogs: List<Pair<String, AiTargetCatalog>>,
        preferredDefaultSourceId: String? = null,
    ): AiTargetCatalog {
        require(catalogs.isNotEmpty()) { "At least one AI target catalog is required" }
        require(catalogs.map(Pair<String, AiTargetCatalog>::first).distinct().size == catalogs.size) {
            "AI target catalog source IDs must be unique"
        }
        require(preferredDefaultSourceId == null || catalogs.any { it.first == preferredDefaultSourceId }) {
            "The preferred default AI target catalog must exist"
        }
        val preferredDefault = preferredDefaultSourceId?.let { sourceId ->
            catalogs.single { it.first == sourceId }.second.defaultTargetId
        }
        return AiTargetCatalog(
            generation = generation(catalogs),
            defaultTargetId = preferredDefault
                ?: catalogs.firstNotNullOfOrNull { (_, catalog) -> catalog.defaultTargetId },
            targets = catalogs.flatMap { (_, catalog) -> catalog.targets },
        )
    }

    /** The same catalog with another default; the generation changes with it so listings notice. */
    fun withDefault(catalog: AiTargetCatalog, defaultTargetId: String?): AiTargetCatalog =
        if (defaultTargetId == catalog.defaultTargetId) {
            catalog
        } else {
            catalog.copy(
                generation = digest { output ->
                    output.writeLengthPrefixed(catalog.generation)
                    output.writeLengthPrefixed(DEFAULT_MARKER)
                    output.writeLengthPrefixed(defaultTargetId.orEmpty())
                },
                defaultTargetId = defaultTargetId,
            )
        }

    private fun generation(catalogs: List<Pair<String, AiTargetCatalog>>): String = digest { output ->
        output.writeInt(catalogs.size)
        catalogs.forEach { (sourceId, catalog) ->
            output.writeLengthPrefixed(sourceId)
            output.writeLengthPrefixed(catalog.generation)
        }
    }

    private fun digest(write: (DataOutputStream) -> Unit): String {
        val input = ByteArrayOutputStream()
        DataOutputStream(input).use(write)
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return "catalog.${digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }}"
    }

    private const val DEFAULT_MARKER = "default"

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
