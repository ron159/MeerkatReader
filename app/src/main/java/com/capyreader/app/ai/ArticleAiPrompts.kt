package com.capyreader.app.ai

object ArticleAiPrompts {
    const val TRANSLATE =
        "Translate the article body into {language}. Return only the translated body text. Do not include the title, URL, labels, explanations, or original text. Preserve paragraph order and separate paragraphs with a blank line.\n\n{content}"

    const val SUMMARIZE =
        "Summarize the article in {language}. Use 3 to 5 concise paragraphs. Return only the summary."

    const val KEY_POINTS =
        "Extract the key points from the article in {language}. Return a concise bullet list."
}
