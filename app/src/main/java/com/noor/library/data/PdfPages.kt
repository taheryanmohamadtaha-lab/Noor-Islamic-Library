package com.noor.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders the pages of a bundled PDF exactly as printed (Android's built-in PdfRenderer).
 * Used for books whose text layer cannot be re-typeset without corrupting Arabic diacritics.
 */
class PdfPages private constructor(private val renderer: PdfRenderer) {
    val pageCount: Int = renderer.pageCount
    val aspect: Float   // height / width of the first page (all pages are A4)
    private val lock = Mutex()
    private val cache = object : LruCache<String, Bitmap>(80 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    init {
        val p = renderer.openPage(0)
        aspect = p.height.toFloat() / p.width.toFloat()
        p.close()
    }

    fun cached(index: Int, widthPx: Int): Bitmap? = cache.get("$index@$widthPx")

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        cache.get("$index@$widthPx")?.let { return@withContext it }
        lock.withLock {
            val page = renderer.openPage(index)
            try {
                val h = (widthPx * page.height.toFloat() / page.width).toInt()
                val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put("$index@$widthPx", bmp)
                bmp
            } finally {
                page.close()
            }
        }
    }

    fun close() {
        try { renderer.close() } catch (_: Exception) {}
    }

    companion object {
        /** Copies the asset to private storage (once per app version) and opens it. */
        suspend fun open(context: Context, assetPath: String, versionTag: String): PdfPages = withContext(Dispatchers.IO) {
            val name = assetPath.substringAfterLast('/').removeSuffix(".pdf")
            val dir = File(context.filesDir, "pdf").apply { mkdirs() }
            val target = File(dir, "${name}_$versionTag.pdf")
            if (!target.exists() || target.length() == 0L) {
                dir.listFiles()?.filter { it.name.startsWith("${name}_") }?.forEach { it.delete() }
                val tmp = File(dir, "${target.name}.tmp")
                context.assets.open(assetPath).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(target)
            }
            val fd = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfPages(PdfRenderer(fd))
        }
    }
}
