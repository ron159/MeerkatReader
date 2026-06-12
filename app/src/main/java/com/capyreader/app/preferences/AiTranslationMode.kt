package com.capyreader.app.preferences

import androidx.annotation.StringRes
import com.capyreader.app.R

enum class AiTranslationMode(@param:StringRes val translationKey: Int) {
    REPLACE_ORIGINAL(R.string.ai_translation_mode_replace),
    PARALLEL(R.string.ai_translation_mode_parallel);

    companion object {
        val default = REPLACE_ORIGINAL
    }
}
