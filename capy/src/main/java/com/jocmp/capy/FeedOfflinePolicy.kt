package com.jocmp.capy

enum class FeedOfflinePolicy {
    ALWAYS,
    NEVER;

    companion object {
        fun parse(value: String?): FeedOfflinePolicy? {
            return entries.find { it.name == value }
        }
    }
}
