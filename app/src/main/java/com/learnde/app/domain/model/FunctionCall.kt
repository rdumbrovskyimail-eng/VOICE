package com.learnde.app.domain.model

data class FunctionCall(
    val name: String,
    val id: String,
    val args: Map<String, String>
)