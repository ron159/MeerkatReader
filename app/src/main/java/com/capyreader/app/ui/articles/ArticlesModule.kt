package com.capyreader.app.ui.articles

import android.content.Context
import com.capyreader.app.R
import com.capyreader.app.ai.AiChatClient
import com.capyreader.app.ai.ArticleAiRepository
import com.capyreader.app.ai.OpenAiCompatibleChatClient
import com.capyreader.app.offline.ArticleOfflineAudioStore
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.ui.addintent.AddLinkViewModel
import com.capyreader.app.ui.articles.audio.AudioPlayerController
import com.capyreader.app.ui.articles.feeds.edit.EditFeedViewModel
import com.jocmp.capy.articles.ArticleRenderer
import com.jocmp.capy.articles.AudioPlayerLabels
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal val articlesModule = module {
    factory {
        AddFeedViewModel(
            account = get(),
            automaticBackupScheduler = get(),
        )
    }
    factory {
        AddLinkViewModel(
            account = get(),
            automaticBackupScheduler = get(),
        )
    }
    single<AiChatClient> {
        OpenAiCompatibleChatClient(
            httpClient = get(),
        )
    }
    single {
        ArticleAiRepository(
            context = get(),
            appPreferences = get(),
            account = get(),
            chatClient = get(),
            articleAiDigestRecords = get(),
            articleAiResultRecords = get(),
        )
    }
    single {
        AudioPlayerController(
            context = get()
        )
    }
    single {
        val context = get<Context>()
        val audioStore = get<ArticleOfflineAudioStore>()
        val template = context.resources.openRawResource(R.raw.template)
            .bufferedReader()
            .readText()

        ArticleRenderer(
            template = template,
            textSize = get<AppPreferences>().readerOptions.fontSize,
            fontOption = get<AppPreferences>().readerOptions.fontFamily,
            titleFontSize = get<AppPreferences>().readerOptions.titleFontSize,
            textAlignment = get<AppPreferences>().readerOptions.titleTextAlignment,
            titleFollowsBodyFont = get<AppPreferences>().readerOptions.titleFollowsBodyFont,
            enableHorizontalScroll = get<AppPreferences>().readerOptions.enableHorizontaPagination,
            audioPlayerLabels = AudioPlayerLabels(
                play = context.getString(R.string.audio_player_play),
                pause = context.getString(R.string.audio_player_pause),
            ),
            audioEnclosureURL = { article, enclosure ->
                audioStore.localURL(
                    articleID = article.id,
                    sourceURL = enclosure.url.toString(),
                    mimeType = enclosure.type,
                ) ?: enclosure.url.toString()
            },
        )
    }
    viewModel {
        val appPreferences = get<AppPreferences>()

        ArticleScreenViewModel(
            account = get(),
            appPreferences = appPreferences,
            notificationHelper = get(),
            application = get(),
            articleImageRecords = get(),
            articleImagePreloader = get(),
            articleImageDownloader = get(),
            articleImageStore = get(),
            articleImageCacheCleaner = get(),
            articleFullContentRecords = get(),
            articleAiRepository = get(),
            articleOfflinePackageDownloader = get(),
            articleRuleMatchRecords = get(),
            automaticBackupScheduler = get(),
        )
    }
    viewModel {
        EditFeedViewModel(
            account = get(),
        )
    }
    viewModel {
        EditFolderViewModel(
            account = get(),
            appPreferences = get(),
        )
    }
}
