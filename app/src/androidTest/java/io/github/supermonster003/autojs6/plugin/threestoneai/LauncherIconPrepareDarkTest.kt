package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** App-UID upgrade preparation, intentionally persistent only under the explicit audit argument. */
class LauncherIconPrepareDarkTest {
    @Test fun prepareExplicitDarkFromTheActuallyInstalledController() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("prepareLauncherDark") == "true")
        assertTrue("Only run upgrade preparation on an AVD", Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        args.getString("expectedLauncherBuild")?.let { assertEquals("Must run against the retained old APK", it.toLong(), build) }
        LauncherIcons.select(context, LauncherIconMode.LIGHT)
        LauncherIcons.select(context, LauncherIconMode.DARK)
        val report = Bundle().apply { putString("launcherBuild", build.toString()) }
        for (mode in LauncherIconMode.entries) {
            val state = context.packageManager.getComponentEnabledSetting(mode.component(context))
            assertEquals(if (mode == LauncherIconMode.DARK) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state)
            report.putString("launcherRaw${mode.name}", state.toString())
        }
        val entries = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        assertEquals(1, entries.size)
        assertEquals(LauncherIconMode.DARK.component(context).className, entries.single().activityInfo.name)
        instrumentation.sendStatus(0, report)
    }
}
