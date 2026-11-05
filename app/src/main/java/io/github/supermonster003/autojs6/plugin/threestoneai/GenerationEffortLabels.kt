package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort

/** Localized names shared by the profile editor and the chat model switcher (roadmap D72). */
internal fun Context.effortLabel(effort: GenerationEffort): String = getString(
    when (effort) {
        GenerationEffort.DEFAULT -> R.string.effort_default
        GenerationEffort.LOW -> R.string.effort_low
        GenerationEffort.MEDIUM -> R.string.effort_medium
        GenerationEffort.HIGH -> R.string.effort_high
        GenerationEffort.XHIGH -> R.string.effort_xhigh
        GenerationEffort.MAX -> R.string.effort_max
    },
)
