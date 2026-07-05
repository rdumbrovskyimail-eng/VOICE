package com.learnde.app.translate

/** Каталог языков синхронного переводчика (BCP-47 коды Live Translation API). */
object TranslatorLanguages {
    /** code → отображаемое имя. Порядок = порядок в дропдауне настроек. */
    val all: List<Pair<String, String>> = listOf(
        "ru" to "Русский",
        "de" to "Немецкий",
        "en" to "Английский",
        "uk" to "Украинский",
        "es" to "Испанский",
        "fr" to "Французский",
        "it" to "Итальянский",
        "pt" to "Португальский",
        "pl" to "Польский",
        "tr" to "Турецкий",
        "nl" to "Нидерландский",
        "cs" to "Чешский",
        "ar" to "Арабский",
        "zh" to "Китайский",
        "ja" to "Японский",
        "ko" to "Корейский",
        "hi" to "Хинди",
    )

    private val byCode = all.toMap()

    fun name(code: String): String = byCode[code.lowercase()] ?: code.uppercase()
}