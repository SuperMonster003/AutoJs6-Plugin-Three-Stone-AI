package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.hairline
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow

class AboutActivity : ConfiguredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): View {
        val scaffold = buildScaffold(R.string.about_app_and_developer)
        val content = scaffold.content

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_XXXL), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_LG))
            addView(ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = getString(R.string.app_name)
                tag = "about-icon"
                background = roundedFill(android.graphics.Color.TRANSPARENT, Ui.RADIUS_SHEET, appPalette.outline)
                clipToOutline = true
            }, LinearLayout.LayoutParams(uiDp(88), uiDp(88)))
            addView(TextView(context).apply {
                text = getString(R.string.app_name)
                textSize = Ui.TEXT_DISPLAY
                typeface = Ui.mediumTypeface
                gravity = Gravity.CENTER
                setTextColor(appPalette.primaryText)
                setPaddingRelative(0, uiDp(Ui.SPACE_XL), 0, uiDp(Ui.SPACE_SM))
            })
            addView(TextView(context).apply {
                text = getString(R.string.about_app_summary)
                textSize = Ui.TEXT_BODY
                gravity = Gravity.CENTER
                setTextColor(appPalette.secondaryText)
                setLineSpacing(0f, Ui.LINE_SPACING_BODY)
            })
        })

        content.addView(hairline())
        content.addView(
            settingRow(
                title = getString(R.string.release_history_title),
                iconResource = R.drawable.ic_article_24,
                onClick = { startActivity(Intent(this, ReleaseHistoryActivity::class.java)) },
            ).view,
        )
        content.addView(
            settingRow(
                title = getString(R.string.about_project_source),
                iconResource = R.drawable.ic_code_24,
                onClick = { openUri(PROJECT_URI) },
            ).view,
        )
        content.addView(
            settingRow(
                title = getString(R.string.about_developer_page),
                iconResource = R.drawable.ic_person_24,
                onClick = { openUri(DEVELOPER_URI) },
            ).view,
        )
        content.addView(
            settingRow(
                title = getString(R.string.about_license_link),
                iconResource = R.drawable.ic_description_24,
                onClick = { openUri(LICENSE_URI) },
            ).view,
        )
        content.addView(hairline())

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        content.addView(infoBlock(
            getString(R.string.about_version),
            getString(
                R.string.about_version_details,
                packageInfo.versionName.orEmpty(),
                versionCode,
                getString(R.string.plugin_version_date),
            ),
        ))
        content.addView(infoBlock(
            getString(R.string.about_developer),
            getString(R.string.plugin_author),
        ))
        content.addView(infoBlock(
            getString(R.string.about_license),
            getString(R.string.about_license_summary),
        ))
        return scaffold.root
    }

    private fun infoBlock(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(uiDp(Ui.SCREEN_MARGIN), uiDp(Ui.SPACE_XL), uiDp(Ui.SCREEN_MARGIN), 0)
        addView(TextView(context).apply {
            text = title
            textSize = Ui.TEXT_SECTION
            typeface = Ui.mediumTypeface
            setTextColor(appPalette.accent)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(appPalette.primaryText)
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.1f)
            setPaddingRelative(0, uiDp(Ui.SPACE_XS), 0, 0)
        })
    }

    private fun openUri(uri: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    private companion object {
        const val PROJECT_URI = "https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI"
        const val DEVELOPER_URI = "https://github.com/SuperMonster003"
        const val LICENSE_URI = "$PROJECT_URI/blob/master/LICENSE"
    }
}
