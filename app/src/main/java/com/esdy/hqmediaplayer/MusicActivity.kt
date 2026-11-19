package com.esdy.hqmediaplayer

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.util.Size
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.Executors

class MusicActivity : AppCompatActivity() {
  private val h = Handler(Looper.getMainLooper()); private val ex = Executors.newSingleThreadExecutor(); private val prefs by lazy { getSharedPreferences("esdy", 0) }
  private lateinit var deck: DeckView; private lateinit var seek: SeekBar; private var lrc: List<Lrc.L> = emptyList(); private var lrcFor = ""; private var lastIdx = -1; private var skinBase = -1; private val born = SystemClock.uptimeMillis()
  private fun t(id: Int) = findViewById<TextView>(id)
  private fun im(id: Int) = findViewById<ImageView>(id)
  private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
  private fun svc() = MusicService.instance

  override fun onCreate(b: Bundle?) {
    super.onCreate(b); setContentView(R.layout.activity_music); requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    deck = DeckView(this).also { findViewById<FrameLayout>(R.id.deckHost).addView(it, FrameLayout.LayoutParams(-1, -1)) }; deck.mode = prefs.getInt("skin", 0)
    findViewById<View>(R.id.musicRoot).background = if (T.mode(this) == 2) ColorDrawable(0xFF000000.toInt()) else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(T.dark(T.accent(this), .42f), 0xFF202022.toInt()))
    seek = findViewById(R.id.mSeek); t(R.id.mTitle).isSelected = true
    im(R.id.mClose).setOnClickListener { finish() }; im(R.id.mPlay).setOnClickListener { svc()?.toggle() }; im(R.id.mNext).setOnClickListener { svc()?.next() }; im(R.id.mPrev).setOnClickListener { svc()?.prev() }
    im(R.id.mShuf).setOnClickListener { svc()?.let { it.shuffle = !it.shuffle } }; im(R.id.mRep).setOnClickListener { svc()?.let { it.loop = (it.loop + 1) % 3 } }
    t(R.id.mAb).setOnClickListener { svc()?.abTap() }; im(R.id.mEq).setOnClickListener { eqDlg() }; t(R.id.mSpd).setOnClickListener { spdDlg() }
    im(R.id.mFav).setOnClickListener { val s = svc() ?: return@setOnClickListener; val f = (prefs.getStringSet("favs", emptySet()) ?: emptySet()).toMutableSet(); if (!f.add(s.uri)) f.remove(s.uri); prefs.edit().putStringSet("favs", HashSet(f)).apply() }
    im(R.id.mMore).setOnClickListener { moreDlg() }; t(R.id.mLyr).setOnClickListener { lyrics() }
    t(R.id.mQ).setOnClickListener { val s = svc() ?: return@setOnClickListener; AlertDialog.Builder(this).setTitle("Playing Queue").setItems(s.names.toTypedArray()) { _, i -> s.jump(i) }.show() }
    im(R.id.mShare).setOnClickListener { val s = svc() ?: return@setOnClickListener; try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, Uri.parse(s.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share")) } catch (_: Exception) {} }
    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) { if (u) svc()?.let { it.seekTo(it.length * p / 1000) } }
      override fun onStartTrackingTouch(sb: SeekBar) {}; override fun onStopTrackingTouch(sb: SeekBar) {} })
    tick()
  }
  private fun tick() {
    val s = svc()
    if (s == null || !s.hasMedia) { if (SystemClock.uptimeMillis() - born > 3000) { finish(); return } }
    else {
      t(R.id.mTitle).text = s.title; t(R.id.mArtist).text = s.artist; if (skinBase < 0) skinBase = kotlin.math.abs(s.uris.firstOrNull().hashCode()) % 8; deck.artist = s.artist; deck.title = s.title; deck.skin = (s.idx + skinBase) % 8; deck.spinning = s.playing; syncLyrics(s)
      if (s.length > 0) { deck.progress = s.time.toFloat() / s.length; seek.progress = (s.time * 1000 / s.length).toInt(); t(R.id.mCur).text = fmt(s.time); t(R.id.mDur).text = fmt(s.length) }
      im(R.id.mPlay).setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play); val on = 0xFFFF6B5A.toInt()
      im(R.id.mShuf).setColorFilter(if (s.shuffle) on else -1); im(R.id.mRep).setColorFilter(if (s.loop > 0) on else -1); im(R.id.mRep).setImageResource(if (s.loop == 1) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
      t(R.id.mAb).setTextColor(if (s.abA >= 0) on else -1); t(R.id.mSpd).text = "${s.rate}X"
      val fav = (prefs.getStringSet("favs", emptySet()) ?: emptySet()).contains(s.uri); im(R.id.mFav).setImageResource(if (fav) R.drawable.ic_fav_on else R.drawable.ic_fav); im(R.id.mFav).setColorFilter(if (fav) on else -1)
      if (s.idx != lastIdx) { lastIdx = s.idx; deck.art = null; val u = Uri.parse(s.uri)
        ex.execute { val bmp = try { if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(u, Size(600, 600), null) else null } catch (_: Throwable) { null }; runOnUiThread { deck.art = bmp } } }
    }
    h.postDelayed({ tick() }, 300)
  }
  private fun eqDlg() { val s = svc() ?: return; val n = (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
    AlertDialog.Builder(this).setTitle("Equalizer").setSingleChoiceItems((listOf("Off") + n).toTypedArray(), s.eqIdx + 1) { d, i -> s.setEq(i - 1); d.dismiss() }.show() }
  private fun spdDlg() { val s = svc() ?: return; val v = floatArrayOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
    AlertDialog.Builder(this).setTitle("Speed").setItems(v.map { "${it}x" }.toTypedArray()) { _, i -> s.rate = v[i] }.show() }
  private fun moreDlg() { val s = svc() ?: return
    AlertDialog.Builder(this).setItems(arrayOf("Skin: " + (if (deck.mode == 0) "Vinyl → Cassette" else "Cassette → Vinyl"), "Add to playlist", "Sleep timer", "Stop playback")) { _, i ->
      when (i) { 0 -> { deck.mode = 1 - deck.mode; prefs.edit().putInt("skin", deck.mode).apply() }
        1 -> addToPl(s)
        2 -> AlertDialog.Builder(this).setTitle("Sleep timer").setItems(arrayOf("Off", "15 min", "30 min", "60 min")) { _, w -> s.sleep(intArrayOf(0, 15, 30, 60)[w]) }.show()
        3 -> { s.stopAll(); finish() } } }.show() }
  private fun syncLyrics(s: MusicService) {
    if (s.uri != lrcFor) { lrcFor = s.uri; lrc = Lrc.parse(prefs.getString("lrc:" + s.uri, "") ?: "") }
    val i = Lrc.current(lrc, s.time); t(R.id.mLine).text = if (lrc.isEmpty()) "" else if (i >= 0) lrc[i].text else "♪" }
  private fun addToPl(s: MusicService) { val n = Playlists.names(this)
    AlertDialog.Builder(this).setTitle("Add to playlist").setItems((listOf("＋ New playlist") + n).toTypedArray()) { _, i ->
      if (i == 0) { val et = EditText(this); AlertDialog.Builder(this).setTitle("New playlist").setView(et).setPositiveButton("Create") { _, _ -> val nm = et.text.toString().trim()
        if (nm.isNotEmpty()) { Playlists.create(this, nm); Playlists.add(this, nm, s.uri, s.title, s.artist); Toast.makeText(this, "Added to $nm", Toast.LENGTH_SHORT).show() } }.show() }
      else { Playlists.add(this, n[i - 1], s.uri, s.title, s.artist); Toast.makeText(this, "Added to ${n[i - 1]}", Toast.LENGTH_SHORT).show() } }.show() }
  private fun lyrics() { val s = svc() ?: return; val k = "lrc:" + s.uri; val txt = prefs.getString(k, null)
    if (txt == null) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 9); return }
    val tv = TextView(this).apply { text = Lrc.parse(txt).map { it.text }.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: txt; textSize = 16f; setPadding(40, 20, 40, 20) }
    AlertDialog.Builder(this).setTitle("Lyrics").setView(ScrollView(this).apply { addView(tv) }).setNeutralButton("Replace") { _, _ -> prefs.edit().remove(k).apply(); lyrics() }.setPositiveButton("Close", null).show() }
  override fun onActivityResult(r: Int, c: Int, d: Intent?) { val u = d?.data ?: return; val s = svc() ?: return
    if (r == 9) try { val raw = contentResolver.openInputStream(u)!!.bufferedReader().readText(); prefs.edit().putString("lrc:" + s.uri, raw).apply(); lyrics() } catch (_: Exception) { Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show() } }
  override fun onDestroy() { super.onDestroy(); h.removeCallbacksAndMessages(null) }
}
