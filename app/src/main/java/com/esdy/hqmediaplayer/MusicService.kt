package com.esdy.hqmediaplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import kotlin.random.Random

class MusicService : Service() {
  companion object {
    var instance: MusicService? = null
    private const val CH = "esdy_music"
    fun start(c: Context, uris: List<String>, names: List<String>, artists: List<String>, index: Int) {
      val i = Intent(c, MusicService::class.java).setAction("start").putStringArrayListExtra("uris", ArrayList(uris.take(1500))).putStringArrayListExtra("names", ArrayList(names.take(1500)))
        .putStringArrayListExtra("artists", ArrayList(artists.take(1500))).putExtra("index", index)
      if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
    }
  }
  private lateinit var lib: LibVLC; private lateinit var mp: MediaPlayer; private var pfd: ParcelFileDescriptor? = null; private val h = Handler(Looper.getMainLooper())
  var uris = listOf<String>(); var names = listOf<String>(); var artists = listOf<String>(); var idx = 0
  var eqIdx = -1; var shuffle = false; var loop = 0; var abA = -1L; private var abB = -1L; private var eq: MediaPlayer.Equalizer? = null
  val hasMedia: Boolean get() = uris.isNotEmpty()
  val title: String get() = names.getOrElse(idx) { "" }
  val artist: String get() = artists.getOrElse(idx) { "" }
  val uri: String get() = uris.getOrElse(idx) { "" }
  val playing: Boolean get() = mp.isPlaying
  val time: Long get() = mp.time
  val length: Long get() = mp.length
  var rate: Float get() = mp.rate; set(v) { mp.rate = v }
  override fun onBind(i: Intent?): IBinder? = null
  private val abLoop = object : Runnable { override fun run() { if (abA >= 0 && abB >= 0 && mp.time >= abB) mp.time = abA; h.postDelayed(this, 300) } }
  override fun onCreate() { super.onCreate(); instance = this; lib = LibVLC(this, arrayListOf("--no-video")); mp = MediaPlayer(lib)
    mp.setEventListener { e -> if (e.type == MediaPlayer.Event.EndReached) h.post { onEnd() } }; h.postDelayed(abLoop, 300)
    if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CH, "Music playback", NotificationManager.IMPORTANCE_LOW)) }
  override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
    val it2 = i ?: return START_NOT_STICKY
    when (it2.action) {
      "start" -> { uris = it2.getStringArrayListExtra("uris") ?: arrayListOf(); names = it2.getStringArrayListExtra("names") ?: arrayListOf(); artists = it2.getStringArrayListExtra("artists") ?: arrayListOf(); abA = -1; abB = -1; load(it2.getIntExtra("index", 0)) }
      "toggle" -> { if (mp.isPlaying) mp.pause() else mp.play() }
      "next" -> next(); "prev" -> prev()
      "stop" -> { stopAll(); return START_NOT_STICKY }
    }
    val n = notif(); if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1, n)
    return START_NOT_STICKY
  }
  private fun load(i: Int) { if (uris.isEmpty()) return; idx = i.coerceIn(0, uris.size - 1); try { pfd?.close() } catch (_: Exception) {}; pfd = null
    val u = Uri.parse(uris[idx]); val m = if (u.scheme == "content") { pfd = contentResolver.openFileDescriptor(u, "r"); Media(lib, pfd!!.fileDescriptor) } else Media(lib, u)
    mp.media = m; m.release(); mp.play() }
  private fun onEnd() { when { loop == 1 -> load(idx); shuffle && uris.size > 1 -> load(Random.nextInt(uris.size)); idx < uris.size - 1 -> load(idx + 1); loop == 2 -> load(0) }; refresh() }
  fun refresh() { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notif()) }
  fun toggle() { if (mp.isPlaying) mp.pause() else mp.play(); refresh() }
  fun pause() { if (::mp.isInitialized) mp.pause(); refresh() }
  fun next() { if (uris.isEmpty()) return; load(if (shuffle) Random.nextInt(uris.size) else if (idx < uris.size - 1) idx + 1 else 0); refresh() }
  fun prev() { if (mp.time > 4000) mp.time = 0 else load(if (idx > 0) idx - 1 else 0); refresh() }
  fun jump(i: Int) { load(i); refresh() }
  fun seekTo(ms: Long) { mp.time = ms }
  fun abTap() { if (abA < 0) abA = mp.time else if (abB < 0) abB = mp.time else { abA = -1; abB = -1 } }
  fun setEq(preset: Int) { eqIdx = preset; try { if (preset < 0) mp.setEqualizer(null) else { eq = MediaPlayer.Equalizer.createFromPreset(preset); mp.setEqualizer(eq) } } catch (_: Throwable) {} }
  fun sleep(min: Int) { h.removeCallbacks(sleepRun); if (min > 0) h.postDelayed(sleepRun, min * 60000L) }
  private val sleepRun = Runnable { mp.pause(); refresh() }
  fun stopAll() { @Suppress("DEPRECATION") stopForeground(true); stopSelf() }
  private fun pi(a: String) = PendingIntent.getService(this, a.hashCode(), Intent(this, MusicService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  private fun act(res: Int, t: String, a: String) = Notification.Action.Builder(Icon.createWithResource(this, res), t, pi(a)).build()
  private fun notif(): Notification {
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
    return b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(artist.ifEmpty { "ESDY HQ Player" }).setOngoing(mp.isPlaying)
      .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MusicActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
      .addAction(act(android.R.drawable.ic_media_previous, "Previous", "prev"))
      .addAction(act(if (mp.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Play/Pause", "toggle"))
      .addAction(act(android.R.drawable.ic_media_next, "Next", "next")).addAction(act(android.R.drawable.ic_menu_close_clear_cancel, "Stop", "stop"))
      .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)).build()
  }
  override fun onDestroy() { super.onDestroy(); instance = null; h.removeCallbacksAndMessages(null); try { pfd?.close() } catch (_: Exception) {}; mp.stop(); mp.release(); lib.release() }
}
