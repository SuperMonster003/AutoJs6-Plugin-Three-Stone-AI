package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.content.pm.PackageManager as P
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** AVD-only component-state fixture; exact previous overrides are restored in finally. */
class LauncherIconMigrationTest {
    @Test fun oldExplicitChoicesAndMixedUpgradeStatesNormalizeToOneEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        fun snapshot() = LauncherIconMode.entries.associateWith { pm.getComponentEnabledSetting(it.component(context)) }
        fun set(states: Map<LauncherIconMode, Int>) {
            states.entries.sortedBy { if (LauncherIconStatePolicy.enabled(it.key, it.value)) 0 else 1 }.forEach { (mode, state) ->
                pm.setComponentEnabledSetting(mode.component(context), state, P.DONT_KILL_APP)
            }
        }
        fun one(mode: LauncherIconMode) {
            val entries = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
            assertEquals(1, entries.size)
            assertEquals(mode.component(context).className, entries.single().activityInfo.name)
            assertEquals(mode, LauncherIcons.current(context))
            val normalized = snapshot()
            LauncherIcons.normalize(context)
            assertEquals("Repeated normalization must be a no-op", normalized, snapshot())
        }
        val original = snapshot()
        val shortcutId = "launcher-migration-${java.util.UUID.randomUUID()}"
        val shortcuts = if (android.os.Build.VERSION.SDK_INT >= 25) context.getSystemService(android.content.pm.ShortcutManager::class.java) else null
        val scenario = androidx.test.core.app.ActivityScenario.launch<AppSettingsActivity>(Intent(context, AppSettingsActivity::class.java))
        synchronized(LauncherIcons) {
            try {
                val defaults = LauncherIconMode.entries.associateWith { P.COMPONENT_ENABLED_STATE_DEFAULT }
                set(defaults)
                LauncherIcons.normalize(context)
                one(LauncherIconMode.AUTO)
                set(defaults + (LauncherIconMode.DARK to P.COMPONENT_ENABLED_STATE_ENABLED))
                assertEquals("A new default must not hide the previous explicit choice", LauncherIconMode.DARK, LauncherIcons.current(context))
                LauncherIcons.normalize(context)
                one(LauncherIconMode.DARK)
                if (shortcuts != null) {
                    assertTrue(shortcuts.addDynamicShortcuts(listOf(android.content.pm.ShortcutInfo.Builder(context, shortcutId)
                        .setShortLabel("Launcher migration").setActivity(LauncherIconMode.DARK.component(context))
                        .setIntent(Intent(context, ChatActivity::class.java).setAction(Intent.ACTION_VIEW)).build())))
                    // The new Manifest default already exposes only Auto; ownership still needs repair.
                    set(defaults)
                    LauncherIcons.normalize(context)
                    one(LauncherIconMode.AUTO)
                    val migrated = shortcuts.dynamicShortcuts.singleOrNull { it.id == shortcutId }
                    if (migrated == null) {
                        // A package/activity rescan may remove unpinned dynamic items before app code
                        // can repair them. Record this platform boundary, do not claim preservation.
                        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                            putString("unpinnedDynamicAfterDefaultChange", "absent-from-dynamic-list")
                        })
                    } else {
                    assertTrue("Migrated shortcuts remain enabled", migrated.isEnabled)
                    assertEquals(LauncherIconMode.AUTO.component(context), migrated.activity)
                    assertEquals(ChatActivity::class.java.name, migrated.intent!!.component!!.className)
                    }
                }
                val disabled = LauncherIconMode.entries.associateWith { P.COMPONENT_ENABLED_STATE_DISABLED }
                set(disabled + (LauncherIconMode.TRANSPARENT to P.COMPONENT_ENABLED_STATE_ENABLED))
                LauncherIcons.normalize(context)
                one(LauncherIconMode.TRANSPARENT)
                set(disabled)
                LauncherIcons.normalize(context)
                one(LauncherIconMode.AUTO)
                set(defaults + (LauncherIconMode.LIGHT to P.COMPONENT_ENABLED_STATE_ENABLED) + (LauncherIconMode.AUTO to P.COMPONENT_ENABLED_STATE_ENABLED))
                LauncherIcons.normalize(context)
                one(LauncherIconMode.AUTO)
            } finally {
                shortcuts?.removeDynamicShortcuts(listOf(shortcutId))
                scenario.close()
                set(original)
            }
        }
    }
}
