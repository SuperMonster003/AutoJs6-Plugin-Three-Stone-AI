package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Process
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Run on AVDs: temporarily switches launcher aliases, then restores their exact previous states. */
class LauncherIconSelectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun findText(view: View, text: String): android.widget.TextView? {
        if (view is android.widget.TextView && view.text.toString() == text) return view
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
            findText(view.getChildAt(index), text)?.let { return it }
        }
        return null
    }

    @Test fun fourChoicesKeepOneEntryPreserveTheProcessAndSurviveRecreation() {
        val pm = context.packageManager
        val before = LauncherIconMode.entries.associateWith { pm.getComponentEnabledSetting(it.component(context)) }
        val previous = LauncherIcons.current(context)
        val process = Process.myPid()
        val shortcutId = "icon-test-${UUID.randomUUID()}"
        val shortcuts = if (Build.VERSION.SDK_INT >= 25) context.getSystemService(ShortcutManager::class.java) else null
        try {
            // The class overload adds ACTION_MAIN, which this exported settings screen
            // intentionally rejects. Mirror the app's internal, action-free Intent.
            ActivityScenario.launch<AppSettingsActivity>(Intent(context, AppSettingsActivity::class.java)).use { scenario ->
                if (shortcuts != null) {
                    assertTrue(shortcuts.addDynamicShortcuts(listOf(ShortcutInfo.Builder(context, shortcutId)
                        .setShortLabel("Icon test").setActivity(previous.component(context))
                        .setIntent(Intent(context, ChatActivity::class.java).setAction(Intent.ACTION_VIEW)).build())))
                }
                for (mode in LauncherIconMode.entries) {
                    scenario.onActivity { activity ->
                        activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("launcher-icon").performClick()
                    }
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    scenario.onActivity { activity ->
                        val dialog = activity.launcherIconDialog!!
                        assertEquals(4, dialog.listView.adapter.count)
                        assertEquals("All four options start at the top of the list", 0, dialog.listView.firstVisiblePosition)
                        assertTrue(dialog.listView.adapter.getItem(LauncherIconMode.AUTO.ordinal).toString().contains(activity.getString(R.string.launcher_icon_auto_note)))
                        assertTrue(dialog.listView.adapter.getItem(LauncherIconMode.TRANSPARENT.ordinal).toString().contains(activity.getString(R.string.launcher_icon_transparent_note)))
                        val savedBefore = LauncherIcons.current(context)
                        dialog.listView.performItemClick(null, mode.ordinal, dialog.listView.adapter.getItemId(mode.ordinal))
                        assertEquals("A draft choice must not change PackageManager", savedBefore, LauncherIcons.current(context))
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                    }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    assertEquals(mode, LauncherIcons.current(context))
                    assertEquals(process, Process.myPid())
                    val matches = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                    assertEquals(1, matches.size)
                    assertEquals(mode.component(context).className, matches.single().activityInfo.name)
                    assertEquals(ChatActivity::class.java.name, matches.single().activityInfo.targetActivity)
                    assertNotNull(pm.resolveActivity(Intent(context, ChatActivity::class.java), 0))
                    shortcuts?.dynamicShortcuts?.single { it.id == shortcutId }?.let { shortcut ->
                        assertEquals(mode.component(context), shortcut.activity)
                        assertEquals(ChatActivity::class.java.name, shortcut.intent!!.component!!.className)
                    }
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        assertNotNull(findText(activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("launcher-icon"), activity.getString(activity.launcherIconLabels[mode.ordinal])))
                        assertNotNull(activity.findViewById<View>(android.R.id.content))
                    }
                }
            }
        } finally {
            shortcuts?.removeDynamicShortcuts(listOf(shortcutId))
            LauncherIcons.select(context, previous)
            before.entries.sortedBy { if (it.key == previous) 0 else 1 }.forEach { (mode, state) ->
                pm.setComponentEnabledSetting(mode.component(context), state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
