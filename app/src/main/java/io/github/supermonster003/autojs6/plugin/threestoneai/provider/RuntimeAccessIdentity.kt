package io.github.supermonster003.autojs6.plugin.threestoneai.provider

/** Updates keep consent; a different signer, UID or installation never inherits it. */
internal data class RuntimeAccessIdentity(
    val packageName: String,
    val uid: Int,
    val firstInstallTime: Long,
    val signers: List<String>,
) {
    fun accepts(current: RuntimeAccessIdentity?): Boolean = current != null && this == current &&
        packageName.isNotBlank() && uid >= 0 && firstInstallTime > 0 && signers.isNotEmpty()
}
