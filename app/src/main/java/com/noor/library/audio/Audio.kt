@file:OptIn(UnstableApi::class)

package com.noor.library.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Reciters offered by EveryAyah.com (free per-ayah recitations). Streaming + local caching. */
data class Reciter(val folder: String, val name: String)

object Reciters {
    val all = listOf(
        Reciter("Alafasy_128kbps", "مشاری راشد العفاسی"),
        Reciter("Husary_128kbps", "محمود خلیل الحصری"),
        Reciter("Abdul_Basit_Murattal_192kbps", "عبدالباسط عبدالصمد (مرتل)"),
        Reciter("Minshawy_Murattal_128kbps", "محمد صدیق المنشاوی (مرتل)"),
        Reciter("Abu_Bakr_Ash-Shaatree_128kbps", "ابوبکر الشاطری"),
    )

    fun byFolder(folder: String) = all.firstOrNull { it.folder == folder } ?: all.first()

    fun url(folder: String, surah: Int, ayah: Int): String =
        "https://everyayah.com/data/$folder/%03d%03d.mp3".format(surah, ayah)
}

object AudioCache {
    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(600L * 1024 * 1024),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { cache = it }
    }

    fun cachedFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Downloads every ayah of a surah into the cache so it plays offline later. */
    suspend fun downloadSurah(context: Context, folder: String, surah: Int, ayahCount: Int, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val ds = cachedFactory(context).createDataSourceForDownloading()
            for (n in 1..ayahCount) {
                CacheWriter(ds, DataSpec(Uri.parse(Reciters.url(folder, surah, n))), null, null).cache()
                onProgress(n)
            }
        }
}

data class AudioState(
    val playingSurah: Int = 0,
    val playingAyah: Int = 0,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val active: Boolean = false,
    val error: String? = null,
)

class AudioController(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = refresh()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.value = _state.value.copy(error = "پخش صوت ناموفق بود (اتصال اینترنت را بررسی کنید)")
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            controller = f.get().also { it.addListener(listener) }
            refresh()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun refresh() {
        val c = controller ?: return
        val id = c.currentMediaItem?.mediaId ?: ""
        val parts = id.split(":")
        _state.value = AudioState(
            playingSurah = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            playingAyah = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            isPlaying = c.isPlaying,
            speed = c.playbackParameters.speed,
            active = c.mediaItemCount > 0,
            error = null,
        )
    }

    fun playSurah(folder: String, surah: Int, surahTitle: String, ayahCount: Int, startAyah: Int) {
        val c = controller ?: return
        val reciter = Reciters.byFolder(folder)
        val items = (1..ayahCount).map { n ->
            MediaItem.Builder()
                .setMediaId("$surah:$n")
                .setUri(Reciters.url(folder, surah, n))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("سوره $surahTitle — آیه $n")
                        .setArtist(reciter.name)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, (startAyah - 1).coerceIn(0, ayahCount - 1), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun stop() { controller?.let { it.stop(); it.clearMediaItems() }; refresh() }
    fun setSpeed(v: Float) { controller?.setPlaybackSpeed(v) }
}
