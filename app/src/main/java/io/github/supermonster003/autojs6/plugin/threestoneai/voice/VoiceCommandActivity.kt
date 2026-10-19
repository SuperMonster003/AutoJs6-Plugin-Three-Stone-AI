package io.github.supermonster003.autojs6.plugin.threestoneai.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity

/**
 * The `VOICE_COMMAND` entry for the digital assistant of 3-Macaw Speech (roadmap D35, D38, Macaw D50).
 * A transparent trampoline: it cleans the text, hands the command to the chat in this process and
 * finishes at once, without starting a model. The chat opens visibly in its own task and decides
 * whether to send (D37). Text is never logged (D16).
 */
class VoiceCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = runCatching {
            VoiceCommand(
                text = VoiceCommandPolicy.cleanText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)),
                languageTag = VoiceCommandPolicy.languageTag(intent.getStringExtra(VoiceCommandPolicy.EXTRA_VOICE_LANGUAGE)),
                receivedAtMillis = SystemClock.elapsedRealtime(),
            )
        }.getOrNull()
        if (command != null) VoiceCommandInbox.post(command)
        runCatching {
            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra(VoiceCommandInbox.EXTRA_CHECK_INBOX, true)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
        }
        finish()
    }
}

/**
 * Hands voice commands from [VoiceCommandActivity] to the chat inside this process. The chat's own
 * intent only carries "look at the inbox", so another app cannot send through the chat's exported
 * launcher activity. Commands older than [MAXIMUM_AGE_MILLIS] are dropped unanswered.
 */
internal object VoiceCommandInbox {
    const val EXTRA_CHECK_INBOX = "io.github.supermonster003.autojs6.plugin.threestoneai.voice.CHECK_INBOX"
    private const val MAXIMUM_PENDING = 4
    private const val MAXIMUM_AGE_MILLIS = 60_000L
    private val pending = ArrayDeque<VoiceCommand>()

    @Synchronized
    fun post(command: VoiceCommand) {
        if (pending.size >= MAXIMUM_PENDING) pending.removeFirst()
        pending.addLast(command)
    }

    @Synchronized
    fun takeAll(nowMillis: Long = SystemClock.elapsedRealtime()): List<VoiceCommand> {
        val commands = pending.filter { command -> nowMillis - command.receivedAtMillis in 0..MAXIMUM_AGE_MILLIS }
        pending.clear()
        return commands
    }
}

/** When the last voice command was sent at once; kept across process restarts for the rate limit. */
internal class VoiceCommandStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var lastImmediateSendAtMillis: Long?
        get() = preferences.getLong(KEY_LAST_SEND, -1L).takeIf { it >= 0L }
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_LAST_SEND) else putLong(KEY_LAST_SEND, value)
            }.apply()
        }

    companion object {
        const val PREFERENCES_NAME = "voice-command"
        private const val KEY_LAST_SEND = "last-immediate-send-elapsed"
    }
}
