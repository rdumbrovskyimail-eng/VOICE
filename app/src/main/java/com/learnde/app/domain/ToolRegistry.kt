package com.learnde.app.domain

import com.learnde.app.domain.model.FunctionDeclarationConfig
import com.learnde.app.domain.model.ParameterConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реестр инструментов (function calling) для Gemini Live.
 *  • getDeclarations() — список функций, уходящий в SessionConfig.
 *  • execute() — выполнение по имени; возвращает JSON-строку для ToolResponse.
 * Побочные эффекты для UI (вывод текста на дашборд) остаются в SessionManager.
 */
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
                )
            ),
            required = listOf("text")
        ),
        FunctionDeclarationConfig(
            name = "show_pronunciations",
            description = "Показывает транскрипцию слова или фразы.",
            parameters = mapOf(
                "word" to ParameterConfig(
                    type = "STRING",
                    description = "Слово или фраза для отображения транскрипции."
                )
            ),
            required = listOf("word")
        )
    )

    fun execute(name: String, args: Map<String, String>): String = when (name) {
        "update_dashboard" -> """{"success": true}"""
        "show_pronunciations" -> """{"success": true}"""
        else -> """{"error": "Unknown function: $name"}"""
    }
}