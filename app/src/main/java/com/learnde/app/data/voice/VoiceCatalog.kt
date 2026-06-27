// Путь: app/src/main/java/com/learnde/app/data/voice/VoiceCatalog.kt
package com.learnde.app.data.voice

/** Один голос Gemini Live (native-audio). id = значение voiceName в API. */
data class GeminiVoice(
    val id: String,         // имя голоса для prebuiltVoiceConfig.voiceName
    val trait: String,      // официальная односложная характеристика Google
    val description: String // подробное описание на русском для выбора
)

/**
 * Реальный список из 30 голосов Gemini (native-audio output).
 * trait — официальный дескриптор Google; description — пояснение для пользователя.
 * recommended — лучшие универсальные голоса для ассистента (помечаются ★ в UI).
 */
object VoiceCatalog {

    val recommended: Set<String> = setOf("Sulafat", "Charon", "Kore", "Aoede", "Puck")

    val all: List<GeminiVoice> = listOf(
        GeminiVoice("Sulafat", "Warm", "Тёплый, мягкий, дружелюбный. Лучший универсальный голос для долгих бесед."),
        GeminiVoice("Charon", "Informative", "Глубокий, спокойный, авторитетный. Идеален для объяснений и новостей."),
        GeminiVoice("Kore", "Firm", "Нейтральный, профессиональный, уверенный. Деловой тон без лишних эмоций."),
        GeminiVoice("Aoede", "Breezy", "Лёгкий, расслабленный, «воздушный». Приятен для неспешного разговора."),
        GeminiVoice("Puck", "Upbeat", "Бодрый и энергичный, с улыбкой в голосе. Хорош для casual-общения."),
        GeminiVoice("Leda", "Youthful", "Молодой, лёгкий, приветливый. Повседневный помощник."),
        GeminiVoice("Fenrir", "Excitable", "Живой и эмоциональный, чуть взволнованный. Динамичные сценарии."),
        GeminiVoice("Orus", "Firm", "Собранный, твёрдый мужской голос. Уверенно звучит в инструкциях."),
        GeminiVoice("Callirrhoe", "Easy-going", "Непринуждённый и спокойный. Дружеская беседа без напряжения."),
        GeminiVoice("Autonoe", "Bright", "Яркий и звонкий. Заряжает энергией, хорош для коротких ответов."),
        GeminiVoice("Enceladus", "Breathy", "Придыхательный, мягкий, интимный тон. Спокойные личные диалоги."),
        GeminiVoice("Iapetus", "Clear", "Чистый и разборчивый. Когда важна чёткость дикции."),
        GeminiVoice("Umbriel", "Easy-going", "Расслабленный и ровный. Комфортный фоновый ассистент."),
        GeminiVoice("Algieba", "Smooth", "Гладкий и обволакивающий. Приятен для длинного чтения."),
        GeminiVoice("Despina", "Smooth", "Плавный и мягкий. Спокойный и располагающий."),
        GeminiVoice("Erinome", "Clear", "Чёткий и аккуратный. Хорош для деловых уведомлений."),
        GeminiVoice("Algenib", "Gravelly", "С лёгкой хрипотцой, характерный. Выделяется среди прочих."),
        GeminiVoice("Rasalgethi", "Informative", "Информативный и внятный. Справки и объяснения."),
        GeminiVoice("Laomedeia", "Upbeat", "Позитивный и бодрый. Поднимает настроение."),
        GeminiVoice("Achernar", "Soft", "Тихий и нежный. Деликатный тон для спокойных тем."),
        GeminiVoice("Alnilam", "Firm", "Уверенный и устойчивый. Серьёзный деловой голос."),
        GeminiVoice("Schedar", "Even", "Ровный и сбалансированный. Нейтральная подача без перепадов."),
        GeminiVoice("Gacrux", "Mature", "Зрелый и солидный. Звучит опытно и основательно."),
        GeminiVoice("Pulcherrima", "Forward", "Прямой и напористый. Активная, ведущая подача."),
        GeminiVoice("Achird", "Friendly", "Приветливый и тёплый. Дружелюбный помощник на каждый день."),
        GeminiVoice("Zubenelgenubi", "Casual", "Разговорный и неформальный, как друг по переписке."),
        GeminiVoice("Vindemiatrix", "Gentle", "Мягкий и заботливый. Успокаивающий тон."),
        GeminiVoice("Sadachbia", "Lively", "Оживлённый и подвижный. Энергичные диалоги."),
        GeminiVoice("Sadaltager", "Knowledgeable", "Звучит компетентно и вдумчиво. Эксперт-консультант."),
        GeminiVoice("Zephyr", "Bright", "Светлый и звонкий. Свежо и легко звучит."),
    )

    fun byId(id: String): GeminiVoice? = all.firstOrNull { it.id.equals(id, ignoreCase = true) }
}
