package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.ImagePreview
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.AppTheme
import com.capyreader.app.ui.articles.ArticleListFontScale
import com.capyreader.app.ui.articles.ArticleRowOptions
import com.capyreader.app.ui.articles.FaviconBadge
import com.capyreader.app.ui.articles.StyleProviders
import com.capyreader.app.ui.articles.list.ArticleListItem
import com.capyreader.app.ui.components.MeerkatSilhouetteIcon
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.settings.PreferenceSelect
import com.capyreader.app.ui.settings.SettingsSection
import com.capyreader.app.ui.theme.LocalAppTheme
import kotlin.math.roundToInt

@Immutable
data class ArticleListOptions(
    val imagePreview: ImagePreview,
    val imagePreviewOnLeft: Boolean,
    val showFeedIcons: Boolean,
    val showFeedName: Boolean,
    val showSummary: Boolean,
    val shortenTitles: Boolean,
    val fontScale: ArticleListFontScale,
    val updateFeedIcons: (show: Boolean) -> Unit,
    val updateFeedName: (show: Boolean) -> Unit,
    val updateImagePreview: (preview: ImagePreview) -> Unit,
    val updateImagePreviewOnLeft: (showOnLeft: Boolean) -> Unit,
    val updateSummary: (show: Boolean) -> Unit,
    val updateFontScale: (scale: ArticleListFontScale) -> Unit,
    val updateShortenTitles: (show: Boolean) -> Unit,
)

@Composable
fun ArticleListSettings(
    options: ArticleListOptions,
) {
    val fontScales = ArticleListFontScale.entries

    SettingsSection(title = stringResource(R.string.settings_section_article_list_appearance)) {
        PreviewArticleRow(options = options)

        RowItem {
            Column {
                Text(
                    text = stringResource(R.string.article_font_scale_label),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    steps = fontScales.size - 2,
                    valueRange = 0f..(fontScales.size - 1).toFloat(),
                    value = options.fontScale.ordinal.toFloat(),
                    onValueChange = {
                        options.updateFontScale(fontScales[it.roundToInt()])
                    }
                )
            }
        }

        RowItem {
            TextSwitch(
                onCheckedChange = options.updateFeedName,
                checked = options.showFeedName,
                title = stringResource(R.string.settings_article_list_feed_name)
            )
            TextSwitch(
                onCheckedChange = options.updateFeedIcons,
                checked = options.showFeedIcons,
                title = stringResource(R.string.settings_article_list_feed_icons)
            )
            TextSwitch(
                onCheckedChange = options.updateSummary,
                checked = options.showSummary,
                title = stringResource(R.string.settings_article_list_summary)
            )
            TextSwitch(
                onCheckedChange = options.updateShortenTitles,
                checked = options.shortenTitles,
                title = stringResource(R.string.settings_article_list_shorten_titles)
            )
        }

        PreferenceSelect(
            selected = options.imagePreview,
            update = options.updateImagePreview,
            options = ImagePreview.sorted,
            label = R.string.image_preview_label,
            disabledOption = ImagePreview.NONE,
            optionText = {
                stringResource(id = it.translationKey)
            }
        )

        RowItem {
            TextSwitch(
                onCheckedChange = options.updateImagePreviewOnLeft,
                checked = options.imagePreviewOnLeft,
                title = stringResource(R.string.settings_article_list_images_on_left),
                subtitle = stringResource(R.string.settings_article_list_images_on_left_summary),
                enabled = options.imagePreview.showInline(),
            )
        }
    }
}

