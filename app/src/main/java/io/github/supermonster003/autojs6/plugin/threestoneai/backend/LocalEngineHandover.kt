package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Keeps one on-device engine per app instead of one per process (D227).
 *
 * The chat (main process) and the script provider (`:provider`) each own a process-wide engine
 * cache. A chat session keeps its engine while the conversation stays open and a script's engine
 * stays cached for five minutes, so a script call after a local chat turn, or a chat turn after a
 * script, loaded a second copy of the model (about 1.5 GB more for Gemma 4 E2B on XQ-AT72). Before
 * loading, a process now asks the app's other processes, with an ordered broadcast inside the
 * package, to give back an engine they are not using, and waits briefly for the answer.
 */
internal object LocalEngineHandover {
    private const val TAG = "ThreeStoneAiEngine"
    private const val EXTRA_SENDER_PID = "senderPid"

    /** A process that does not answer in time (frozen, busy) is not waited for any longer. */
    const val REQUEST_TIMEOUT_MILLIS = 4_000L

    /** How long a process waits for its engine to close before it answers. */
    const val RELEASE_WAIT_MILLIS = 3_000L

    const val RESULT_FREE = 1
    const val RESULT_IN_USE = 2

    /** Instrumentation only: how this process's last request and last answer went (no conversation content). */
    @Volatile
    internal var lastRequestForTest: String? = null

    @Volatile
    internal var lastAnswerForTest: String? = null

    private val thread: HandlerThread by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HandlerThread("three-stone-ai-engine-handover").apply { start() }
    }

    private fun handler() = Handler(thread.looper)

    fun action(context: Context): String = context.packageName + ".action.RELEASE_LOCAL_ENGINE"

    /**
     * Called on a worker thread right before this process loads an engine. Returns once every other
     * process answered, or after [timeoutMillis]; failures only cost the memory this tries to save.
     */
    fun requestRelease(context: Context, timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS) {
        val startedAt = SystemClock.elapsedRealtime()
        val answered = CountDownLatch(1)
        var resultCode = 0
        val intent = Intent(action(context))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(EXTRA_SENDER_PID, Process.myPid())
        val result = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                resultCode = getResultCode()
                answered.countDown()
            }
        }
        try {
            context.sendOrderedBroadcast(intent, null, result, handler(), 0, null, null)
            val done = answered.await(timeoutMillis, TimeUnit.MILLISECONDS)
            val summary = "answered=$done result=${describe(resultCode)} " +
                "elapsedMillis=${SystemClock.elapsedRealtime() - startedAt}"
            lastRequestForTest = summary
            Log.d(TAG, "Engine release requested $summary")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Engine release request failed", error)
        }
    }

    /**
     * Registers this process's answer. [release] runs on a worker thread, gives back the engine if it
     * is idle (or as soon as it becomes idle) and returns whether this process holds none in time.
     */
    fun register(context: Context, release: () -> Boolean) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_SENDER_PID, Process.myPid()) == Process.myPid()) return
                val pending = goAsync()
                Thread({
                    val startedAt = SystemClock.elapsedRealtime()
                    val released = runCatching(release).getOrElse { error ->
                        Log.w(TAG, "Engine release failed", error)
                        false
                    }
                    val code = if (released) RESULT_FREE else RESULT_IN_USE
                    val summary = "result=${describe(code)} elapsedMillis=${SystemClock.elapsedRealtime() - startedAt}"
                    lastAnswerForTest = summary
                    Log.d(TAG, "Engine release answered $summary")
                    // Several processes may answer; one that still uses its engine keeps that answer.
                    if (pending.resultCode != RESULT_IN_USE) pending.resultCode = code
                    pending.finish()
                }, "three-stone-ai-engine-release").apply { isDaemon = true }.start()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action(context)),
            null,
            handler(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun describe(code: Int): String = when (code) {
        RESULT_FREE -> "free"
        RESULT_IN_USE -> "in-use"
        else -> "nothing-held"
    }
}
