package com.jocmp.capy.opml

internal sealed class Outline {
    abstract val title: String

    data class FolderOutline(val folder: Folder) : Outline() {
        override val title: String
            get() = folder.title?.takeIf { it.isNotBlank() } ?: folder.text.orEmpty()
    }

    data class FeedOutline(val feed: Feed) : Outline() {
        override val title: String
            get() = feed.title ?: ""
    }
}
