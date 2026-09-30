package io.github.supermonster003.autojs6.plugin.threestoneai


import android.content.Intent
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** AVD-only UI contract; restores app appearance values after each test. */
class AppearanceSettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun snapshot() = ApplicationSettingsStore(context).load()
    private fun force(dark: Boolean) { ApplicationSettingsStore(context).save(ApplicationSettings(AppThemeSelection.CUSTOM, 0xff3f51b5.toInt(), if (dark) AppDarkMode.DARK else AppDarkMode.LIGHT, AppLanguage.ENGLISH)) }
    private fun color(): Int? = ApplicationSettingsStore(context).load().customThemeColor
    private fun dialog(activity: AppSettingsActivity): AlertDialog = activity.appearanceDialog!!
    private fun palette(activity: AppSettingsActivity): Pair<Int, Int> = activity.appPalette.windowBackground to activity.appPalette.accent
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun row(activity: AppSettingsActivity, resource: Int): View {
        var view: View = views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == activity.getString(resource) }
        while (!view.isClickable) view = view.parent as View
        return view
    }
    private fun draft(dialog: AlertDialog, index: Int) {
        if (dialog.listView != null) dialog.listView.performItemClick(null, index, dialog.listView.adapter.getItemId(index))
        else {
            var view: View = views(dialog.window!!.decorView).filterIsInstance<RadioButton>()[index]
            while (!view.isClickable) view = view.parent as View
            view.performClick()
        }
    }
    private fun waitUntil(action: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        do { instrumentation.waitForIdleSync(); if (action()) return; SystemClock.sleep(40) } while (SystemClock.uptimeMillis() < deadline)
        fail("Appearance change did not finish")
    }
    private fun launch() = ActivityScenario.launch<AppSettingsActivity>(Intent(context, AppSettingsActivity::class.java))
    private fun backup(): () -> Unit {
        val preferences = context.getSharedPreferences("application-settings", android.content.Context.MODE_PRIVATE)
        val saved = preferences.all.toMap()
        return {
            val restored = preferences.edit().clear().apply {
                saved.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value); is Int -> putInt(key, value); is Long -> putLong(key, value)
                    is Float -> putFloat(key, value); is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
            }.commit()
            assertTrue("Appearance restoration must finish writing before instrumentation exits", restored)
            assertEquals(saved, preferences.all)
        }
 }

    @Test fun rowsFollowTheSharedMetricsAndCanceledChoicesDoNotPersist() {
        val restore = backup()
        try {
            force(false)
            val before = snapshot()
            launch().use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    var previousTop = -1
                    for (resource in listOf(R.string.app_settings_language, R.string.app_settings_dark_mode, R.string.app_settings_theme_color, R.string.launcher_icon_title)) {
                        val view = row(activity, resource) as LinearLayout
                        val density = activity.resources.displayMetrics.density
                        fun dp(value: Int) = (value * density + 0.5f).toInt()
                        assertEquals(dp(24), view.paddingStart)
                        assertEquals(dp(24), view.paddingEnd)
                        assertEquals(dp(72), view.minimumHeight)
                        assertTrue(view.top > previousTop); previousTop = view.top
                        val texts = views(view).filterIsInstance<TextView>()
                        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, activity.resources.displayMetrics), texts[0].textSize, 0.5f)
                        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, activity.resources.displayMetrics), texts[1].textSize, 0.5f)
                    }
                }
                for (resource in listOf(R.string.app_settings_language, R.string.app_settings_dark_mode)) {
                    scenario.onActivity { activity ->
                        row(activity, resource).performClick()
                        val opened = dialog(activity)
                        draft(opened, 1)
                        assertEquals(before, snapshot())
                        opened.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                        assertEquals(before, snapshot())
                    }
                }
                scenario.onActivity { activity ->
                    val saved = LauncherIcons.current(context)
                    row(activity, R.string.launcher_icon_title).performClick()
                    val opened = activity.launcherIconDialog!!
                    draft(opened, LauncherIconMode.entries.first { it != saved }.ordinal)
                    assertEquals(saved, LauncherIcons.current(context))
                    opened.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                    assertEquals(saved, LauncherIcons.current(context))
                }
            }
        } finally { restore() }
    }

    @Test fun localColorPreviewCancelsCleanlyAndConfirmedColorKeepsNeutralSurfaces() {
        val restore = backup()
        try {
            for (dark in listOf(false, true)) {
                force(dark)
                val before = snapshot()
                launch().use { scenario ->
                    scenario.onActivity { row(it, R.string.app_settings_theme_color).performClick() }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val opened = dialog(activity)
                        opened.window!!.decorView.findViewWithTag<EditText>("theme-color-input").setText("rgb(17, 34, 51)")
                        assertEquals("Local preview does not save application appearance", before, snapshot())
                        opened.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                    }
                    instrumentation.waitForIdleSync()
                    assertEquals(before, snapshot())
                    scenario.onActivity { row(it, R.string.app_settings_theme_color).performClick() }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val opened = dialog(activity)
                        val field = opened.window!!.decorView.findViewWithTag<EditText>("theme-color-input")
                        field.setText("rgb(300, 0, 0)")
                        assertFalse(opened.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled)
                        field.setText("#113355")
                        assertTrue(opened.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled)
                        assertEquals(before, snapshot())
                        opened.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                    }
                    waitUntil { color() == 0xff113355.toInt() }
                    scenario.recreate()
                    var expectedAccent = 0
                    scenario.onActivity { activity ->
                        val (background, accent) = palette(activity)
                        assertEquals(if (dark) 0xff121212.toInt() else 0xfff3f4f5.toInt(), background)
                        assertTrue("Accent text stays readable", AppColorPolicy.contrastRatio(accent, background) >= 4.5)
                        expectedAccent = accent
                        row(activity, R.string.app_settings_theme_color).performClick()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val opened = dialog(activity)
                        assertEquals(expectedAccent, opened.getButton(DialogInterface.BUTTON_NEGATIVE).currentTextColor)
                        opened.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                    }
                    instrumentation.waitForIdleSync()
                }
            }
        } finally { restore() }
    }

    /** Controlled cache data exercises lifecycle policy, not the host signature or Binder contract. */
    private fun publishChangedHostTheme(): () -> Unit {
        val previous = AutoJs6HostSettingsClient.query(context)
        AutoJs6HostSettingsClient.publish(AutoJs6HostSettingsResult(AutoJs6HostAvailability.AVAILABLE,
            AutoJs6HostSettingsSnapshot(1, "controlled", 0xfff44336.toInt(), 0xfff44336.toInt(), 0xfff44336.toInt(),
                AutoJs6DarkModePolicy.FOLLOW_SYSTEM, false, "en", "en")))
        return { AutoJs6HostSettingsClient.publish(previous) }
    }

    @Test fun lateHostAppearanceDoesNotDiscardAnUnconfirmedColorDraft() {
        val restore = backup()
        var restoreHost: (() -> Unit)? = null
        try {
            force(false)
            ApplicationSettingsStore(context).let { it.save(it.load().copy(themeSelection = AppThemeSelection.FOLLOW_AUTOJS6)) }
            val before = snapshot()
            launch().use { scenario ->
                var instance = 0
                scenario.onActivity { activity ->
                    instance = System.identityHashCode(activity)
                    row(activity, R.string.app_settings_theme_color).performClick()
                    dialog(activity).window!!.decorView.findViewWithTag<EditText>("theme-color-input").setText("rgb(17, 34, 51)")
                    restoreHost = publishChangedHostTheme()
                }
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals("An unconfirmed dialog must survive a late host snapshot", instance, System.identityHashCode(activity))
                    assertTrue(dialog(activity).isShowing)
                    assertEquals("rgb(17, 34, 51)", dialog(activity).window!!.decorView.findViewWithTag<EditText>("theme-color-input").text.toString())
                    assertEquals(before, snapshot())
                    dialog(activity).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                }
            }
        } finally { restoreHost?.invoke(); restore() }
    }

    @Test fun rtlAppearanceDialogKeepsLabelsAndFixedActionsWithinBounds() {
        val restore = backup()
        try {
            force(true)
            ApplicationSettingsStore(context).let { it.save(it.load().copy(language = AppLanguage.ARABIC)) }
            launch().use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(View.LAYOUT_DIRECTION_RTL, activity.resources.configuration.layoutDirection)
                    row(activity, R.string.launcher_icon_title).performClick()
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val opened = activity.launcherIconDialog!!
                    val root = opened.window!!.decorView
                    val density = activity.resources.displayMetrics.density
                    assertTrue("Dialog respects the 560dp maximum", root.width <= (560 * density + 1).toInt())
                    val bounds = android.graphics.Rect()
                    for (which in listOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE)) {
                        val button = opened.getButton(which)
                        assertTrue(button.getGlobalVisibleRect(bounds))
                        assertEquals("Action remains fully visible", button.height, bounds.height())
                    }
                    for (text in views(root).filterIsInstance<TextView>()) {
                        val layout = text.layout ?: continue
                        if (text.visibility != View.VISIBLE || text.text.isNullOrEmpty()) continue
                        assertTrue("No clipped label: ${text.text}", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
                    }
                    val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(bitmap))
                    java.io.File(context.cacheDir, "settings-unification-rtl-dialog.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    opened.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                }
                instrumentation.waitForIdleSync()
            }
        } finally { restore() }
    }

}
