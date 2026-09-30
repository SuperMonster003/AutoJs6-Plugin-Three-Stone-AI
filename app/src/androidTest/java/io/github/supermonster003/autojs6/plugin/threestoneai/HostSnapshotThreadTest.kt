package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ContextWrapper
import android.content.ContentResolver
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class HostSnapshotThreadTest {
    @Test fun mainThreadOnlyReadsThePublishedAppearanceSnapshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var providerCalls = 0
        var packageCalls = 0
        val guard = object : ContextWrapper(instrumentation.targetContext) {
            override fun getContentResolver(): ContentResolver { providerCalls++; error("No provider calls from UI inflation") }
            override fun getPackageManager(): PackageManager { packageCalls++; return super.getPackageManager() }
        }
        instrumentation.runOnMainSync { AutoJs6HostSettingsClient.query(guard) }
        assertEquals(0, providerCalls)
        assertEquals(0, packageCalls)
    }
}
