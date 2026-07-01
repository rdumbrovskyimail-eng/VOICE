package com.learnde.app.domain

import com.learnde.app.domain.model.FunctionDeclarationConfig
import com.learnde.app.domain.model.ParameterConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor() {

    fun getDeclarations(): List<FunctionDeclarationConfig> = listOf(
        FunctionDeclarationConfig(
            name = "update_dashboard",
            description = "Выводит важный текст на экран пользователя (в дашборд). " +
                "Используй это для показа главных мыслей, перевода слов, формул или списков.",
            parameters = mapOf(
                "text" to ParameterConfig(
                    type = "STRING",
                    description = "Текст, который нужно показать на экране."
                ),
                "german_words" to ParameterConfig(
                    type = "ARRAY",
                    description = "СТРОГО только немецкие слова из текста. БЕЗ перевода, БЕЗ знаков препинания. " +
                        "Существительные обязательно с артиклем (der/die/das). Глаголы в инфинитиве. " +
                        "Правильный пример: [\"der Hund\", \"laufen\", \"schön\"]. " +
                        "Неправильный пример: [\"der Hund (собака)\", \"lief\"].",
                    items = ParameterConfig(
                        type = "STRING",
                        description = "Немецкое слово в начальной форме."
                    )
                )
            ),
            required = listOf("text") // german_words не обязателен, если немецких слов нет
        )
    )

    fun execute(name: String, args: Map<String, String>): String = when (name) {
        "update_dashboard" -> """{"success": true}"""
        else -> """{"error": "Unknown function: $name"}"""
    }
}