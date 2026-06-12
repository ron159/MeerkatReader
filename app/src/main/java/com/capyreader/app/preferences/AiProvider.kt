package com.capyreader.app.preferences

import androidx.annotation.StringRes
import com.capyreader.app.R

enum class AiProvider(
    @param:StringRes val translationKey: Int,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    OPENAI_COMPATIBLE(
        translationKey = R.string.ai_provider_openai_compatible,
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
    ),
    DEEPSEEK(
        translationKey = R.string.ai_provider_deepseek,
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-v4-flash",
    );

    companion object {
        val default = OPENAI_COMPATIBLE
    }
}
