package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AboutActivity : ConfiguredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        addView(createToolbar())
        addView(ScrollView(context).apply {
            addView(createAboutContent())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        applySystemBarInsets(this)
    }

    private fun createToolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(58)
        setPaddingRelative(dp(8), dp(4), dp(18), dp(4))
        setBackgroundColor(appPalette.primary)
        addView(TextView(context).apply {
            text = getString(R.string.navigation_back)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(appPalette.onPrimary)
            setPaddingRelative(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { finish() }
        })
        addView(TextView(context).apply {
            text = getString(R.string.about_app_and_developer)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(appPalette.onPrimary)
            setPaddingRelative(dp(8), 0, 0, 0)
        })
    }

    private fun createAboutContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPaddingRelative(dp(24), dp(26), dp(24), dp(30))
        addView(ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher_on_device_ai)
            contentDescription = getString(R.string.app_name)
        }, LinearLayout.LayoutParams(dp(96), dp(96)))
        addView(TextView(context).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(appPalette.primaryText)
            setPaddingRelative(0, dp(16), 0, dp(5))
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

        addView(actionButton(R.string.release_history_title) {
            startActivity(Intent(this@AboutActivity, ReleaseHistoryActivity::class.java))
        })
        addView(actionButton(R.string.about_project_source) { openUri(PROJECT_URI) })
        addView(actionButton(R.string.about_developer_page) { openUri(DEVELOPER_URI) })
        addView(actionButton(R.string.about_license_link) { openUri(LICENSE_URI) })
    }

    private fun infoBlock(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(0, dp(20), 0, 0)
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

    private fun actionButton(labelResource: Int, action: () -> Unit) = Button(this).apply {
        text = getString(labelResource)
        isAllCaps = false
        setTextColor(appPalette.onPrimary)
        backgroundTintList = android.content.res.ColorStateList.valueOf(appPalette.primary)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) }
    }

    private fun openUri(uri: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PROJECT_URI = "https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI"
        const val DEVELOPER_URI = "https://github.com/SuperMonster003"
        const val LICENSE_URI = "$PROJECT_URI/blob/master/LICENSE"
    }
}
