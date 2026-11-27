package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.VisibleForTesting
import org.autojs.plugin.speech.api.ISpeechPlugin
import org.autojs.plugin.speech.api.ISpeechSession
import org.autojs.plugin.speech.api.ISpeechSessionCallback
import org.autojs.plugin.speech.api.SpeechCapabilityKeys
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechOptionKeys
import org.autojs.plugin.speech.api.SpeechPluginActions
import org.autojs.plugin.speech.api.SpeechPluginIds
import org.autojs.plugin.speech.api.SpeechResultKeys
import java.util.concurrent.Executors

/**
 * The speech service of 3-Macaw Speech (roadmap D208), bound directly: AutoJs6 is not involved and its minimum version
 * does not change. The service is protected only by the host's signature permission `org.autojs.permission.PLUGIN`, so
 * binding fails with [SecurityException] while AutoJs6 is not installed. Recognized text is never logged (D16).
 */
internal object MacawSpeech {
    const val PACKAGE = SpeechPluginIds.OFFICIAL_PACKAGE_NAME

    /** Opens the system dialog for 3-Macaw Speech's `RECORD_AUDIO`; named by its `PERMISSION_DENIED` (protocol). */
    const val MICROPHONE_PERMISSION_ACTIVITY = "$PACKAGE.service.MicrophonePermissionActivity"
    const val PROBE_TIMEOUT_MILLIS = 5_000L

    /** A finish that 3-Macaw Speech does not end within this time ends locally. */
    const val FINISH_TIMEOUT_MILLIS = 5_000L

    /**
     * How long a new session waits for 3-Macaw Speech to end the one cancelled just before: until then it still holds
     * the microphone and refuses the new one with `SESSION_BUSY` (seen right after its engines loaded, D218).
     */
    const val CLOSE_WAIT_MILLIS = 2_000L

    /** Binder calls never run on the main thread. */
    internal val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "three-stone-ai-macaw-speech").apply { isDaemon = true }
    }

    fun intent(): Intent =
        Intent(SpeechPluginActions.SPEECH).addCategory(SpeechPluginActions.SERVICE_CATEGORY).setPackage(PACKAGE)

    fun installed(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }
    }.isSuccess

    /**
     * The flags AutoJs6 binds 3-Macaw Speech with as well (its roadmap D22): `BIND_INCLUDE_CAPABILITIES` (API 29+) lets
     * the plugin process record with the visible chat's while-in-use microphone capability, otherwise Android silences
     * the recording (seen as `App op 27 missing, silencing record` on an XQ-AT72, D219); `BIND_IMPORTANT` keeps it at
     * the chat's priority; `BIND_ALLOW_ACTIVITY_STARTS` (API 34+) lets it open its transparent activity that starts the
     * microphone foreground service (protocol "Background microphone"). Pure for the JVM tests.
     */
    @SuppressLint("InlinedApi")
    fun bindFlags(sdkInt: Int = Build.VERSION.SDK_INT): Int {
        var flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        if (sdkInt >= 29) flags = flags or Context.BIND_INCLUDE_CAPABILITIES
        if (sdkInt >= 34) flags = flags or Context.BIND_ALLOW_ACTIVITY_STARTS
        return flags
    }

    fun microphonePermissionIntent(): Intent = Intent().setClassName(PACKAGE, MICROPHONE_PERMISSION_ACTIVITY)

    /** One probe over a bound plugin; Binder calls, never on the main thread. */
    fun read(plugin: ISpeechPlugin): MacawSpeechProbe {
        val capabilities = plugin.capabilities ?: Bundle()
        fun installedModels(kind: String): Int = runCatching {
            plugin.listModels(Bundle().apply {
                putInt(SpeechContract.KEY_CONTRACT_VERSION, SpeechContract.CALL_VERSION_1)
                putString(SpeechOptionKeys.KIND, kind)
                putBoolean(SpeechOptionKeys.INSTALLED, true)
            })?.let { answer ->
                @Suppress("DEPRECATION")
                answer.getParcelableArrayList<Bundle>(SpeechResultKeys.MODELS)?.size
            }
        }.getOrNull() ?: 0
        return MacawSpeechProbe(
            installed = true,
            bindFailure = null,
            baseContractVersion = capabilities.intOrNull(SpeechCapabilityKeys.CONTRACT_VERSION),
            maxContractVersion = capabilities.intOrNull(SpeechCapabilityKeys.MAX_CONTRACT_VERSION),
            features = capabilities.getStringArray(SpeechCapabilityKeys.FEATURES)?.toSet().orEmpty(),
            microphoneGranted = capabilities.getBoolean(SpeechCapabilityKeys.MICROPHONE_GRANTED, false),
            recognitionModels = installedModels(SpeechContract.KIND_ASR),
            wakeModels = installedModels(SpeechContract.KIND_KWS),
        )
    }

    /** The error code of a failed Binder call: `CODE: detail` messages (protocol), or the death of the plugin. */
    fun errorCode(error: Throwable): String = when (error) {
        is DeadObjectException -> SpeechErrorCodes.SESSION_CLOSED
        else -> error.message?.let { message -> SpeechErrorCodes.decode(message)?.first }
            ?.takeIf(SpeechErrorCodes::isKnown)
            ?: SpeechErrorCodes.INTERNAL
    }

    /**
     * Reads the availability once with a short binding (the voice settings on every resume). [done] runs on the main
     * thread once, after [PROBE_TIMEOUT_MILLIS] at the latest; the returned action cancels it.
     */
    fun probeOnce(context: Context, done: (MacawSpeechAvailability) -> Unit): () -> Unit {
        val main = Handler(Looper.getMainLooper())
        var finished = false
        lateinit var connection: MacawSpeechConnection
        connection = MacawSpeechConnection(context) {
            val availability = connection.availability ?: return@MacawSpeechConnection
            if (finished) return@MacawSpeechConnection
            finished = true
            main.post { connection.disconnect() }
            done(availability)
        }
        connection.connect()
        return {
            if (!finished) {
                finished = true
                connection.disconnect()
            }
        }
    }

    private fun Bundle.intOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null
}

