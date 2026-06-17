package com.capyreader.app.ai

object ArticleAiPrompts {
    const val TRANSLATE =
        "Translate the article body into {language} for an RSS reader.\n\nRules:\n- Return only the translated body text.\n- Do not include the title, URL, labels, explanations, or original text.\n- Preserve paragraph order and keep paragraph breaks with blank lines.\n- Keep names, brands, product models, numbers, prices, dates, URLs, and code unchanged unless they normally translate.\n- Do not summarize or add information that is not in the article.\n\nArticle body:\n\"\"\"\n{content}\n\"\"\""

    const val SUMMARIZE =
        "Summarize this RSS article in {language} for a reader deciding whether to read the full article.\n\nOutput:\n- Return 2 to 3 short paragraphs.\n- Focus on the main claim/event, why it matters, and important details.\n- Include concrete names, numbers, dates, and places when they are central.\n- Use only information from the article. Do not speculate.\n- Return only the summary, with no heading or preamble.\n\nTitle: {title}\nURL: {url}\nArticle body:\n\"\"\"\n{content}\n\"\"\""

    const val PREVIEW_SUMMARY =
        "Create a reading-list preview summary in {language} for this RSS article.\n\nOutput:\n- Return exactly one sentence.\n- Keep it under 35 words if possible.\n- State the core update or takeaway, not background filler.\n- Do not include the title, URL, bullets, labels, or explanations.\n- Use only information from the article. Do not speculate.\n\nTitle: {title}\nArticle body:\n\"\"\"\n{content}\n\"\"\""

    const val KEY_POINTS =
        "Extract the key points from this RSS article in {language}.\n\nOutput:\n- Return 3 to 6 bullet points.\n- Each bullet should be one concise sentence.\n- Prioritize facts, decisions, consequences, timelines, numbers, and named entities.\n- Avoid repeating the title or summary.\n- Use only information from the article. Do not speculate.\n\nTitle: {title}\nURL: {url}\nArticle body:\n\"\"\"\n{content}\n\"\"\""

    const val QUESTION =
        "Answer the user's question in {language} using only this RSS article.\n\nOutput:\n- Answer directly and concisely.\n- If the article does not contain enough information, say that the article does not answer it.\n- Do not use outside knowledge.\n- Do not include a heading or preamble.\n\nQuestion: {question}\n\nTitle: {title}\nURL: {url}\nArticle body:\n\"\"\"\n{content}\n\"\"\""

    const val DIGEST =
        "Create a reading digest in {language} from these RSS articles.\n\nOutput:\n- Start with 3 to 5 concise bullet points covering the most important updates across the set.\n- Then include a short \"Articles\" section with one sentence per article.\n- Preserve concrete names, numbers, dates, and sources when they matter.\n- Use only the supplied article excerpts. Do not speculate.\n- Do not include URLs unless they are essential to the meaning.\n\nArticles:\n\"\"\"\n{content}\n\"\"\""
}
