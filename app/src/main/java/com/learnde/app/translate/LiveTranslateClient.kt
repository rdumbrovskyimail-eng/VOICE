// Путь: app/src/main/java/com/learnde/app/translate/LiveTranslateClient.kt
//
// Тонкий WebSocket-клиент строго под модель gemini-3.5-live-translate-preview.
// 1:1 по официальной документации Gemini Live API → Live Translation:
//   • эндпоинт — тот же v1beta BidiGenerateContent (ключ в query), что и у основного клиента;
//   • setup: { model, generationConfig{ responseModalities:[AUDIO],
//             inputAudioTranscription:{}, outputAudioTranscription:{},
//             translationConfig:{ targetLanguageCode, echoTargetLanguage } } };
//   • вход:  realtimeInput.audio = base64 PCM16 16кГц mono LE (mimeType "audio/pcm;rate=16000");
//   • выход: serverContent.modelTurn.parts[].inlineData.data = base64 PCM16 24кГц.
// НЕТ tools / systemInstruction / speechConfig / temperature — модель их не поддерживает.
//
// Один экземпляр = перевод В ОДИН целевой язык. Для двунаправленного RU<->DE
// TranslatorManager поднимает ДВА таких клиента (target=de и target=ru).

package com.learnde.app.translate

import android.util.Base64
import com.learnde.app.domain.model.SessionConfig
import com.learnde.app.util.AppLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

sealed interface TranslateEvent {
    data object Ready : TranslateEvent
    data object TurnComplete : TranslateEvent
    data class InputText(val text: String, val lang: String?) : TranslateEvent
    data class OutputText(val text: String, val lang: String?) : TranslateEvent
    data class Audio(val pcm: ByteArray) : TranslateEvent
    data class Closed(val code: Int, val reason: String) : TranslateEvent
    data class Error(val message: String) : TranslateEvent
}

class LiveTranslateClient(
    private val targetLanguageCode: String,
    private val echoTargetLanguage: Boolean,
    private val logger: AppLogger,
) {
    companion object {
        const val MODEL = "models/gemini-3.5-live-translate-preview"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var lastSetup: String = ""

    @Volatile var isReady: Boolean = false
        private set

    val target: String get() = targetLanguageCode

    private val _events = MutableSharedFlow<TranslateEvent>(
        replay = 0, extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<TranslateEvent> = _events.asSharedFlow()

    fun connect(apiKey: String) {
        runCatching { webSocket?.close(1000, "reconnect") } // сброс предыдущего сокета при переподключении
        val url = "wss://${SessionConfig.WS_HOST}/${SessionConfig.WS_PATH}?key=${apiKey.trim()}"
        logger.d("Translate[$targetLanguageCode]: connecting…")
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                logger.d("Translate[$targetLanguageCode]: WS open HTTP ${response.code}")
                val setup = buildSetup()
                lastSetup = setup
                logger.d("Translate[$targetLanguageCode]: SETUP → ${setup.length} chars")
                ws.send(setup)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                parse(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                runCatching { parse(bytes.utf8()) }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                runCatching { ws.close(1000, null) }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isReady = false
                logger.w("Translate[$targetLanguageCode]: closed $code '$reason'")
                if (code == 1007 || code == 1008) {
                    logger.e("Translate[$targetLanguageCode]: REJECTED setup ($code). reason='$reason'")
                    logger.e("Translate[$targetLanguageCode]: setup was: $lastSetup")
                }
                _events.tryEmit(TranslateEvent.Closed(code, reason))
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                isReady = false
                val msg = response?.code?.let { "HTTP $it" } ?: (t.message ?: "network error")
                logger.e("Translate[$targetLanguageCode]: failure $msg", t)
                _events.tryEmit(TranslateEvent.Error(msg))
                _events.tryEmit(TranslateEvent.Closed(response?.code ?: 1006, t.message ?: ""))
            }
        })
    }

    /**
     * Структура setup.
     * ВАЖНО: inputAudioTranscription/outputAudioTranscription — поля ВЕРХНЕГО уровня setup
     * (сиблинги generationConfig), как в рабочем основном клиенте этого приложения.
     * В doc-примере они показаны внутри generationConfig — на реальном v1beta API это даёт
     * close 1007 (SDK переносит их сам). translationConfig — внутри generationConfig (док).
     */
    private fun buildSetup(): String = buildJsonObject {
        put("setup", buildJsonObject {
            put("model", MODEL)
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                put("translationConfig", buildJsonObject {
                    put("targetLanguageCode", targetLanguageCode)
                    put("echoTargetLanguage", echoTargetLanguage)
                })
            })
            put("inputAudioTranscription", buildJsonObject {})
            put("outputAudioTranscription", buildJsonObject {})
        })
    }.toString()

    fun sendAudio(pcm: ByteArray) {
        if (!isReady) return
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        webSocket?.send(
            """{"realtimeInput":{"audio":{"data":"$b64","mimeType":"audio/pcm;rate=${SessionConfig.INPUT_SAMPLE_RATE}"}}}"""
        )
    }

    fun disconnect() {
        isReady = false
        runCatching { webSocket?.close(1000, "bye") }
        webSocket = null
    }

    private fun parse(raw: String) {
        try {
            val root = json.parseToJsonElement(raw).jsonObject

            if (root.containsKey("setupComplete")) {
                isReady = true
                logger.d("Translate[$targetLanguageCode]: setupComplete ✓")
                _events.tryEmit(TranslateEvent.Ready)
            }

            val sc = root["serverContent"]?.jsonObject ?: return

            sc["inputTranscription"]?.jsonObject?.let { node ->
                val text = node["text"]?.jsonPrimitive?.content
                val lang = node["languageCode"]?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) _events.tryEmit(TranslateEvent.InputText(text, lang))
            }

            sc["outputTranscription"]?.jsonObject?.let { node ->
                val text = node["text"]?.jsonPrimitive?.content
                val lang = node["languageCode"]?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) _events.tryEmit(TranslateEvent.OutputText(text, lang))
            }

            if (sc["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) {
                _events.tryEmit(TranslateEvent.TurnComplete)
            }

            (sc["modelTurn"]?.jsonObject?.get("parts") as? JsonArray)?.forEach { part ->
                val data = part.jsonObject["inlineData"]?.jsonObject
                    ?.get("data")?.jsonPrimitive?.content
                if (!data.isNullOrBlank()) {
                    val pcm = runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull()
                    if (pcm != null) _events.tryEmit(TranslateEvent.Audio(pcm))
                }
            }
        } catch (e: Exception) {
            logger.e("Translate[$targetLanguageCode]: parse error ${e.message}")
        }
    }
}
