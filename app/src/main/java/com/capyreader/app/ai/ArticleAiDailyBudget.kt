package com.capyreader.app.ai

import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.DEFAULT_BACKGROUND_PREVIEW_DAILY_LIMIT
import java.time.LocalDate

internal class ArticleAiDailyBudget(
    private val aiOptions: AppPreferences.AiOptions,
    private val today: () -> LocalDate = LocalDate::now,
) {
    @Synchronized
    fun remaining(): Int {
        val limit = aiOptions.backgroundPreviewsDailyLimit.get()
            .coerceIn(MIN_DAILY_LIMIT, MAX_DAILY_LIMIT)
        val usage = currentUsage()

        return (limit - usage.count).coerceAtLeast(0)
    }

    @Synchronized
    fun recordSuccess(): Boolean {
        val limit = aiOptions.backgroundPreviewsDailyLimit.get()
            .coerceIn(MIN_DAILY_LIMIT, MAX_DAILY_LIMIT)
        val usage = currentUsage()
        if (usage.count >= limit) {
            return false
        }

        aiOptions.backgroundPreviewsDailyUsage.set(
            DailyUsage(
                day = usage.day,
                count = usage.count + 1,
            ).serialize()
        )
        return true
    }

    private fun currentUsage(): DailyUsage {
        val currentDay = today()
        val stored = DailyUsage.parse(aiOptions.backgroundPreviewsDailyUsage.get())
        if (stored?.day == currentDay) {
            return stored.copy(count = stored.count.coerceAtLeast(0))
        }

        return DailyUsage(day = currentDay, count = 0).also {
            aiOptions.backgroundPreviewsDailyUsage.set(it.serialize())
        }
    }

    private data class DailyUsage(
        val day: LocalDate,
        val count: Int,
    ) {
        fun serialize(): String {
            return "$day|$count"
        }

        companion object {
            fun parse(value: String): DailyUsage? {
                val parts = value.split('|', limit = 2)
                if (parts.size != 2) {
                    return null
                }

                val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
                    ?: return null
                val count = parts[1].toIntOrNull() ?: return null

                return DailyUsage(day = day, count = count)
            }
        }
    }

    companion object {
        const val DEFAULT_DAILY_LIMIT = DEFAULT_BACKGROUND_PREVIEW_DAILY_LIMIT
        const val MIN_DAILY_LIMIT = 1
        const val MAX_DAILY_LIMIT = 100
        val DAILY_LIMIT_OPTIONS = listOf(5, 10, DEFAULT_DAILY_LIMIT, 50)
    }
}
