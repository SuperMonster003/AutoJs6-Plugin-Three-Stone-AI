package io.github.supermonster003.autojs6.plugin.ai.text.model

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal data class PendingModelTransaction(
    val transactionId: String,
    val destinationFileName: String,
    val sha256: String,
)

internal enum class PendingModelRecoveryDecision {
    DISCARD_MARKER,
    DELETE_UNPUBLISHED_DESTINATION,
    RETAIN_PUBLISHED_DESTINATION,
}

internal object PendingModelTransactionPolicy {
    const val MAXIMUM_MARKER_BYTES = 512
    private const val SCHEMA = 1
    private val TRANSACTION_ID = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    )
    private val SHA_256 = Regex("^[0-9a-f]{64}$")
    private val REQUIRED_KEYS = setOf(
        "schema",
        "transactionId",
        "createdDestination",
        "destinationFileName",
        "sha256",
    )

    fun create(transactionId: String, sha256: String): PendingModelTransaction {
        require(TRANSACTION_ID.matches(transactionId)) { "Invalid model transaction ID" }
        require(SHA_256.matches(sha256)) { "Invalid model transaction digest" }
        return PendingModelTransaction(
            transactionId = transactionId,
            destinationFileName = destinationFileName(sha256),
            sha256 = sha256,
        )
    }

    fun encode(marker: PendingModelTransaction): ByteArray {
        requireValid(marker)
        val json = """{"schema":$SCHEMA,"transactionId":"${marker.transactionId}","createdDestination":true,"destinationFileName":"${marker.destinationFileName}","sha256":"${marker.sha256}"}"""
        return json.toByteArray(Charsets.UTF_8).also {
            require(it.size in 1..MAXIMUM_MARKER_BYTES) { "Model transaction marker is too large" }
        }
    }

    fun decode(bytes: ByteArray): PendingModelTransaction {
        require(bytes.size in 1..MAXIMUM_MARKER_BYTES) { "Model transaction marker size is invalid" }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        var schema: Int? = null
        var transactionId: String? = null
        var createdDestination: Boolean? = null
        var destinationFileName: String? = null
        var sha256: String? = null
        val keys = linkedSetOf<String>()
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Model transaction marker must be an object" }
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in REQUIRED_KEYS && keys.add(name)) { "Model transaction marker keys are invalid" }
                when (name) {
                    "schema" -> {
                        require(reader.peek() == JsonToken.NUMBER) { "Model transaction schema is invalid" }
                        val raw = reader.nextString()
                        require(raw == SCHEMA.toString()) { "Model transaction schema is unsupported" }
                        schema = SCHEMA
                    }
                    "transactionId" -> transactionId = reader.nextStrictString()
                    "createdDestination" -> {
                        require(reader.peek() == JsonToken.BOOLEAN) { "Model transaction creation flag is invalid" }
                        createdDestination = reader.nextBoolean()
                    }
                    "destinationFileName" -> destinationFileName = reader.nextStrictString()
                    "sha256" -> sha256 = reader.nextStrictString()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Model transaction marker has trailing data" }
        }
        require(keys == REQUIRED_KEYS && schema == SCHEMA && createdDestination == true) {
            "Model transaction marker is incomplete"
        }
        return PendingModelTransaction(
            transactionId = requireNotNull(transactionId),
            destinationFileName = requireNotNull(destinationFileName),
            sha256 = requireNotNull(sha256),
        ).also(::requireValid)
    }

    fun decideRecovery(
        marker: PendingModelTransaction,
        currentFileName: String?,
        destinationExists: Boolean,
    ): PendingModelRecoveryDecision {
        requireValid(marker)
        if (!destinationExists) return PendingModelRecoveryDecision.DISCARD_MARKER
        return if (currentFileName == marker.destinationFileName) {
            PendingModelRecoveryDecision.RETAIN_PUBLISHED_DESTINATION
        } else {
            PendingModelRecoveryDecision.DELETE_UNPUBLISHED_DESTINATION
        }
    }

    fun shouldCreateMarker(destinationExistsBeforeImport: Boolean): Boolean =
        !destinationExistsBeforeImport

    fun destinationFileName(sha256: String): String {
        require(SHA_256.matches(sha256)) { "Invalid model transaction digest" }
        return "model-$sha256.litertlm"
    }

    private fun requireValid(marker: PendingModelTransaction) {
        require(TRANSACTION_ID.matches(marker.transactionId)) { "Invalid model transaction ID" }
        require(SHA_256.matches(marker.sha256)) { "Invalid model transaction digest" }
        require(marker.destinationFileName == destinationFileName(marker.sha256)) {
            "Model transaction destination does not match its digest"
        }
    }

    private fun JsonReader.nextStrictString(): String {
        require(peek() == JsonToken.STRING) { "Model transaction string field is invalid" }
        return nextString()
    }
}
