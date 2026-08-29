package io.github.supermonster003.autojs6.plugin.threestoneai.profile

/**
 * Portable, bounded profile metadata. API keys are deliberately excluded: exported files remain
 * safe to inspect and share, while imported profiles require an explicit credential entry.
 */
internal object OnlineAiProfileTransferCodec {
    fun encode(snapshot: OnlineAiProfileRegistrySnapshot): ByteArray = OnlineAiProfileCodec.encode(
        OnlineAiProfileDocument(
            revision = 1L,
            profiles = snapshot.profiles.map(ConfiguredOnlineAiProfile::profile),
            defaultProfileId = snapshot.defaultProfileId,
            allowMeteredNetwork = snapshot.allowMeteredNetwork,
        ),
    )

    fun decode(bytes: ByteArray): OnlineAiProfileDocument = OnlineAiProfileCodec.decode(bytes)
}
