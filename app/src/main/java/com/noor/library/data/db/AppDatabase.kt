package com.noor.library.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class, SectionEntity::class, PassageEntity::class,
        BookmarkEntity::class, NoteEntity::class, HistoryEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun content(): ContentDao
    abstract fun user(): UserDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "noor.db").build()
    }
}