/** Instrumentation only: replaces 3-Macaw Speech with a fixed state and a scripted session source. */
@VisibleForTesting
internal object MacawSpeechTestHooks {
    /** When set, no binding happens and this is the state everywhere. */
    @Volatile
    var availability: MacawSpeechAvailability? = null

    /** When set, live dictation sessions come from it instead of the bound plugin. */
    @Volatile
    var source: LiveDictationSource? = null
}

/**
 * One binding to 3-Macaw Speech for as long as a screen needs it (the chat between `onStart` and `onStop`, D208):
 * probes it, keeps [availability] current and opens live dictation sessions. [onChange] runs on the main thread when
 * [availability] changes; null means the probe is still running. Main thread only.
 */
internal class MacawSpeechConnection(
    context: Context,
    private val onChange: () -> Unit,
) : LiveDictationSource {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var bound = false
    private var plugin: ISpeechPlugin? = null
    private var pluginBinder: IBinder? = null

    /** Invalidates results of an earlier binding after [disconnect] or a rebinding. */
    private var generation = 0
    private val sessions = mutableSetOf<MacawLiveSession>()

    /** Sessions that ended here (cancelled, failed, timed out) but not yet in 3-Macaw Speech (D218). */
    private val closing = mutableSetOf<MacawLiveSession>()

    /** Sessions opened while [closing] was not empty; they start once it is, or after [MacawSpeech.CLOSE_WAIT_MILLIS]. */
    private val waiting = mutableListOf<MacawLiveSession>()
    private val closeWait = Runnable {
        closing.clear()
        startWaiting()
    }
    // A late connection still publishes its probe afterwards.
    private val probeTimeout = Runnable {
        if (availability == null) publish(MacawSpeechProbe(installed = true, bindFailure = MacawBindFailure.TIMEOUT))
    }
    private val pluginDeath = IBinder.DeathRecipient {
        main.post { onPluginGone(MacawBindFailure.REFUSED) }
    }

    var availability: MacawSpeechAvailability? = null
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val current = generation
            val connected = service?.let(ISpeechPlugin.Stub::asInterface) ?: return
            unlinkPlugin()
            plugin = connected
            pluginBinder = service
            runCatching { service.linkToDeath(pluginDeath, 0) }
            read(current, connected)
        }

        override fun onServiceDisconnected(name: ComponentName?) = onPluginGone(MacawBindFailure.REFUSED)

        override fun onBindingDied(name: ComponentName?) {
            // The binding never reconnects by itself (an update of 3-Macaw Speech): bind once more.
            onPluginGone(MacawBindFailure.REFUSED)
            if (bound) {
                runCatching { app.unbindService(this) }
                bound = false
                bind()
            }
        }

        override fun onNullBinding(name: ComponentName?) = onPluginGone(MacawBindFailure.REFUSED)
    }

    fun connect() {
        MacawSpeechTestHooks.availability?.let { fixed ->
            setAvailability(fixed)
            return
        }
        if (bound) return
        generation++
        if (!MacawSpeech.installed(app)) {
            setAvailability(MacawSpeechAvailability.NOT_INSTALLED)
            return
        }
        setAvailability(null)
        bind()
    }

    /** Reads the capabilities again (`onResume`: a model or the microphone permission may have changed). */
    fun refresh() {
        MacawSpeechTestHooks.availability?.let { fixed ->
            setAvailability(fixed)
            return
        }
        val connected = plugin
        when {
            connected != null -> read(generation, connected)
            !bound -> connect()
        }
    }

    /** Cancels the sessions still running and unbinds. */
    fun disconnect() {
        generation++
        main.removeCallbacks(probeTimeout)
        sessions.toList().forEach(MacawLiveSession::cancel)
        sessions.clear()
        waiting.clear()
        unlinkPlugin()
        plugin = null
        if (bound) runCatching { app.unbindService(connection) }
        bound = false
    }

    override fun open(mode: LiveDictationMode, listener: LiveDictationListener): LiveDictationSession {
        MacawSpeechTestHooks.source?.let { fake -> return fake.open(mode, listener) }
        val session = MacawLiveSession(mode, listener)
        val connected = plugin
        when {
            connected == null -> main.post { session.fail(SpeechErrorCodes.PLUGIN_UNAVAILABLE) }
            closing.isEmpty() -> {
                sessions += session
                session.start(connected)
            }
            else -> {
                sessions += session
                waiting += session
                main.removeCallbacks(closeWait)
                main.postDelayed(closeWait, MacawSpeech.CLOSE_WAIT_MILLIS)
            }
        }
        return session
    }

    private fun startWaiting() {
        main.removeCallbacks(closeWait)
        val ready = waiting.toList()
        waiting.clear()
        ready.forEach { session ->
            if (session.ended) return@forEach
            val connected = plugin
            if (connected == null) session.fail(SpeechErrorCodes.PLUGIN_UNAVAILABLE) else session.start(connected)
        }
    }

    private fun closed(session: MacawLiveSession) {
        if (closing.remove(session) && closing.isEmpty() && waiting.isNotEmpty()) startWaiting()
    }

    private fun bind() {
        val current = generation
        bound = try {
            app.bindService(MacawSpeech.intent(), connection, MacawSpeech.bindFlags())
        } catch (_: SecurityException) {
            publish(MacawSpeechProbe(installed = true, bindFailure = MacawBindFailure.NO_PLUGIN_PERMISSION))
            return
        } catch (_: RuntimeException) {
            false
        }
        if (!bound) {
            publish(MacawSpeechProbe(installed = true, bindFailure = MacawBindFailure.REFUSED))
        } else if (current == generation) {
            main.removeCallbacks(probeTimeout)
            main.postDelayed(probeTimeout, MacawSpeech.PROBE_TIMEOUT_MILLIS)
        }
    }

    private fun read(current: Int, connected: ISpeechPlugin) {
        MacawSpeech.worker.execute {
            val probe = runCatching { MacawSpeech.read(connected) }
                .getOrElse { MacawSpeechProbe(installed = true, bindFailure = MacawBindFailure.REFUSED) }
            main.post { if (current == generation) publish(probe) }
        }
    }

    private fun publish(probe: MacawSpeechProbe) {
        main.removeCallbacks(probeTimeout)
        setAvailability(MacawSpeechAvailabilityPolicy.resolve(probe))
    }

    private fun setAvailability(value: MacawSpeechAvailability?) {
        if (availability == value && value != null) return
        availability = value
        onChange()
    }

    private fun onPluginGone(failure: MacawBindFailure) {
        unlinkPlugin()
        plugin = null
        // Its sessions died with it: nothing is left to wait for.
        closing.clear()
        waiting.clear()
        main.removeCallbacks(closeWait)
        sessions.toList().forEach { session -> session.fail(SpeechErrorCodes.SESSION_CLOSED) }
        sessions.clear()
        if (bound || MacawSpeech.installed(app)) publish(MacawSpeechProbe(installed = true, bindFailure = failure))
        else setAvailability(MacawSpeechAvailability.NOT_INSTALLED)
    }

    private fun unlinkPlugin() {
        pluginBinder?.let { binder -> runCatching { binder.unlinkToDeath(pluginDeath, 0) } }
        pluginBinder = null
    }

    /** One `openSession` of 3-Macaw Speech; its callbacks reach the [LiveDictationEventGate] on the main thread. */
    private inner class MacawLiveSession(
        mode: LiveDictationMode,
        listener: LiveDictationListener,
    ) : LiveDictationSession {
        private val gate = LiveDictationEventGate(mode, ForwardingListener(listener)) { requestRemoteFinish() }

        @Volatile
        private var remote: ISpeechSession? = null

        @Volatile
        private var cancelled = false

        @Volatile
        private var finishRequested = false

        /** `openSession` was sent; main thread. */
        private var started = false

        /** 3-Macaw Speech ended the session, refused it or died; main thread. */
        private var remoteDone = false

        val ended: Boolean get() = gate.ended
        private val sessionDeath = IBinder.DeathRecipient {
            main.post {
                remoteClosed()
                fail(SpeechErrorCodes.SESSION_CLOSED)
            }
        }
        private val finishTimeout = Runnable {
            if (!gate.ended) {
                cancelRemote()
                gate.end(SpeechContract.REASON_STOPPED)
            }
        }

        private val callback = object : ISpeechSessionCallback.Stub() {
            override fun onReady(info: Bundle?) {
                main.post { gate.ready() }
            }

            override fun onPartial(result: Bundle?) {
                val text = result?.getString(SpeechResultKeys.TEXT).orEmpty()
                main.post { gate.partial(text) }
            }

            override fun onFinal(result: Bundle?) {
                val text = result?.getString(SpeechResultKeys.TEXT).orEmpty()
                main.post { gate.final(text) }
            }

            override fun onEndpoint() = Unit

            override fun onVoiceActivity(activity: Bundle?) = Unit

            // A silenced microphone or a refused foreground service; the session goes on (protocol).
            override fun onWarning(warning: Bundle?) = Unit

            override fun onError(error: Bundle?) {
                val code = error?.getString(SpeechContract.KEY_ERROR_CODE)
                main.post { gate.error(code) }
            }

            override fun onEnd(end: Bundle?) {
                val reason = end?.getString(SpeechResultKeys.REASON)
                main.post {
                    remoteClosed()
                    gate.end(reason)
                }
            }
        }

        fun start(connected: ISpeechPlugin) {
            started = true
            val options = Bundle().apply {
                LiveDictationRequest.options(gate.mode).forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                    }
                }
            }
            MacawSpeech.worker.execute {
                val opened = try {
                    connected.openSession(options, callback)
                } catch (error: Exception) {
                    val code = MacawSpeech.errorCode(error)
                    main.post {
                        remoteClosed()
                        fail(code)
                    }
                    return@execute
                }
                if (opened == null) {
                    main.post {
                        remoteClosed()
                        fail(SpeechErrorCodes.INTERNAL)
                    }
                    return@execute
                }
                remote = opened
                runCatching { opened.asBinder().linkToDeath(sessionDeath, 0) }
                when {
                    cancelled -> runCatching { opened.cancel() }
                    finishRequested -> runCatching { opened.finish() }
                }
            }
        }

        override fun finish() {
            gate.requestFinish()
        }

        override fun cancel() {
            if (gate.ended) return
            gate.cancel()
            cancelRemote()
        }

        fun fail(code: String) {
            if (gate.ended) return
            cancelRemote()
            gate.fail(code)
        }

        private fun requestRemoteFinish() {
            finishRequested = true
            main.postDelayed(finishTimeout, MacawSpeech.FINISH_TIMEOUT_MILLIS)
            MacawSpeech.worker.execute { remote?.let { session -> runCatching { session.finish() } } }
        }

        private fun cancelRemote() {
            cancelled = true
            MacawSpeech.worker.execute { remote?.let { session -> runCatching { session.cancel() } } }
        }

        private fun remoteClosed() {
            if (remoteDone) return
            remoteDone = true
            closed(this)
        }

        private fun ended() {
            main.removeCallbacks(finishTimeout)
            sessions.remove(this)
            // Ended here first: 3-Macaw Speech keeps the microphone until it ends the session too.
            if (started && !remoteDone) closing += this
            remote?.let { session -> runCatching { session.asBinder().unlinkToDeath(sessionDeath, 0) } }
        }

        /** Cleans up once the gate reports the end, before the chat hears of it. */
        private inner class ForwardingListener(private val target: LiveDictationListener) : LiveDictationListener {
            override fun onReady() = target.onReady()
            override fun onPartial(text: String) = target.onPartial(text)
            override fun onFinal(text: String) = target.onFinal(text)
            override fun onEnd(end: LiveDictationEnd) {
                ended()
                target.onEnd(end)
            }
        }
    }
}
