package com.capyreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTtsVoiceSelectorTest {
    private val voices = listOf(
        ArticleTtsVoice(
            id = "en-network",
            languageTag = "en-US",
            requiresNetwork = true,
        ),
        ArticleTtsVoice(
            id = "en-local",
            languageTag = "en-US",
            requiresNetwork = false,
        ),
        ArticleTtsVoice(
            id = "zh-local",
            languageTag = "zh-CN",
            requiresNetwork = false,
        ),
    )

    @Test
    fun `uses requested installed voice`() {
        val selected = ArticleTtsVoiceSelector.select(
            voices = voices,
            configuration = ArticleTtsConfiguration(
                languageTag = "zh-CN",
                voiceID = "zh-local",
            ),
            deviceLanguageTag = "en-US",
        )

        assertEquals("zh-local", selected?.id)
    }

    @Test
    fun `missing voice falls back to local voice in selected language`() {
        val selected = ArticleTtsVoiceSelector.select(
            voices = voices,
            configuration = ArticleTtsConfiguration(
                languageTag = "en-US",
                voiceID = "removed-voice",
            ),
            currentVoiceID = "en-network",
            deviceLanguageTag = "zh-CN",
        )

        assertEquals("en-local", selected?.id)
    }
}
