package io.github.supermonster003.autojs6.plugin.threestoneai.ui.compose

import android.util.TypedValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.supermonster003.autojs6.plugin.threestoneai.AppColorPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.AppThemePalette
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.ReleaseHistoryEntry
import io.github.supermonster003.autojs6.plugin.threestoneai.ReleaseHistoryInlineParser

/**
 * The Material 3 colors of the release history page, taken from the palette of the view pages: the page and its top
 * bar on the window background (as the settings pages, Q148 A), text in the primary and secondary text colors, the
 * category labels in the accent on a tint of it.
 */
@Composable
internal fun ReleaseHistoryTheme(palette: AppThemePalette, content: @Composable () -> Unit) {
    val colorScheme = remember(palette) {
        val container = AppColorPolicy.blend(palette.windowBackground, palette.accent, if (palette.isDark) 0.24 else 0.14)
        // Readable on the label tint and, with that, on the window background (the link color).
        val accent = AppColorPolicy.readableAccent(AppColorPolicy.readableAccent(palette.accent, container), palette.windowBackground)
        val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = Color(accent),
            onPrimary = Color(palette.onAccent),
            primaryContainer = Color(container),
            onPrimaryContainer = Color(accent),
            background = Color(palette.windowBackground),
            onBackground = Color(palette.primaryText),
            surface = Color(palette.windowBackground),
            onSurface = Color(palette.primaryText),
            surfaceVariant = Color(palette.surfaceVariant),
            onSurfaceVariant = Color(palette.secondaryText),
            outline = Color(palette.outline),
            outlineVariant = Color(palette.divider),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** The versions shown expanded; saved as a list so the choice survives rotation and process death. */
private val ExpandedVersionsSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() },
)

/**
 * Release history, after the model page of the official plugins (`AUTOJS6_PLUGIN_RELEASE_HISTORY_AGENTS.md`): the
 * title "Release history" with the plugin name as the subtitle and one group per version. A collapsed version shows
 * its date and how many items each category has; an expanded one lists its items under each category, the category
 * named once. The newest version starts expanded; the top bar expands or collapses all of them.
 */
@Composable
internal fun ReleaseHistoryScreen(entries: List<ReleaseHistoryEntry>, onBack: () -> Unit) {
    var expanded by rememberSaveable(stateSaver = ExpandedVersionsSaver) {
        mutableStateOf(entries.firstOrNull()?.let { setOf(it.version) }.orEmpty())
    }
    val allExpanded = entries.isNotEmpty() && entries.all { it.version in expanded }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        ReleaseHistoryTopBar(onBack = onBack) {
            if (entries.isNotEmpty()) {
                IconButton(
                    onClick = { expanded = if (allExpanded) emptySet() else entries.mapTo(mutableSetOf()) { it.version } },
                    modifier = Modifier.testTag("stone.releaseHistory.toggleAll"),
                ) {
                    Icon(
                        painter = painterResource(if (allExpanded) R.drawable.ic_unfold_less_24 else R.drawable.ic_unfold_more_24),
                        contentDescription = stringResource(
                            if (allExpanded) R.string.release_history_collapse_all else R.string.release_history_expand_all,
                        ),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ReleaseHistoryEmptyState(stringResource(R.string.release_history_load_failed))
            }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("stone.releaseHistory.list"), contentPadding = bottom) {
            itemsIndexed(entries, key = { _, entry -> entry.version }) { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ReleaseHistoryDefaults.horizontalPadding),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                val isExpanded = entry.version in expanded
                ReleaseEntry(
                    entry = entry,
                    expanded = isExpanded,
                    onToggle = { expanded = if (isExpanded) expanded - entry.version else expanded + entry.version },
                )
            }
        }
    }
}

/** Dimensions of the release history page (`AUTOJS6_PLUGIN_RELEASE_HISTORY_AGENTS.md`, layout). */
internal object ReleaseHistoryDefaults {
    val horizontalPadding = 24.dp
    val headerEndPadding = 16.dp
    val verticalPadding = 16.dp
    val bulletSlot = 20.dp
    val bulletSize = 5.dp
    val bulletStart = 6.dp
    val groupSpacing = 14.dp
    val itemSpacing = 6.dp
}

/**
 * The top bar of the view pages (`ConfiguredActivity.createAppToolbar`) in Compose: the action bar height of the
 * theme (taller on tablets), the back button, the title (20 sp, medium) over the subtitle (13 sp) from 72 dp, the
 * actions at the end, all on the window background.
 */
