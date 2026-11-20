package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.content.Intent
import java.io.IOException

/**
 * Opens the release history page. The "Release history" row of "Updates and about" and the neutral button of the
 * update dialog both lead here.
 */
internal fun Context.openReleaseHistory() {
    startActivity(Intent(this, ReleaseHistoryActivity::class.java))
}

/**
 * Returns the releases of the first candidate asset that holds at least one, so a missing, blank or unparsable
 * localized changelog falls back to the English one; empty when none of them does (the page shows its empty state).
 */
internal fun loadReleaseHistory(candidates: List<String>, loader: (String) -> String): List<ReleaseHistoryEntry> {
    for (path in candidates) {
        val markdown = try { loader(path) } catch (_: IOException) { continue }
        val entries = ReleaseHistoryParser.parse(markdown)
        if (entries.isNotEmpty()) return entries
    }
    return emptyList()
}
