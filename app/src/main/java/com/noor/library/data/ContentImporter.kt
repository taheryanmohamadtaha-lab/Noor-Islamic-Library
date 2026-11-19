package com.noor.library.data

import android.content.Context
import androidx.room.withTransaction
import com.noor.library.data.db.AppDatabase
import com.noor.library.data.db.BookEntity
import com.noor.library.data.db.PassageEntity
import com.noor.library.data.db.SectionEntity
import com.noor.library.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Imports the authentic content files bundled in assets/content/ (produced by scripts/build_content.py
 * from licensed sources) into Room. Only content tables are rebuilt; bookmarks, notes and history are
 * keyed by book/section/passage numbers and therefore survive every content update.
 *
 * Nothing is ever fabricated here: a book whose JSON file is absent is stored with status "missing".
 */
class ContentImporter(private val context: Context, private val db: AppDatabase, private val prefs: Prefs) {

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        null
    }

    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val manifestText = readAsset("content/manifest.json")
        val contentVersion = manifestText?.let { JSONObject(it).optString("contentVersion", "none") } ?: "none"
        if (prefs.contentVersionNow() == contentVersion && contentVersion != "none") return@withContext

        val books = ArrayList<BookEntity>()
        val parsed = ArrayList<Pair<String, JSONObject>>()
        for (info in BookCatalog.all) {
            val text = readAsset("content/${info.id}.json")
            if (text == null) {
                books += BookEntity(
                    id = info.id, title = info.title, subtitle = info.subtitle, status = "missing",
                    source = "", translator = "", version = "", license = "", sectionCount = 0, passageCount = 0
                )
            } else {
                val obj = JSONObject(text)
                parsed += info.id to obj
                var sc = 0
                var pc = 0
                val secs = obj.getJSONArray("sections")
                for (i in 0 until secs.length()) {
                    sc++
                    pc += secs.getJSONObject(i).getJSONArray("passages").length()
                }
                books += BookEntity(
                    id = info.id, title = info.title, subtitle = info.subtitle,
                    status = obj.optString("status", "partial"),
                    source = obj.optString("source"),
                    translator = obj.optString("translator"),
                    version = obj.optString("version"),
                    license = obj.optString("license"),
                    sectionCount = sc, passageCount = pc
                )
            }
        }

        db.withTransaction {
            val dao = db.content()
            dao.clearPassages()
            dao.clearSections()
            dao.clearBooks()
            dao.insertBooks(books)
            for ((bookId, obj) in parsed) {
                val secs = obj.getJSONArray("sections")
                for (i in 0 until secs.length()) {
                    val s = secs.getJSONObject(i)
                    val type = s.optString("type", "section")
                    val number = s.getInt("number")
                    val passages = s.getJSONArray("passages")
                    val sectionId = dao.insertSection(
                        SectionEntity(
                            bookId = bookId, sectionType = type, number = number,
                            title = s.optString("title"), passageCount = passages.length()
                        )
                    )
                    val batch = ArrayList<PassageEntity>(passages.length())
                    for (j in 0 until passages.length()) {
                        val p = passages.getJSONObject(j)
                        val ar = p.optString("ar")
                        val fa = p.optString("fa")
                        batch += PassageEntity(
                            sectionId = sectionId, bookId = bookId, sectionType = type,
                            sectionNumber = number, passageNumber = p.getInt("n"),
                            arabicText = ar, persianTranslation = fa,
                            // PDF text layers split words unpredictably, so their search index ignores spaces
                            arabicNorm = TextNormalizer.normalize(ar).let { if (BookCatalog.byId(bookId).isPdf) it.replace(" ", "") else it },
                            persianNorm = TextNormalizer.normalize(fa),
                            sourceReference = p.optString("ref")
                        )
                    }
                    dao.insertPassages(batch)
                }
            }
        }
        prefs.setContentVersion(contentVersion)
    }
}
