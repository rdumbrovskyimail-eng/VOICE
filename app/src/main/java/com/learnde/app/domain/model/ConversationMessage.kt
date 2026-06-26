package com.learnde.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ConversationMessage(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentNote: String? = null,
    val attachmentUris: List<String> = emptyList()   // ← URI‑строки вложений (для превью)
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"

        fun user(text: String) = ConversationMessage(ROLE_USER, text)
        fun model(text: String) = ConversationMessage(ROLE_MODEL, text)
    }
}