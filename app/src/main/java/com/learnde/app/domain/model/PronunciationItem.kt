package com.learnde.app.domain.model

import androidx.compose.runtime.Immutable

/** Одно слово на экране произношения. audioUrl приходит позже (асинхронно из Forvo). */
@Immutable
data class PronunciationItem(
    val word: String,
    val audioUrl: String? = null,
    val status: Status = Status.Loading,
) {
    enum class Status { Loading, Ready, Error }
}