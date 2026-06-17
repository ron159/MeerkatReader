package com.jocmp.capy

enum class ArticleIntegrationExportState {
    QUEUED,
    EXPORTING,
    EXPORTED,
    FAILED;

    companion object {
        fun from(value: String): ArticleIntegrationExportState {
            return entries.find { it.name == value } ?: FAILED
        }
    }
}
