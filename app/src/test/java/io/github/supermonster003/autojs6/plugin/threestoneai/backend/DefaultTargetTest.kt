package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One global default target: migration, explicit choice and fallback (roadmap 1.7.0 P5.3, D97, D114). */
class DefaultTargetTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val local = target("local:gemma", "local", AiTargetLocality.LOCAL)
    private val otherLocal = target("local:qwen", "local", AiTargetLocality.LOCAL)
    private val remote = target("profile:codex", "online", AiTargetLocality.REMOTE)
    private val otherRemote = target("profile:codex.model.0123456789abcdef01234567", "online", AiTargetLocality.REMOTE)

    @Test
    fun migrationUsesTheDefaultServiceWhenOnlyItIsSet() {
        val store = MemoryStore()
        val catalog = backend(store, localDefault = null, onlineDefault = remote.targetId).catalog()

        assertEquals(remote.targetId, catalog.defaultTargetId)
        assertEquals(remote.targetId, store.value)
    }

    @Test
    fun migrationUsesTheSelectedLocalModelWhenOnlyItIsSet() {
        val store = MemoryStore()
        val catalog = backend(store, localDefault = local.targetId, onlineDefault = null).catalog()

        assertEquals(local.targetId, catalog.defaultTargetId)
        assertEquals(local.targetId, store.value)
    }

    @Test
    fun migrationPrefersTheDefaultServiceOverTheSelectedLocalModel() {
        val store = MemoryStore()
        val catalog = backend(store, localDefault = local.targetId, onlineDefault = remote.targetId).catalog()

        assertEquals(remote.targetId, catalog.defaultTargetId)
        assertEquals(remote.targetId, store.value)
    }

    @Test
    fun migrationWithNeitherStoresNoChoiceAndKeepsTheAutomaticDefault() {
        val store = MemoryStore()
        val catalog = backend(store, localDefault = null, onlineDefault = null).catalog()

        assertNull(catalog.defaultTargetId)
        assertTrue(store.migrated)
        assertNull(store.value)
    }

    @Test
    fun migrationRunsOnceAndTheMigratedValueIsUnchangedByLaterSources() {
        val store = MemoryStore()
        backend(store, localDefault = local.targetId, onlineDefault = null).catalog()

        val later = backend(store, localDefault = otherLocal.targetId, onlineDefault = remote.targetId).catalog()

        assertEquals(1, store.migrations)
        assertEquals(local.targetId, later.defaultTargetId)
    }

    @Test
    fun explicitChoiceWinsAndChangesTheGeneration() {
        val store = MemoryStore()
        val automatic = backend(store, localDefault = local.targetId, onlineDefault = remote.targetId).catalog()

        store.write(otherRemote.targetId)
        val chosen = backend(store, localDefault = local.targetId, onlineDefault = remote.targetId).catalog()

        assertEquals(otherRemote.targetId, chosen.defaultTargetId)
        assertNotEquals(automatic.generation, chosen.generation)
        assertEquals(automatic.targets, chosen.targets)
    }

    @Test
    fun deletedDefaultFallsBackToTheAutomaticDefaultAndKeepsTheStoredChoice() {
        val store = MemoryStore()
        store.write(otherLocal.targetId)

        val catalog = backend(
            store,
            localDefault = local.targetId,
            onlineDefault = null,
            localTargets = listOf(local),
        ).catalog()

        assertEquals(local.targetId, catalog.defaultTargetId)
        assertEquals(otherLocal.targetId, store.value)
    }

    @Test
    fun deletedDefaultServiceFallsBackToTheSelectedLocalModel() {
        val store = MemoryStore()
        store.write(remote.targetId)

        val catalog = backend(store, localDefault = local.targetId, onlineDefault = null, onlineTargets = emptyList())
            .catalog()

        assertEquals(local.targetId, catalog.defaultTargetId)
    }

    @Test
    fun unavailableDefaultFallsBackUntilItIsAvailableAgain() {
        val store = MemoryStore()
        store.write(otherRemote.targetId)
        val withoutKey = otherRemote.copy(configured = false, available = false)

        val missing = backend(
            store,
            localDefault = local.targetId,
            onlineDefault = null,
            onlineTargets = listOf(remote, withoutKey),
        ).catalog()
        val restored = backend(store, localDefault = local.targetId, onlineDefault = null).catalog()

        assertEquals(local.targetId, missing.defaultTargetId)
        assertEquals(otherRemote.targetId, restored.defaultTargetId)
    }

    @Test
    fun unreadableStoreLeavesTheAutomaticDefaultAndTheMergedGeneration() {
        val failing = object : DefaultTargetStore {
            override fun readOrMigrate(migrated: () -> String?): String? = throw java.io.IOException("disk")
            override fun write(targetId: String?) = Unit
            override fun replace(mapping: Map<String, String>) = Unit
        }
        val plain = backend(null, localDefault = local.targetId, onlineDefault = null).catalog()

        val catalog = backend(failing, localDefault = local.targetId, onlineDefault = null).catalog()

        assertEquals(plain, catalog)
    }

    @Test
    fun withDefaultReturnsTheSameCatalogWhenNothingChanges() {
        val catalog = AiTargetCatalog("one", local.targetId, listOf(local, otherLocal))

        assertSame(catalog, AiTargetCatalogs.withDefault(catalog, local.targetId))
        assertEquals(
            AiTargetCatalogs.withDefault(catalog, otherLocal.targetId).generation,
            AiTargetCatalogs.withDefault(catalog, otherLocal.targetId).generation,
        )
        assertNotEquals(
            AiTargetCatalogs.withDefault(catalog, otherLocal.targetId).generation,
            AiTargetCatalogs.withDefault(catalog, null).generation,
        )
    }

    @Test
    fun fileStoreMigratesOnceAndKeepsAnExplicitEmptyChoice() {
        val directory = folder.newFolder("files")
        var calls = 0

        assertNull(fileStore(directory).readOrMigrate { calls += 1; null })
        assertNull(fileStore(directory).readOrMigrate { calls += 1; local.targetId })

        assertEquals(1, calls)
    }

    @Test
    fun fileStoreWriteReplacesTheValueForANewInstance() {
        val directory = folder.newFolder("files")
        fileStore(directory).readOrMigrate { local.targetId }

        fileStore(directory).write(otherRemote.targetId)

        assertEquals(otherRemote.targetId, fileStore(directory).readOrMigrate { error("already migrated") })
        assertFalse(File(directory, "ai-default-target/default-target.tmp").exists())
    }

    @Test
    fun fileStoreMigratesAgainWhenTheDocumentIsDamaged() {
        val directory = folder.newFolder("files")
        fileStore(directory).write(local.targetId)
        File(directory, "ai-default-target/default-target").writeText("v9\nlocal:gemma\n")

        assertEquals(remote.targetId, fileStore(directory).readOrMigrate { remote.targetId })
        assertEquals(remote.targetId, fileStore(directory).readOrMigrate { error("already migrated") })
    }

    @Test
    fun fileStoreRejectsIdsWithWhitespace() {
        val store = fileStore(folder.newFolder("files"))

        assertThrows(IllegalArgumentException::class.java) { store.write("local:a b") }
        assertThrows(IllegalArgumentException::class.java) { store.write("") }
    }

    @Test
    fun fileStoreReplaceFollowsAReorderedServiceOnlyForAStoredChoice() {
        val directory = folder.newFolder("files")
        val store = fileStore(directory)
        val mapping = mapOf(remote.targetId to otherRemote.targetId, otherRemote.targetId to remote.targetId)

        store.replace(mapping)
        assertEquals(local.targetId, store.readOrMigrate { local.targetId })

        store.write(remote.targetId)
        store.replace(mapping)
        assertEquals(otherRemote.targetId, fileStore(directory).readOrMigrate { error("already migrated") })

        store.replace(mapOf(local.targetId to otherLocal.targetId))
        assertEquals(otherRemote.targetId, store.readOrMigrate { error("already migrated") })
        assertThrows(IllegalArgumentException::class.java) { store.replace(mapOf(otherRemote.targetId to "a b")) }
    }

    private fun fileStore(directory: File) = FileDefaultTargetStore(directory) { source, target ->
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun backend(
        store: DefaultTargetStore?,
        localDefault: String?,
        onlineDefault: String?,
        localTargets: List<AiTarget> = listOf(local, otherLocal),
        onlineTargets: List<AiTarget> = listOf(remote, otherRemote),
    ) = CompositeAiBackend(
        backends = listOf(
            FakeBackend("local", AiTargetCatalog("local-1", localDefault, localTargets)),
            FakeBackend("online", AiTargetCatalog("online-${onlineTargets.size}", onlineDefault, onlineTargets)),
        ),
        preferredDefaultBackendId = "online",
        defaultTargetStore = store,
    )

    private class MemoryStore : DefaultTargetStore {
        var migrated = false
            private set
        var migrations = 0
            private set
        var value: String? = null
            private set

        override fun readOrMigrate(migrated: () -> String?): String? {
            if (!this.migrated) {
                migrations += 1
                write(migrated())
            }
            return value
        }

        override fun write(targetId: String?) {
            migrated = true
            value = targetId
        }

        override fun replace(mapping: Map<String, String>) {
            value?.let(mapping::get)?.let { value = it }
        }
    }

    private class FakeBackend(override val backendId: String, private val targetCatalog: AiTargetCatalog) : AiBackend {
        override fun ownsTarget(targetId: String) = targetCatalog.targets.any { it.targetId == targetId }
        override fun catalog() = targetCatalog
        override fun createSession(request: AiBackendSessionRequest): AiBackendSession = error("unused")
    }

    private fun target(targetId: String, backendId: String, locality: AiTargetLocality): AiTarget = AiTarget(
        targetId = targetId,
        backendId = backendId,
        providerId = if (locality == AiTargetLocality.LOCAL) "local-provider" else "remote-provider",
        profileId = if (locality == AiTargetLocality.LOCAL) null else "codex",
        modelId = targetId.substringAfterLast(':'),
        displayName = targetId,
        locality = locality,
        credentialMode = if (locality == AiTargetLocality.LOCAL) {
            AiTargetCredentialMode.NONE
        } else {
            AiTargetCredentialMode.PLUGIN_MANAGED
        },
        declaredHttpsOrigins = if (locality == AiTargetLocality.LOCAL) emptyList() else listOf("https://api.example.com"),
        configured = true,
        available = true,
        capabilities = AiTargetCapabilities(false, false, false, false, false, false),
        limits = AiTargetLimits(null, null),
        executionProfiles = emptyList(),
    )
}