@Composable
private fun ReleaseHistoryTopBar(onBack: () -> Unit, actions: @Composable () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    val height: Dp = remember(context, resources, density) {
        val value = TypedValue()
        val resolved = context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, value, true)
        if (resolved && value.type == TypedValue.TYPE_DIMENSION) {
            with(density) { TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics).toDp() }
        } else {
            56.dp
        }
    }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(height).background(colors.surface).testTag("stone.topBar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onBack, modifier = Modifier.testTag("stone.topBar.navigation")) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24),
                contentDescription = stringResource(R.string.navigation_back),
                tint = colors.onSurface,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.release_history_title),
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, textDirection = TextDirection.Content),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.app_name),
                // The plugin name starts with "3-"; its own direction keeps it from reading "Stone AI-3" in Arabic.
                style = TextStyle(fontSize = 13.sp, textDirection = TextDirection.Content),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
        Spacer(Modifier.width(4.dp))
    }
}

/** The page when no changelog could be read: the warning icon over the message, as the empty states of the views. */
@Composable
private fun ReleaseHistoryEmptyState(title: String) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp).testTag("stone.emptyState"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseEntry(entry: ReleaseHistoryEntry, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.release_history_expanded else R.string.release_history_collapsed)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        // The header is the one focus stop of a release (Tab walks the history, Enter expands or collapses it) and
        // reads version, date, counts and state in one go.
        Column(
            modifier = Modifier
                .testTag("stone.releaseHistory.entry.${entry.version}")
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state }
                .padding(
                    start = ReleaseHistoryDefaults.horizontalPadding,
                    end = ReleaseHistoryDefaults.headerEndPadding,
                    top = ReleaseHistoryDefaults.verticalPadding,
                    bottom = if (expanded) 0.dp else ReleaseHistoryDefaults.verticalPadding,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (entry.date != null) {
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).rotate(chevronRotation),
                )
            }
            val counts = entry.categoryCounts
            if (!expanded && counts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp).testTag("stone.releaseHistory.counts.${entry.version}"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    counts.forEach { (tag, count) -> CategoryChip(tag, count) }
                }
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stone.releaseHistory.body.${entry.version}")
                    // A screen reader reads the items of a release in one go after its header.
                    .semantics(mergeDescendants = true) {}
                    .padding(
                        start = ReleaseHistoryDefaults.horizontalPadding,
                        end = ReleaseHistoryDefaults.horizontalPadding,
                        bottom = ReleaseHistoryDefaults.verticalPadding,
                    ),
            ) {
                entry.groups.forEachIndexed { index, group ->
                    val top = if (index == 0) 10.dp else ReleaseHistoryDefaults.groupSpacing
                    if (group.tag != null) CategoryChip(group.tag, count = null, modifier = Modifier.padding(top = top))
                    group.items.forEachIndexed { itemIndex, text ->
                        val itemTop = if (group.tag == null && itemIndex == 0) top else ReleaseHistoryDefaults.itemSpacing
                        BulletItem(text, Modifier.padding(top = itemTop))
                    }
                }
            }
        }
    }
}

/** A category label (primary container, 10 dp corners), with the item count in the collapsed summary. */
@Composable
private fun CategoryChip(tag: String, count: Int?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = tag, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = color)
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(text = count.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

/**
 * One item: a drawn dot (not a font glyph, so it looks the same with every font) centered on the first line, the text
 * beside it; wrapped lines align with the text, not with the dot. Markdown links in the text show as link text in the
 * primary color, underlined, and open in the browser (Q160 A).
 */
@Composable
private fun BulletItem(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val firstLine: Dp = with(LocalDensity.current) { if (style.lineHeight.isSpecified) style.lineHeight.toDp() else 20.dp }
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inline = remember(text) { ReleaseHistoryInlineParser.parse(text) }
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val body = remember(inline, linkColor, uriHandler) {
        buildAnnotatedString {
            append(inline.text)
            val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            // Our own listener: with the default one, a device without a browser crashes the page (IllegalArgumentException).
            inline.links.forEach { link ->
                addLink(LinkAnnotation.Url(link.url, styles) { runCatching { uriHandler.openUri(link.url) } }, link.start, link.end)
            }
        }
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.width(ReleaseHistoryDefaults.bulletSlot).height(firstLine), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .padding(start = ReleaseHistoryDefaults.bulletStart)
                    .size(ReleaseHistoryDefaults.bulletSize)
                    .background(dotColor, CircleShape),
            )
        }
        Text(text = body, style = style, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
