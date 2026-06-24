package com.learnde.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ConversationMessage(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"

        fun user(text: String) = ConversationMessage(ROLE_USER, text)
        fun model(text: String) = ConversationMessage(ROLE_MODEL, text)
    }
}