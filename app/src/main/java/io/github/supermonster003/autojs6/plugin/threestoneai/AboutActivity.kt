package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AboutActivity : ConfiguredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_RELEASE_HISTORY, 0, R.string.release_history_title)
        menu.add(0, MENU_PROJECT, 1, R.string.about_project_source)
        menu.add(0, MENU_DEVELOPER, 2, R.string.about_developer_page)
        menu.add(0, MENU_LICENSE, 3, R.string.about_license_link)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_RELEASE_HISTORY -> {
            startActivity(Intent(this, ReleaseHistoryActivity::class.java))
            true
        }
        MENU_PROJECT -> {
            openUri(PROJECT_URI)
            true
        }
        MENU_DEVELOPER -> {
            openUri(DEVELOPER_URI)
            true
        }
        MENU_LICENSE -> {
            openUri(LICENSE_URI)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        val statusBarBackground = createStatusBarBackground()
        addView(
            statusBarBackground,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0),
        )
        addView(createAppToolbar(R.string.about_app_and_developer, showBack = true))
        addView(ScrollView(context).apply {
            addView(createAboutContent())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        applySystemBarInsets(this, statusBarBackground)
    }

    private fun createAboutContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPaddingRelative(dp(28), dp(34), dp(28), dp(40))
        addView(ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = getString(R.string.app_name)
        }, LinearLayout.LayoutParams(dp(96), dp(96)))
        addView(TextView(context).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(appPalette.primaryText)
            setPaddingRelative(0, dp(20), 0, dp(8))
        })
        addView(TextView(context).apply {
            text = getString(R.string.about_app_summary)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(appPalette.secondaryText)
            setLineSpacing(0f, 1.15f)
        })

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        addView(infoBlock(
            getString(R.string.about_version),
            getString(
                R.string.about_version_details,
                packageInfo.versionName.orEmpty(),
                versionCode,
                getString(R.string.plugin_version_date),
            ),
        ))
        addView(infoBlock(
            getString(R.string.about_developer),
            getString(R.string.plugin_author),
        ))
        addView(infoBlock(
            getString(R.string.about_license),
            getString(R.string.about_license_summary),
        ))

    }

    private fun infoBlock(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(0, dp(26), 0, dp(2))
        addView(TextView(context).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(appPalette.accent)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(appPalette.primaryText)
            setTextIsSelectable(true)
            setPaddingRelative(0, dp(4), 0, 0)
        })
    }

    private fun openUri(uri: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MENU_RELEASE_HISTORY = 1
        const val MENU_PROJECT = 2
        const val MENU_DEVELOPER = 3
        const val MENU_LICENSE = 4
        const val PROJECT_URI = "https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI"
        const val DEVELOPER_URI = "https://github.com/SuperMonster003"
        const val LICENSE_URI = "$PROJECT_URI/blob/master/LICENSE"
    }
}
