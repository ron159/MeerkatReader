package com.capyreader.app.preferences

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTtsTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `tts choices persist across preference instances`() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = AppPreferences(context).also(AppPreferences::clearAll)

        preferences.readerOptions.ttsLanguageTag.set("zh-CN")
        preferences.readerOptions.ttsVoiceID.set("zh-local")
        preferences.readerOptions.ttsSpeechRate.set(1.25f)

        val restored = AppPreferences(context)
        assertEquals("zh-CN", restored.readerOptions.ttsLanguageTag.get())
        assertEquals("zh-local", restored.readerOptions.ttsVoiceID.get())
        assertEquals(1.25f, restored.readerOptions.ttsSpeechRate.get())
    }
}
