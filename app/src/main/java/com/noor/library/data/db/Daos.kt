package com.noor.library.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM books ORDER BY CASE id WHEN 'quran' THEN 0 WHEN 'mafatih' THEN 1 WHEN 'sahifa' THEN 2 ELSE 3 END")
    fun books(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun book(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM sections WHERE bookId = :bookId ORDER BY sectionType, number")
    fun sections(bookId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE bookId = :bookId AND sectionType = :type AND number = :number LIMIT 1")
    suspend fun section(bookId: String, type: String, number: Int): SectionEntity?

    @Query("SELECT * FROM passages WHERE bookId = :bookId AND sectionType = :type AND sectionNumber = :number ORDER BY passageNumber")
    fun passages(bookId: String, type: String, number: Int): Flow<List<PassageEntity>>

    /** Next/previous section in reading order within the same book and type. */
    @Query("SELECT * FROM sections WHERE bookId = :bookId AND sectionType = :type AND number > :number ORDER BY number LIMIT 1")
    suspend fun nextSection(bookId: String, type: String, number: Int): SectionEntity?

    @Query("SELECT * FROM sections WHERE bookId = :bookId AND sectionType = :type AND number < :number ORDER BY number DESC LIMIT 1")
    suspend fun previousSection(bookId: String, type: String, number: Int): SectionEntity?

    @Query(
        "SELECT * FROM passages WHERE (:bookId = '' OR bookId = :bookId) AND " +
            "(persianNorm LIKE '%' || :q || '%' OR arabicNorm LIKE '%' || :q || '%') LIMIT 300"
    )
    suspend fun searchNormalized(q: String, bookId: String): List<PassageEntity>

    @Query(
        "SELECT * FROM passages WHERE (:bookId = '' OR bookId = :bookId) AND " +
            "(persianNorm LIKE '%' || :qNorm || '%' OR arabicText LIKE '%' || :qRaw || '%') LIMIT 300"
    )
    suspend fun searchExactArabic(qNorm: String, qRaw: String, bookId: String): List<PassageEntity>

    @Query(
        "SELECT p.* FROM passages p JOIN sections s ON s.id = p.sectionId " +
            "WHERE (:bookId = '' OR s.bookId = :bookId) AND s.title LIKE '%' || :q || '%' GROUP BY s.id LIMIT 100"
    )
    suspend fun searchSectionTitles(q: String, bookId: String): List<PassageEntity>

    @Query("SELECT * FROM passages WHERE bookId = :bookId ORDER BY id LIMIT 1 OFFSET :offset")
    suspend fun passageAtOffset(bookId: String, offset: Int): PassageEntity?

    @Query("SELECT COUNT(*) FROM passages WHERE bookId = :bookId")
    suspend fun passageCount(bookId: String): Int

    @Query("SELECT title FROM sections WHERE bookId = :bookId AND sectionType = :type AND number = :number")
    suspend fun sectionTitle(bookId: String, type: String, number: Int): String?

    @Insert
    suspend fun insertBooks(items: List<BookEntity>)

    @Insert
    suspend fun insertSection(item: SectionEntity): Long

    @Insert
    suspend fun insertPassages(items: List<PassageEntity>)

    @Query("DELETE FROM passages")
    suspend fun clearPassages()

    @Query("DELETE FROM sections")
    suspend fun clearSections()

    @Query("DELETE FROM books")
    suspend fun clearBooks()
}

@Dao
interface UserDao {
    // ---- bookmarks
    @Query("SELECT passageNumber FROM bookmarks WHERE bookId = :bookId AND sectionType = :type AND sectionNumber = :number")
    fun bookmarkedPassages(bookId: String, type: String, number: Int): Flow<List<Int>>

    @Query(
        "SELECT b.id AS id, b.bookId AS bookId, b.sectionType AS sectionType, b.sectionNumber AS sectionNumber, " +
            "b.passageNumber AS passageNumber, b.folder AS folder, b.createdAt AS createdAt, " +
            "p.arabicText AS arabicText, p.persianTranslation AS persianTranslation, s.title AS sectionTitle " +
            "FROM bookmarks b " +
            "LEFT JOIN passages p ON p.bookId = b.bookId AND p.sectionType = b.sectionType " +
            "AND p.sectionNumber = b.sectionNumber AND p.passageNumber = b.passageNumber " +
            "LEFT JOIN sections s ON s.bookId = b.bookId AND s.sectionType = b.sectionType AND s.number = b.sectionNumber " +
            "ORDER BY b.createdAt DESC"
    )
    fun bookmarksWithText(): Flow<List<BookmarkWithText>>

    @Query("SELECT * FROM bookmarks")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun bookmarkCount(): Flow<Int>

    @Query("SELECT DISTINCT folder FROM bookmarks ORDER BY folder")
    fun folders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(item: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND sectionType = :type AND sectionNumber = :number AND passageNumber = :passage")
    suspend fun deleteBookmark(bookId: String, type: String, number: Int, passage: Int)

    @Query("UPDATE bookmarks SET folder = :folder WHERE id = :id")
    suspend fun moveBookmark(id: Long, folder: String)

    // ---- notes
    @Query("SELECT * FROM notes WHERE bookId = :bookId AND sectionType = :type AND sectionNumber = :number")
    fun notesFor(bookId: String, type: String, number: Int): Flow<List<NoteEntity>>

    @Query(
        "SELECT n.id AS id, n.bookId AS bookId, n.sectionType AS sectionType, n.sectionNumber AS sectionNumber, " +
            "n.passageNumber AS passageNumber, n.content AS content, n.createdAt AS createdAt, " +
            "p.arabicText AS arabicText, s.title AS sectionTitle " +
            "FROM notes n " +
            "LEFT JOIN passages p ON p.bookId = n.bookId AND p.sectionType = n.sectionType " +
            "AND p.sectionNumber = n.sectionNumber AND p.passageNumber = n.passageNumber " +
            "LEFT JOIN sections s ON s.bookId = n.bookId AND s.sectionType = n.sectionType AND s.number = n.sectionNumber " +
            "ORDER BY n.createdAt DESC"
    )
    fun notesWithText(): Flow<List<NoteWithText>>

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(item: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM notes WHERE bookId = :bookId AND sectionType = :type AND sectionNumber = :number AND passageNumber = :passage")
    suspend fun deleteNoteAt(bookId: String, type: String, number: Int, passage: Int)

    // ---- history
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun recentHistory(limit: Int): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(item: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
