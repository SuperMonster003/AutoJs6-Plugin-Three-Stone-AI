package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider

class ThreeStoneAiPluginInfoService : Service() {
    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo() = threeStoneAiPluginInfo()
    }
}
