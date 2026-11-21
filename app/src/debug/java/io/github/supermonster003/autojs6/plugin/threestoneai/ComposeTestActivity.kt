package io.github.supermonster003.autojs6.plugin.threestoneai

import androidx.activity.ComponentActivity

/**
 * The empty host of the Compose tests that set their own content (`createAndroidComposeRule<ComposeTestActivity>()`).
 * It stands in for the activity of `androidx.compose.ui:ui-test-manifest`, which is exported and would add an exported
 * `androidx.activity.ComponentActivity` to the debug APK; this one is not exported and keeps the same theme. Debug
 * builds only.
 */
class ComposeTestActivity : ComponentActivity()
