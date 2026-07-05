package com.learnde.app.data.settings

import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue: AppSettings = AppSettings()

    // Используем lenient Json, чтобы не падать при добавлении новых полей
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            json.decodeFromString(
                AppSettings.serializer(),
                input.readBytes().decodeToString()
            )
        } catch (e: SerializationException) {
            e.printStackTrace()
            defaultValue
        } catch (e: Exception) {
            // Битый/усечённый файл (kill при записи) → дефолты вместо краш-лупа.
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                json.encodeToString(AppSettings.serializer(), t).encodeToByteArray()
            )
        }
    }
}