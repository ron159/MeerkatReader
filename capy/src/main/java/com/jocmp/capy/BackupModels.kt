package com.jocmp.capy

data class SavedSearchBackupEntry(
    val id: String,
    val name: String,
    val query: String?,
    val showUnreadBadge: Boolean,
    val articleIDs: List<String>,
)

data class ArticleBackupReference(
    val id: String,
    val url: String?,
)
