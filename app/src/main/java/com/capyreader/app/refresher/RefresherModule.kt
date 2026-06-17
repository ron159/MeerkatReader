package com.capyreader.app.refresher

import com.capyreader.app.articleimages.ArticleImageDownloadWorker
import com.capyreader.app.ai.ArticleAiPreviewWorker
import com.capyreader.app.offline.ArticleOfflinePackageWorker
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

val refresherModule = module {
    single {
        FeedRefresher(
            account = get(),
            appContext = get(),
            notificationHelper = get(),
            appPreferences = get(),
            articleImagePreloader = get(),
            articleImageCacheCleaner = get(),
        )
    }
    single { RefreshScheduler(get(), get()) }
    worker { RefreshFeedsWorker(get(), get()) }
    worker { ArticleImageDownloadWorker(get(), get()) }
    worker { ArticleAiPreviewWorker(get(), get()) }
    worker { ArticleOfflinePackageWorker(get(), get()) }
}
