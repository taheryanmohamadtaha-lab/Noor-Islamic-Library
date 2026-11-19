package com.noor.library.data

import com.noor.library.data.db.AppDatabase
import com.noor.library.data.db.BookmarkEntity
import com.noor.library.data.db.HistoryEntity
import com.noor.library.data.db.NoteEntity
import com.noor.library.data.db.PassageEntity
import com.noor.library.util.TextNormalizer
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class Repository(private val db: AppDatabase, val prefs: Prefs) {
    private val content = db.content()
    private val user = db.user()

    val books = content.books()
    fun book(id: String) = content.book(id)
    fun sections(bookId: String) = content.sections(bookId)
    fun passages(bookId: String, type: String, number: Int) = content.passages(bookId, type, number)
    suspend fun section(bookId: String, type: String, number: Int) = content.section(bookId, type, number)
    suspend fun nextSection(bookId: String, type: String, number: Int) = content.nextSection(bookId, type, number)
    suspend fun previousSection(bookId: String, type: String, number: Int) = content.previousSection(bookId, type, number)
    suspend fun sectionTitle(bookId: String, type: String, number: Int) = content.sectionTitle(bookId, type, number)
    suspend fun passageByPage(bookId: String, page: Int) = content.passageByPage(bookId, page)
    suspend fun firstPassageNumber(bookId: String, type: String, number: Int) = content.firstPassageNumber(bookId, type, number)
    suspend fun maxPage(bookId: String) = content.maxPassageNumber(bookId)
    fun pageBookmarked(bookId: String, page: Int) = user.pageBookmarkCount(bookId, page)
    fun pageNote(bookId: String, page: Int) = user.pageNote(bookId, page)

    // ---- search
    suspend fun search(query: String, bookId: String, ignoreHarakat: Boolean): List<PassageEntity> {
        val q = TextNormalizer.normalize(query)
        if (q.length < 2) return emptyList()
        val qc = q.replace(" ", "")
        val byText = if (ignoreHarakat) content.searchNormalized(q, qc, bookId)
        else content.searchExactArabic(q, query.trim(), qc, bookId)
        val byTitle = content.searchSectionTitles(query.trim(), bookId)
        return (byTitle + byText).distinctBy { it.id }
    }

    /** Verse/passage of the day: deterministic per calendar day, always real content. */
    suspend fun dailyPassage(): PassageEntity? {
        val day = LocalDate.now().toEpochDay().toInt()
        for (info in BookCatalog.all) {
            val count = content.passageCount(info.id)
            if (count > 0) return content.passageAtOffset(info.id, day % count)
        }
        return null
    }

    // ---- bookmarks & notes
    fun bookmarkedPassages(bookId: String, type: String, number: Int) = user.bookmarkedPassages(bookId, type, number)
    fun bookmarks() = user.bookmarksWithText()
    fun bookmarkCount() = user.bookmarkCount()
    fun folders() = user.folders()
    fun notes() = user.notesWithText()
    fun notesFor(bookId: String, type: String, number: Int) = user.notesFor(bookId, type, number)
    fun history(limit: Int = 20) = user.recentHistory(limit)

    suspend fun toggleBookmark(p: PassageEntity, currentlyBookmarked: Boolean, folder: String = DEFAULT_FOLDER) {
        if (currentlyBookmarked) {
            user.deleteBookmark(p.bookId, p.sectionType, p.sectionNumber, p.passageNumber)
        } else {
            user.insertBookmark(
                BookmarkEntity(
                    bookId = p.bookId, sectionType = p.sectionType, sectionNumber = p.sectionNumber,
                    passageNumber = p.passageNumber, folder = folder, createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeBookmark(b: com.noor.library.data.db.BookmarkWithText) =
        user.deleteBookmark(b.bookId, b.sectionType, b.sectionNumber, b.passageNumber)

    suspend fun moveBookmark(id: Long, folder: String) = user.moveBookmark(id, folder)

    suspend fun saveNote(p: PassageEntity, text: String) {
        if (text.isBlank()) {
            user.deleteNoteAt(p.bookId, p.sectionType, p.sectionNumber, p.passageNumber)
        } else {
            user.upsertNote(
                NoteEntity(
                    bookId = p.bookId, sectionType = p.sectionType, sectionNumber = p.sectionNumber,
                    passageNumber = p.passageNumber, content = text.trim(), createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteNote(id: Long) = user.deleteNote(id)

    suspend fun recordVisit(bookId: String, type: String, number: Int, passage: Int) {
        val title = content.sectionTitle(bookId, type, number) ?: return
        user.upsertHistory(
            HistoryEntity(
                bookId = bookId, sectionType = type, sectionNumber = number,
                passageNumber = passage, title = title, visitedAt = System.currentTimeMillis()
            )
        )
        prefs.savePosition(bookId, type, number, passage)
    }

    suspend fun clearHistory() = user.clearHistory()

    // ---- backup / restore (user data only; keyed by numbers so it survives content updates)
    suspend fun exportUserData(): String {
        val root = JSONObject()
        root.put("app", "noor-islamic-library")
        root.put("format", 1)
        val bm = JSONArray()
        for (b in user.allBookmarks()) {
            bm.put(JSONObject().put("book", b.bookId).put("type", b.sectionType).put("section", b.sectionNumber)
                .put("passage", b.passageNumber).put("folder", b.folder).put("createdAt", b.createdAt))
        }
        val nt = JSONArray()
        for (n in user.allNotes()) {
            nt.put(JSONObject().put("book", n.bookId).put("type", n.sectionType).put("section", n.sectionNumber)
                .put("passage", n.passageNumber).put("content", n.content).put("createdAt", n.createdAt))
        }
        root.put("bookmarks", bm)
        root.put("notes", nt)
        return root.toString(2)
    }

    /** Returns number of imported items, or -1 when the text is not a valid backup. */
    suspend fun importUserData(text: String): Int {
        return try {
            val root = JSONObject(text)
            if (root.optString("app") != "noor-islamic-library") return -1
            var count = 0
            val bm = root.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bm.length()) {
                val o = bm.getJSONObject(i)
                user.insertBookmark(
                    BookmarkEntity(
                        bookId = o.getString("book"), sectionType = o.getString("type"),
                        sectionNumber = o.getInt("section"), passageNumber = o.getInt("passage"),
                        folder = o.optString("folder", DEFAULT_FOLDER), createdAt = o.optLong("createdAt")
                    )
                )
                count++
            }
            val nt = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until nt.length()) {
                val o = nt.getJSONObject(i)
                user.upsertNote(
                    NoteEntity(
                        bookId = o.getString("book"), sectionType = o.getString("type"),
                        sectionNumber = o.getInt("section"), passageNumber = o.getInt("passage"),
                        content = o.getString("content"), createdAt = o.optLong("createdAt")
                    )
                )
                count++
            }
            count
        } catch (e: Exception) {
            -1
        }
    }

    companion object {
        const val DEFAULT_FOLDER = "علاقه‌مندی‌ها"
    }
}
