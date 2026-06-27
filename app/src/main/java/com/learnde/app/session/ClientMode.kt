// Путь: app/src/main/java/com/learnde/app/session/ClientMode.kt
package com.learnde.app.session

/**
 * Режим клиента.
 *  NORMAL  — «дворовой», чистый инкогнито: ничего не сохраняется, привязка к промпту сессии.
 *  HISTORY — режим H: все диалоги копятся в БД, модель при каждом старте читает историю целиком,
 *            промпт фиксированный (меняется только через Clear), сжатие контекста выключено.
 *
 * (Режим CAM будет добавлен в следующем дропе камеры.)
 */
enum class ClientMode { NORMAL, HISTORY }
