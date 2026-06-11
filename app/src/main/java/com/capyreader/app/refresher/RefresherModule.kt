package com.capyreader.app.refresher

import com.capyreader.app.articleimages.ArticleImageDownloadWorker
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
}
