package com.noor.library.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** status: "complete" | "partial" | "missing" */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val source: String,
    val translator: String,
    val version: String,
    val license: String,
    val sectionCount: Int,
    val passageCount: Int,
)

@Entity(
    tableName = "sections",
    indices = [Index(value = ["bookId", "sectionType", "number"], unique = true)]
)
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val sectionType: String,
    val number: Int,
    val title: String,
    val passageCount: Int,
)

@Entity(
    tableName = "passages",
    indices = [
        Index(value = ["bookId", "sectionType", "sectionNumber"]),
        Index(value = ["sectionId"])
    ]
)
data class PassageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val bookId: String,
    val sectionType: String,
    val sectionNumber: Int,
    val passageNumber: Int,
    val arabicText: String,
    val persianTranslation: String,
    val arabicNorm: String,
    val persianNorm: String,
    val sourceReference: String,
)

/**
 * User data is keyed by (bookId, sectionType, sectionNumber, passageNumber) — not by row id —
 * so a content update (which rebuilds books/sections/passages) never loses bookmarks or notes.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId", "sectionType", "sectionNumber", "passageNumber"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val sectionType: String,
    val sectionNumber: Int,
    val passageNumber: Int,
    val folder: String,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    indices = [Index(value = ["bookId", "sectionType", "sectionNumber", "passageNumber"], unique = true)]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val sectionType: String,
    val sectionNumber: Int,
    val passageNumber: Int,
    val content: String,
    val createdAt: Long,
)

@Entity(
    tableName = "history",
    indices = [Index(value = ["bookId", "sectionType", "sectionNumber"], unique = true)]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val sectionType: String,
    val sectionNumber: Int,
    val passageNumber: Int,
    val title: String,
    val visitedAt: Long,
)

/** Join result used by the bookmarks screen. */
data class BookmarkWithText(
    val id: Long,
    val bookId: String,
    val sectionType: String,
    val sectionNumber: Int,
    val passageNumber: Int,
    val folder: String,
    val createdAt: Long,
    val arabicText: String?,
    val persianTranslation: String?,
    val sectionTitle: String?,
)

data class NoteWithText(
    val id: Long,
    val bookId: String,
    val sectionType: String,
    val sectionNumber: Int,
    val passageNumber: Int,
    val content: String,
    val createdAt: Long,
    val arabicText: String?,
    val sectionTitle: String?,
)
