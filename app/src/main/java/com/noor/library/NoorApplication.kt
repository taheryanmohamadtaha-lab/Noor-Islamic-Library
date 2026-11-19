package com.noor.library

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.noor.library.data.ContentImporter
import com.noor.library.data.Prefs
import com.noor.library.data.Repository
import com.noor.library.data.db.AppDatabase

class NoorApplication : Application() {
    lateinit var repository: Repository
        private set
    lateinit var importer: ContentImporter
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.create(this)
        val prefs = Prefs(this)
        repository = Repository(db, prefs)
        importer = ContentImporter(this, db, prefs)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "یادآور مطالعه", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "reading_reminder"
    }
}
