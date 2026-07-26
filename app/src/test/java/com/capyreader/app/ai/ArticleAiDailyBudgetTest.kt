package com.capyreader.app.ai

import android.content.Context
import androidx.preference.PreferenceManager
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ArticleAiDailyBudgetTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var preferences: AppPreferences
    private var currentDay = LocalDate.of(2026, 7, 25)
    private lateinit var budget: ArticleAiDailyBudget

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        preferences = AppPreferences(context, InMemorySecretStore())
        preferences.clearAll()
        budget = ArticleAiDailyBudget(preferences.aiOptions) { currentDay }
    }

    @Test
    fun `successful previews exhaust same-day allowance`() {
        preferences.aiOptions.backgroundPreviewsDailyLimit.set(2)

        assertEquals(2, budget.remaining())
        assertTrue(budget.recordSuccess())
        assertEquals(1, budget.remaining())
        assertTrue(budget.recordSuccess())
        assertEquals(0, budget.remaining())
        assertFalse(budget.recordSuccess())
    }

    @Test
    fun `allowance resets on the next local calendar day`() {
        preferences.aiOptions.backgroundPreviewsDailyLimit.set(2)
        budget.recordSuccess()
        budget.recordSuccess()
        assertEquals(0, budget.remaining())

        currentDay = currentDay.plusDays(1)

        assertEquals(2, budget.remaining())
        assertEquals(
            "$currentDay|0",
            preferences.aiOptions.backgroundPreviewsDailyUsage.get(),
        )
    }

    @Test
    fun `invalid usage state resets safely`() {
        preferences.aiOptions.backgroundPreviewsDailyLimit.set(5)
        preferences.aiOptions.backgroundPreviewsDailyUsage.set("not-a-usage-record")

        assertEquals(5, budget.remaining())
        assertTrue(budget.recordSuccess())
        assertEquals(4, budget.remaining())
    }

    @Test
    fun `changing the configured limit updates remaining allowance`() {
        preferences.aiOptions.backgroundPreviewsDailyLimit.set(5)
        repeat(3) {
            budget.recordSuccess()
        }

        preferences.aiOptions.backgroundPreviewsDailyLimit.set(10)

        assertEquals(7, budget.remaining())
    }
}