@Composable
private fun PreviewArticleRow(options: ArticleListOptions) {
    val inlineImageOnLeft = options.imagePreviewOnLeft && options.imagePreview.showInline()
    val rowOptions = ArticleRowOptions(
        showIcon = options.showFeedIcons,
        showSummary = options.showSummary,
        showFeedName = options.showFeedName,
        imagePreview = options.imagePreview,
        imagePreviewOnLeft = options.imagePreviewOnLeft,
        fontScale = options.fontScale,
        shortenTitles = options.shortenTitles,
        dim = false,
    )
    val colors = ListItemDefaults.colors()
    val overlineColor = colors.overlineContentColor

    StyleProviders(options = rowOptions) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                )
        ) {
        ArticleListItem(
            headlineContent = {
                Text(
                    text = PREVIEW_TITLE,
                    maxLines = if (options.shortenTitles) 3 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
            },
            overlineContent = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                ) {
                    if (inlineImageOnLeft && options.showFeedIcons) {
                        FaviconBadge(url = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (options.showFeedName) {
                        Text(
                            text = PREVIEW_FEED_NAME,
                            color = overlineColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                    } else if (inlineImageOnLeft && options.showFeedIcons) {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        text = PREVIEW_TIME,
                        color = overlineColor,
                        maxLines = 1,
                    )
                }
            },
            supportingContent = if (options.showSummary || options.imagePreview == ImagePreview.LARGE) {
                {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        if (options.showSummary) {
                            Text(
                                text = PREVIEW_SUMMARY,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (options.imagePreview == ImagePreview.LARGE) {
                            PreviewImage(imagePreview = options.imagePreview)
                        }
                    }
                }
            } else {
                null
            },
            leadingContent = when {
                inlineImageOnLeft -> {
                    { PreviewImage(imagePreview = options.imagePreview) }
                }
                options.showFeedIcons -> {
                    { FaviconBadge(url = null) }
                }
                else -> null
            },
            leadingContentSize = if (inlineImageOnLeft) {
                when (options.imagePreview) {
                    ImagePreview.MEDIUM -> 84.dp
                    else -> 56.dp
                }
            } else {
                16.dp
            },
            trailingContent = if (options.imagePreview.showInline() && !inlineImageOnLeft) {
                { PreviewImage(imagePreview = options.imagePreview) }
            } else {
                null
            },
        )
        }
    }
}

@Composable
private fun PreviewImage(imagePreview: ImagePreview) {
    val sizeModifier = when (imagePreview) {
        ImagePreview.SMALL -> Modifier.size(56.dp)
        ImagePreview.MEDIUM -> Modifier.size(84.dp)
        else -> Modifier.fillMaxWidth().aspectRatio(3 / 2f)
    }

    val shape = MaterialTheme.shapes.small

    Box(
        contentAlignment = Alignment.Center,
        modifier = sizeModifier
            .monochromeBorder(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        MeerkatSilhouetteIcon(
            contentDescription = null,
            modifier = Modifier.size(
                when (imagePreview) {
                    ImagePreview.SMALL -> 48.dp
                    ImagePreview.MEDIUM -> 64.dp
                    else -> 80.dp
                }
            )
        )
    }
}

@Composable
private fun Modifier.monochromeBorder(shape: Shape): Modifier {
    val isMonochrome = LocalAppTheme.current.value == AppTheme.MONOCHROME

    return if (isMonochrome) {
        border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
            shape = shape,
        )
    } else {
        this
    }
}

private const val PREVIEW_TITLE = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua"
private const val PREVIEW_FEED_NAME = "Lorem Ipsum"
private const val PREVIEW_TIME = "3h"
private const val PREVIEW_SUMMARY = "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam."

@Preview
@Composable
private fun ArticleListSettingsPreview() {
    ArticleListSettings(
        options = ArticleListOptions(
            imagePreview = ImagePreview.default,
            imagePreviewOnLeft = false,
            showSummary = true,
            showFeedIcons = true,
            fontScale = ArticleListFontScale.LARGE,
            showFeedName = false,
            shortenTitles = true,
            updateImagePreview = {},
            updateImagePreviewOnLeft = {},
            updateSummary = {},
            updateFeedName = {},
            updateFeedIcons = {},
            updateFontScale = {},
            updateShortenTitles = {},
        )
    )
}
