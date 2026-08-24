package io.github.supermonster003.autojs6.plugin.threestoneai.profile

/** Cross-process transaction boundary for the non-secret profile document. */
internal interface OnlineAiProfileDocumentStorage {
    fun <T> withExclusiveAccess(action: (OnlineAiProfileDocumentAccess) -> T): T
}

internal interface OnlineAiProfileDocumentAccess {
    fun read(): ByteArray?

    /** Implementations must consume or copy [encodedDocument] before returning. */
    fun write(encodedDocument: ByteArray)
}

internal class OnlineAiProfileRepository(
    private val storage: OnlineAiProfileDocumentStorage,
) {
    fun snapshot(): OnlineAiProfileDocument = withTransaction(Transaction::snapshot)

    fun find(profileId: String): OnlineAiProfile? {
        val normalizedId = OnlineAiProfilePolicy.canonicalProfileId(profileId)
        return withTransaction { transaction -> transaction.find(normalizedId) }
    }

    fun save(profile: OnlineAiProfile): OnlineAiProfileUpdate =
        withTransaction { transaction -> transaction.save(profile) }

    fun delete(profileId: String): OnlineAiProfileDeletion =
        withTransaction { transaction -> transaction.delete(profileId) }

    /**
     * Keeps profile metadata stable while a registry coordinates its credential update. The
     * transaction object is invalidated before the storage lock is released.
     */
    fun <T> withTransaction(action: (Transaction) -> T): T =
        storage.withExclusiveAccess { access ->
            val transaction = Transaction(access)
            try {
                action(transaction)
            } finally {
                transaction.invalidate()
            }
        }

    internal class Transaction(
        private val access: OnlineAiProfileDocumentAccess,
    ) {
        private var valid = true
        private var document = access.read()?.let(OnlineAiProfileCodec::decode)
            ?: OnlineAiProfilePolicy.empty()

        fun snapshot(): OnlineAiProfileDocument {
            checkValid()
            return OnlineAiProfilePolicy.normalize(document)
        }

        fun find(profileId: String): OnlineAiProfile? {
            checkValid()
            val normalizedId = OnlineAiProfilePolicy.canonicalProfileId(profileId)
            return document.profiles.singleOrNull { it.profileId == normalizedId }
        }

        fun save(profile: OnlineAiProfile): OnlineAiProfileUpdate {
            checkValid()
            val update = OnlineAiProfilePolicy.upsert(document, profile)
            if (update.changed) {
                access.write(OnlineAiProfileCodec.encode(update.document))
                document = update.document
            }
            return update
        }

        fun delete(profileId: String): OnlineAiProfileDeletion {
            checkValid()
            val deletion = OnlineAiProfilePolicy.delete(document, profileId)
            if (deletion.changed) {
                access.write(OnlineAiProfileCodec.encode(deletion.document))
                document = deletion.document
            }
            return deletion
        }

        fun invalidate() {
            valid = false
        }

        private fun checkValid() {
            check(valid) { "Online AI profile transaction has ended" }
        }
    }
}
