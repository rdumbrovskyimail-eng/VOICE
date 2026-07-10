package com.learnde.app.attach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.learnde.app.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    data class Result(
        val images: List<ByteArray>,   // JPEG, готовы к отправке
        val extractedText: String,     // текст из текстовых файлов
        val accepted: List<String>,    // имена принятых файлов
        val skipped: List<String>,     // имена нечитаемых форматов
    )

    companion object {
        const val MAX_FILES = 20
        private const val MAX_IMAGE_SIDE = 1024
        private const val JPEG_QUALITY = 80
        private const val MAX_PDF_PAGES_TOTAL = 20
        private const val PDF_RENDER_SIDE = 1280
        private const val MAX_TEXT_CHARS_PER_FILE = 20_000
        private const val MAX_TEXT_CHARS_TOTAL = 120_000
    }

    suspend fun process(uris: List<Uri>): Result = withContext(Dispatchers.IO) {
        val images = ArrayList<ByteArray>()
        val accepted = ArrayList<String>()
        val skipped = ArrayList<String>()
        val textSb = StringBuilder()
        var pdfPagesUsed = 0

        for (uri in uris.take(MAX_FILES)) {
            val name = displayName(uri)
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            try {
                when {
                    mime.startsWith("image/") -> {
                        val jpeg = loadImageAsJpeg(uri)
                        if (jpeg != null) { images.add(jpeg); accepted.add(name) } else skipped.add(name)
                    }
                    mime == "application/pdf" || name.endsWith(".pdf", true) -> {
                        val before = images.size
                        pdfPagesUsed += renderPdf(uri, images, MAX_PDF_PAGES_TOTAL - pdfPagesUsed)
                        if (images.size > before) accepted.add(name) else skipped.add(name)
                    }
                    isTextual(mime, name) -> {
                        val text = readText(uri)
                        if (text.isNotBlank() && textSb.length < MAX_TEXT_CHARS_TOTAL) {
                            textSb.append("\n\n--- Файл: ").append(name).append(" ---\n")
                                .append(text.take(MAX_TEXT_CHARS_PER_FILE))
                            accepted.add(name)
                        } else skipped.add(name)
                    }
                    else -> skipped.add(name) // бинарные форматы модель не понимает
                }
            } catch (e: Exception) {
                logger.e("attach '$name' failed: ${e.message}")
                skipped.add(name)
            }
        }
        Result(images, textSb.toString().trim(), accepted, skipped)
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "файл"

    private fun isTextual(mime: String, name: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime in setOf("application/json", "application/xml", "application/csv",
                "application/javascript", "application/x-yaml")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("txt","md","csv","json","xml","log","kt","java","py","js","ts",
            "html","css","yaml","yml","ini","cfg","gradle","properties","sql","sh")
    }

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""

    private fun loadImageAsJpeg(uri: Uri): ByteArray? {
        val cr = context.contentResolver
        
        // 1. Читаем только размеры картинки (не загружая пиксели в память)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null

        // 2. Вычисляем коэффициент сжатия (inSampleSize) для экономии RAM
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2

        // 3. Загружаем уже уменьшенную копию
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } 
            ?: return null

        // 4. Точная подгонка размера (если inSampleSize сжал недостаточно)
        val scaled = downscale(src)
        if (scaled != src) src.recycle()
        
        return scaled.toJpeg(JPEG_QUALITY).also { scaled.recycle() }
    }

    private fun renderPdf(uri: Uri, out: MutableList<ByteArray>, pageBudget: Int): Int {
        if (pageBudget <= 0) return 0
        var rendered = 0
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
        pfd.use {
            PdfRenderer(it).use { renderer ->
                val pages = minOf(renderer.pageCount, pageBudget)
                for (i in 0 until pages) {
                    renderer.openPage(i).use { page ->
                        val scale = PDF_RENDER_SIDE.toFloat() / maxOf(page.width, page.height)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE) // прозрачность PDF → белый фон
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(bmp.toJpeg(JPEG_QUALITY)); bmp.recycle(); rendered++
                    }
                }
            }
        }
        return rendered
    }

    private fun downscale(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= MAX_IMAGE_SIDE) return src
        val s = MAX_IMAGE_SIDE.toFloat() / longest
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postScale(s, s) }, true)
    }

    private fun Bitmap.toJpeg(q: Int): ByteArray =
        ByteArrayOutputStream().use { compress(Bitmap.CompressFormat.JPEG, q, it); it.toByteArray() }
}