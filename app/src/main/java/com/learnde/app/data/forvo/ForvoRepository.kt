package com.learnde.app.data.forvo

import androidx.datastore.core.DataStore
import com.learnde.app.data.settings.AppSettings
import com.learnde.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forvo Pronunciation API: слово → URL mp3 эталонного (top-rated) произношения.
 * Ключ берётся из AppSettings.forvoApiKey. Пустой ключ / не найдено / сбой → null (без падений).
 */
@Singleton
class ForvoRepository @Inject constructor(
    private val settingsStore: DataStore<AppSettings>,
    private val logger: AppLogger,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // Кэш «lang:слово → URL»: бережём лимит плана, слова повторяются.
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun standardPronunciationUrl(word: String, language: String = "de"): String? =
        withContext(Dispatchers.IO) {
            val key = settingsStore.data.first().forvoApiKey.trim()
            if (key.isEmpty()) {
                logger.w("Forvo: ключ не задан — пропускаю «$word»")
                return@withContext null
            }
            val cacheKey = "$language:${word.lowercase()}"
            cache[cacheKey]?.let { return@withContext it }

            // Параметры Forvo идут в ПУТИ. Кодируем слово как path-сегмент:
            // умляуты → %C3%xx, пробел → %20 (URLEncoder даёт '+', заменяем).
            val encoded = URLEncoder.encode(word, "UTF-8").replace("+", "%20")
            val url = "$HOST/key/$key/format/json/action/standard-pronunciation" +
                "/word/$encoded/language/$language"

            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        logger.w("Forvo: HTTP ${resp.code} для «$word»")
                        return@withContext null
                    }
                    val mp3 = parseFirstMp3(resp.body?.string().orEmpty())
                    if (mp3 != null) cache[cacheKey] = mp3
                    mp3
                }
            } catch (e: Exception) {
                logger.w("Forvo: сбой запроса для «$word»: ${e.message}")
                null
            }
        }

    // Ответ: { "items": [ { "pathmp3": "https://...", ... } ] }
    private fun parseFirstMp3(body: String): String? = runCatching {
        json.parseToJsonElement(body)
            .jsonObject["items"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("pathmp3")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?.replace("http://", "https://") // cleartext запрещён с API 28
    }.getOrNull()

    companion object {
        // Стандартный (free) план Forvo. Для коммерческого: apicommercial.forvo.com
        private const val HOST = "https://apifree.forvo.com"
    }
}