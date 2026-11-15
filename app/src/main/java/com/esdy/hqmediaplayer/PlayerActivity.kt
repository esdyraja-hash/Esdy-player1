package com.esdy.hqmediaplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.*
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.random.Random

private val ENC_N = listOf("Auto", "UTF-8", "Windows-1252 (Western)", "Windows-1256 (Arabic)", "Windows-1251 (Cyrillic)", "ISO-8859-1", "GBK (Chinese)", "Big5", "Shift_JIS")
private val ENC_V = listOf("", "UTF-8", "CP1252", "CP1256", "CP1251", "ISO-8859-1", "GBK", "Big5", "Shift_JIS")
private val LANG_N = listOf("Auto", "English", "Tamil", "Hindi", "Telugu", "Malayalam", "Kannada", "Arabic", "French", "Spanish")
private val LANG_V = listOf("", "eng", "tam", "hin", "tel", "mal", "kan", "ara", "fra", "spa")
private val SIZE_N = listOf("Small", "Normal", "Large", "Extra large"); private val SIZE_V = listOf(24, 16, 12, 9)
private val COLOR_N = listOf("White", "Yellow", "Cyan", "Green"); private val COLOR_V = listOf(16777215, 16776960, 65535, 65280)
private val OUT_N = listOf("None", "Thin", "Normal", "Thick"); private val OUT_V = listOf(0, 2, 4, 6)
private val BG_N = listOf("None", "Semi-transparent", "Solid"); private val BG_V = listOf(0, 128, 255)
private val POS_N = listOf("Bottom", "Slightly higher", "Higher"); private val POS_V = listOf(0, 40, 90)

private val ICONS = mapOf("night" to R.drawable.ic_moon, "cust" to R.drawable.ic_edit, "shuf" to R.drawable.ic_shuffle, "loop" to R.drawable.ic_repeat, "mute" to R.drawable.ic_mute, "sleep" to R.drawable.ic_timer, "afx" to R.drawable.ic_wave, "eq" to R.drawable.ic_tune, "shot" to R.drawable.ic_camera, "bg" to R.drawable.ic_headset, "rot" to R.drawable.ic_rotate, "mir" to R.drawable.ic_flip)

class PlayerActivity : AppCompatActivity() {
  private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
  private class QA(val id: String, val icon: String, val label: String, val act: () -> Unit, val on: () -> Boolean = { false })
  private lateinit var lib: LibVLC; private lateinit var mp: MediaPlayer; private lateinit var am: AudioManager
  private lateinit var vlc: VLCVideoLayout; private lateinit var hud: TextView; private lateinit var seek: SeekBar
  private lateinit var top: View; private lateinit var bottom: View; private lateinit var qa: LinearLayout; private lateinit var night: View
  private lateinit var panelHost: View; private lateinit var panel: LinearLayout; private lateinit var bPlay: ImageButton; private lateinit var bDec: Button; private lateinit var bUnlock: View
  private val h = Handler(Looper.getMainLooper()); private val prefs by lazy { getSharedPreferences("esdy", 0) }
  private var uris = listOf<String>(); private var names = listOf<String>(); private var idx = 0; private var pfd: ParcelFileDescriptor? = null
  private var hw = 0; private var locked = false; private var uiOn = true; private var loopMode = 0; private var shuffle = false; private var muted = false
  private var bg = false; private var mirror = false; private var nightOn = false; private var abA = -1L; private var abB = -1L; private var sleepOn = false; private var sleepEnd = false
  private var rotI = 0; private var aspI = 0; private var speed = 1f; private var boost = 100; private var zoom = 1f
  private var eq: MediaPlayer.Equalizer? = null; private var eqOn = false; private var eqPreset = -1
  private val scales = arrayOf(MediaPlayer.ScaleType.SURFACE_BEST_FIT, MediaPlayer.ScaleType.SURFACE_FIT_SCREEN, MediaPlayer.ScaleType.SURFACE_FILL, MediaPlayer.ScaleType.SURFACE_16_9, MediaPlayer.ScaleType.SURFACE_4_3, MediaPlayer.ScaleType.SURFACE_ORIGINAL)
  private val scaleNames = listOf("Best fit", "Crop", "Stretch", "16:9", "4:3", "Original")
  private val orients = intArrayOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
  private val orientNames = listOf("Landscape", "Portrait", "Auto / sensor")
  private var mode = 0; private var sx = 0f; private var sy = 0f; private var startT = 0L; private var startBr = .5f; private var startVol = 0; private var lastTap = 0L; private var fast = false
  private val longP = Runnable { if (mode == 0 && !locked) { fast = true; mp.rate = 2f; hud("2x") } }
  private val hideHud = Runnable { hud.visibility = View.GONE }; private val hideUi = Runnable { if (mp.isPlaying && panelHost.visibility != View.VISIBLE) showUi(false) }
  private val sgd by lazy { ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(d: ScaleGestureDetector): Boolean { zoom = (zoom * d.scaleFactor).coerceIn(1f, 4f); vlc.scaleX = zoom * (if (mirror) -1 else 1); vlc.scaleY = zoom; hud("${(zoom * 100).toInt()}%"); return true } }) }

  override fun onCreate(b: Bundle?) {
    super.onCreate(b); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // screen stays awake while playing
    setContentView(R.layout.activity_player); requestedOrientation = orients[0]; immersive()
    am = getSystemService(AUDIO_SERVICE) as AudioManager
    MusicService.instance?.pause()
    vlc = findViewById(R.id.vlc); hud = findViewById(R.id.hud); seek = findViewById(R.id.seek); top = findViewById(R.id.top); bottom = findViewById(R.id.bottom)
    qa = findViewById(R.id.qa); night = findViewById(R.id.night); panelHost = findViewById(R.id.panelHost); panel = findViewById(R.id.panel)
    bPlay = findViewById(R.id.bPlay); bDec = findViewById(R.id.bDec); bUnlock = findViewById(R.id.bUnlock)
    uris = intent.getStringArrayListExtra("uris") ?: listOfNotNull(intent.data?.toString())
    names = intent.getStringArrayListExtra("names") ?: uris.map { dname(it) }
    idx = intent.getIntExtra("index", 0); if (uris.isEmpty()) { finish(); return }
    lib = try { LibVLC(this, vlcArgs()) } catch (e: Exception) { LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames")) }; mp = MediaPlayer(lib); mp.attachViews(vlc, null, true, false)
    mp.setEventListener { e -> if (e.type == MediaPlayer.Event.EndReached) runOnUiThread { onEnd() } }
    fun b(id: Int, f: () -> Unit) = findViewById<View>(id).setOnClickListener { f(); wake() }
    b(R.id.bBack) { finish() }; b(R.id.bPlay) { if (mp.isPlaying) mp.pause() else mp.play() }
    b(R.id.bPrev) { if (mp.time > 5000 || idx == 0) mp.time = 0 else load(idx - 1) }; b(R.id.bNext) { if (idx < uris.size - 1) load(idx + 1) }
    b(R.id.bLock) { locked = true; showUi(false); bUnlock.visibility = View.VISIBLE }; b(R.id.bUnlock) { locked = false; bUnlock.visibility = View.GONE; showUi(true) }
    b(R.id.bAr) { aspI = (aspI + 1) % scales.size; mp.videoScale = scales[aspI]; hud(scaleNames[aspI]) }; b(R.id.bPip) { pip() }
    b(R.id.bAud) { audioP() }; b(R.id.bSub) { subP() }; b(R.id.bDec) { decoderDlg() }; b(R.id.bMore) { moreP() }
    seek.setOnSeekBarChangeListener(onSeek { p, user -> if (user && mp.length > 0) mp.time = mp.length * p / 1000 })
    vlc.setOnTouchListener { _, e -> gesture(e); true }; renderQA(); load(idx); tick()
  }
  private fun dname(s: String): String { val u = Uri.parse(s)
    if (u.scheme == "content") try { contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0)?.let { n -> return n } } } catch (_: Exception) {}
    return u.lastPathSegment?.substringAfterLast('/') ?: "Video" }
  private fun vlcArgs(): ArrayList<String> = arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--freetype-rel-fontsize=" + SIZE_V[prefs.getInt("sub_size_i", 1)], "--freetype-color=" + COLOR_V[prefs.getInt("sub_color_i", 0)],
    "--freetype-outline-thickness=" + OUT_V[prefs.getInt("sub_out_i", 2)], "--freetype-background-opacity=" + BG_V[prefs.getInt("sub_bg_i", 0)], "--sub-margin=" + POS_V[prefs.getInt("sub_pos_i", 0)]).also { if (prefs.getBoolean("sub_bold", false)) it.add("--freetype-bold") }
  private fun restartPlayer() { prefs.edit().putLong("pos:" + uris[idx], mp.time).apply(); recreate() }
  private fun choice(title: String, names: List<String>, key: String, def: Int, restart: Boolean) { AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(names.toTypedArray(), prefs.getInt(key, def)) { d, w ->
    prefs.edit().putInt(key, w).apply(); d.dismiss(); if (restart) restartPlayer() else { prefs.edit().putLong("pos:" + uris[idx], mp.time).apply(); load(idx) }; subSetP() }.show() }
  private fun subSetP() { openPanel("Subtitle settings") {
    item("Character encoding  ·  ${ENC_N[prefs.getInt("sub_enc_i", 0)]}") { choice("Character encoding", ENC_N, "sub_enc_i", 0, false) }
    item("Preferred subtitle language  ·  ${LANG_N[prefs.getInt("sub_lang_i", 0)]}") { choice("Preferred subtitle language", LANG_N, "sub_lang_i", 0, false) }
    stepper("Default sync", { "${prefs.getInt("sub_sync", 0)} ms" }) { d -> val v = prefs.getInt("sub_sync", 0) + d * 100; prefs.edit().putInt("sub_sync", v).apply(); mp.spuDelay = v * 1000L }
    item("APPEARANCE", 12f)
    item("Text size  ·  ${SIZE_N[prefs.getInt("sub_size_i", 1)]}") { choice("Text size", SIZE_N, "sub_size_i", 1, true) }
    item("Text color  ·  ${COLOR_N[prefs.getInt("sub_color_i", 0)]}") { choice("Text color", COLOR_N, "sub_color_i", 0, true) }
    item("Border  ·  ${OUT_N[prefs.getInt("sub_out_i", 2)]}") { choice("Border", OUT_N, "sub_out_i", 2, true) }
    item("Background  ·  ${BG_N[prefs.getInt("sub_bg_i", 0)]}") { choice("Background", BG_N, "sub_bg_i", 0, true) }
    item("Position  ·  ${POS_N[prefs.getInt("sub_pos_i", 0)]}") { choice("Position", POS_N, "sub_pos_i", 0, true) }
    check("Bold", prefs.getBoolean("sub_bold", false)) { prefs.edit().putBoolean("sub_bold", it).apply(); restartPlayer() }
    item("Size, color, border, background and position restart the video at the same spot.", 12f) } }
  private fun immersive() { @Suppress("DEPRECATION") window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES } }
  private fun onSeek(f: (Int, Boolean) -> Unit) = object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) = f(p, u); override fun onStartTrackingTouch(s: SeekBar) {}; override fun onStopTrackingTouch(s: SeekBar) {} }
  private fun fmt(ms: Long) = if (ms >= 3600000) "%d:%02d:%02d".format(ms / 3600000, ms / 60000 % 60, ms / 1000 % 60) else "%02d:%02d".format(ms / 60000, ms / 1000 % 60)
  private fun hud(t: String) { hud.text = t; hud.visibility = View.VISIBLE; h.removeCallbacks(hideHud); h.postDelayed(hideHud, 800) }
  private fun showUi(on: Boolean) { uiOn = on; val v = if (on) View.VISIBLE else View.GONE; top.visibility = v; bottom.visibility = v }
  private fun wake() { if (locked) return; showUi(true); h.removeCallbacks(hideUi); h.postDelayed(hideUi, 4000) }

  // ---------- loading / decoding
  private fun load(i: Int) {
    idx = i; prefs.edit().putString("last", uris[i]).apply(); val u = Uri.parse(uris[i]); findViewById<TextView>(R.id.title).text = names.getOrElse(i) { "" }
    try { pfd?.close() } catch (_: Exception) {}; pfd = null
    val m = if (u.scheme == "content") { pfd = contentResolver.openFileDescriptor(u, "r"); Media(lib, pfd!!.fileDescriptor) } else Media(lib, u)
    when (hw) { 0 -> m.setHWDecoderEnabled(true, false); 1 -> m.setHWDecoderEnabled(true, true); else -> m.setHWDecoderEnabled(false, false) }
    ENC_V[prefs.getInt("sub_enc_i", 0)].takeIf { it.isNotEmpty() }?.let { m.addOption(":subsdec-encoding=$it") }; LANG_V[prefs.getInt("sub_lang_i", 0)].takeIf { it.isNotEmpty() }?.let { m.addOption(":sub-language=$it") }; mp.media = m; m.release(); mp.rate = speed; mp.volume = if (muted) 0 else boost; mp.play(); abA = -1; abB = -1; h.postDelayed({ mp.spuDelay = prefs.getInt("sub_sync", 0) * 1000L }, 1500)
    val p = prefs.getLong("pos:" + uris[i], 0); if (p > 5000) h.postDelayed({ mp.time = p; hud("Resumed ${fmt(p)}") }, 900)
    applyEq()
  }
  private fun decoderDlg() { AlertDialog.Builder(this).setTitle("Select decoder").setSingleChoiceItems(arrayOf("HW decoder", "HW+ decoder", "SW decoder"), hw) { d, w ->
    hw = w; bDec.text = arrayOf("HW", "HW+", "SW")[w]; val t = mp.time; prefs.edit().putLong("pos:" + uris[idx], t).apply(); load(idx); d.dismiss() }.show() }
  private fun onEnd() { prefs.edit().remove("pos:" + uris[idx]).apply()
    when { sleepEnd -> finish(); loopMode == 1 -> load(idx); shuffle && uris.size > 1 -> load(Random.nextInt(uris.size)); idx < uris.size - 1 -> load(idx + 1); loopMode == 2 -> load(0); else -> finish() } }
  private fun tick() {
    if (mp.length > 0) { seek.progress = (mp.time * 1000 / mp.length).toInt(); findViewById<TextView>(R.id.tCur).text = fmt(mp.time); findViewById<TextView>(R.id.tDur).text = fmt(mp.length) }
    if (abA >= 0 && abB >= 0 && mp.time >= abB) mp.time = abA
    bPlay.setImageResource(if (mp.isPlaying) R.drawable.ic_pause else R.drawable.ic_play); h.postDelayed({ tick() }, 400)
  }

  // ---------- gestures
  private fun gesture(e: MotionEvent) {
    sgd.onTouchEvent(e); if (e.pointerCount > 1 || sgd.isInProgress) { mode = -1; h.removeCallbacks(longP); return }
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> { sx = e.x; sy = e.y; mode = 0; startT = mp.time; startVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        startBr = window.attributes.screenBrightness.let { if (it < 0) .5f else it }; h.postDelayed(longP, 500) }
      MotionEvent.ACTION_MOVE -> if (!locked && mode >= 0) { val dx = e.x - sx; val dy = e.y - sy
        if (mode == 0 && (abs(dx) > 40 || abs(dy) > 40)) { mode = if (abs(dx) > abs(dy)) 1 else if (sx < vlc.width / 2) 2 else 3; h.removeCallbacks(longP) }
        when (mode) {
          1 -> { val p = (startT + dx * 60).toLong().coerceIn(0, maxOf(0, mp.length)); mp.time = p; hud("${if (dx > 0) "+" else "−"}${abs(p - startT) / 1000}s   ${fmt(p)}") }
          2 -> { val br = (startBr - dy / vlc.height).coerceIn(.02f, 1f); window.attributes = window.attributes.also { it.screenBrightness = br }; hud("☀ ${(br * 100).toInt()}%") }
          3 -> { val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val v = (startVol - dy / vlc.height * mx).toInt().coerceIn(0, mx); am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); hud("🔊 ${v * 100 / mx}%") } } }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(longP); if (fast) { mp.rate = speed; fast = false; mode = -1 }
        if (mode == 0 && e.actionMasked == MotionEvent.ACTION_UP) {
          if (panelHost.visibility == View.VISIBLE) { panelHost.visibility = View.GONE; return }
          val n = SystemClock.uptimeMillis()
          if (!locked && n - lastTap < 300) { when { e.x < vlc.width * .35 -> { mp.time -= 10000; hud("−10s") }; e.x > vlc.width * .65 -> { mp.time += 10000; hud("+10s") }; else -> if (mp.isPlaying) mp.pause() else mp.play() }; lastTap = 0 }
          else { lastTap = n; if (locked) bUnlock.visibility = View.VISIBLE else if (uiOn) showUi(false) else wake() } } }
    }
  }

  // ---------- quick actions
  private fun qaList() = listOf(
    QA("night", "🌙", "Night Mode", { nightOn = !nightOn; night.visibility = if (nightOn) View.VISIBLE else View.GONE }, { nightOn }),
    QA("cust", "✎", "Customise Items", { custP() }), QA("shuf", "⤮", "Shuffle", { shuffle = !shuffle }, { shuffle }),
    QA("loop", "🔁", "Loop", { loopMode = (loopMode + 1) % 3; hud(listOf("Loop off", "Loop one", "Loop all")[loopMode]) }, { loopMode > 0 }),
    QA("mute", "🔇", "Mute", { muted = !muted; mp.volume = if (muted) 0 else boost }, { muted }),
    QA("sleep", "⏱", "Sleep Timer", { sleepP() }, { sleepOn || sleepEnd }),
    QA("ab", "A↔B", "A - B Repeat", { if (abA < 0) { abA = mp.time; hud("A set") } else if (abB < 0) { abB = mp.time; hud("B set") } else { abA = -1; abB = -1; hud("A–B cleared") } }, { abA >= 0 }),
    QA("afx", "〰", "Audio Effect", { afxP() }, { boost > 100 }), QA("eq", "🎚", "Equalizer", { eqP() }, { eqOn }),
    QA("spd", "1X", "Speed", { speedP() }, { speed != 1f }), QA("shot", "📷", "Screenshot", { shot() }),
    QA("bg", "🎧", "Background Play", { bg = !bg; hud("Background play " + if (bg) "on" else "off") }, { bg }),
    QA("rot", "⟲", "Screen Rotation", { rotI = (rotI + 1) % orients.size; requestedOrientation = orients[rotI]; hud(orientNames[rotI]) }),
    QA("mir", "⇋", "Mirror Mode", { mirror = !mirror; vlc.scaleX = zoom * (if (mirror) -1 else 1) }, { mirror }))
  private fun renderQA() { qa.removeAllViews(); val on = prefs.getStringSet("qa", null) ?: qaList().map { it.id }.filter { it != "mir" }.toSet()
    qaList().filter { it.id in on }.forEach { q -> val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(14, 6, 14, 6); minimumWidth = 180 }
      val col = if (q.on()) 0xFF5AAEFF.toInt() else -1
      val ic = ICONS[q.id]; if (ic != null) c.addView(ImageView(this).apply { setImageResource(ic); setColorFilter(col); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)) }) else c.addView(TextView(this).apply { text = if (q.id == "spd") "${speed}X" else q.icon; textSize = 22f; setTextColor(col); gravity = Gravity.CENTER })
      c.addView(TextView(this).apply { text = q.label; textSize = 10f; setTextColor(col); gravity = Gravity.CENTER })
      c.setOnClickListener { q.act(); renderQA(); wake() }; qa.addView(c) } }

  // ---------- side panel helpers
  private fun openPanel(title: String, fill: LinearLayout.() -> Unit) { panel.removeAllViews(); panel.item(title, 22f); panel.fill(); panelHost.visibility = View.VISIBLE; h.removeCallbacks(hideUi) }
  private fun LinearLayout.item(t: String, sp: Float = 16f, cb: (() -> Unit)? = null): TextView { val x = TextView(this@PlayerActivity).apply { text = t; textSize = sp; setTextColor(-1); setPadding(8, 24, 8, 24); cb?.let { c -> setOnClickListener { c() } } }; addView(x); return x }
  private fun LinearLayout.check(t: String, on: Boolean, cb: (Boolean) -> Unit) { addView(CheckBox(this@PlayerActivity).apply { text = t; isChecked = on; textSize = 16f; setTextColor(-1); setOnCheckedChangeListener { _, b -> cb(b) } }) }
  private fun LinearLayout.radios(opts: List<String>, sel: Int, cb: (Int) -> Unit) { val g = RadioGroup(this@PlayerActivity); opts.forEachIndexed { i, t -> g.addView(RadioButton(this@PlayerActivity).apply { id = View.generateViewId(); text = t; textSize = 16f; setTextColor(-1); isChecked = i == sel; setOnClickListener { cb(i) } }) }; addView(g) }
  private fun LinearLayout.stepper(label: String, text: () -> String, d: (Int) -> Unit) { val r = LinearLayout(this@PlayerActivity).apply { gravity = Gravity.CENTER_VERTICAL }
    val v = TextView(this@PlayerActivity).apply { this.text = text(); setTextColor(-1); textSize = 16f; setPadding(20, 0, 20, 0) }
    r.addView(TextView(this@PlayerActivity).apply { this.text = label; setTextColor(-1); textSize = 16f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
    r.addView(Button(this@PlayerActivity).apply { this.text = "−"; setOnClickListener { d(-1); v.text = text() } }); r.addView(v); r.addView(Button(this@PlayerActivity).apply { this.text = "+"; setOnClickListener { d(1); v.text = text() } }); addView(r) }
  private fun LinearLayout.slider(label: String, min: Int, max: Int, cur: Int, cb: (Int) -> Unit) { addView(TextView(this@PlayerActivity).apply { text = label; setTextColor(0xFF9A9A9A.toInt()); textSize = 13f })
    addView(SeekBar(this@PlayerActivity).apply { this.max = max - min; progress = cur - min; setOnSeekBarChangeListener(onSeek { p, u -> if (u) cb(p + min) }) }) }
  private fun pick(code: Int) = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), code)
  override fun onActivityResult(r: Int, c: Int, d: Intent?) { val u = d?.data ?: return; try { contentResolver.takePersistableUriPermission(u, 1) } catch (_: Exception) {}
    if (r == 7) { mp.addSlave(IMedia.Slave.Type.Subtitle, u, true); hud("Subtitle loaded") } else if (r == 8) { mp.addSlave(IMedia.Slave.Type.Audio, u, true); hud("Audio loaded") } }

  // ---------- panels
  private fun audioP() { val tr = mp.audioTracks?.toList() ?: emptyList()
    openPanel("Audio Track") { radios(tr.map { it.name }, tr.indexOfFirst { it.id == mp.audioTrack }) { mp.audioTrack = tr[it].id }
      item("Open…") { pick(8) }; stepper("Audio delay", { "${mp.audioDelay / 1000} ms" }) { mp.audioDelay += it * 50000L } } }
  private fun subP() { val tr = mp.spuTracks?.toList() ?: emptyList()
    openPanel("Subtitle") { item("📂  Open…") { pick(7) }; item("⚙  Subtitle settings") { subSetP() }; radios(tr.map { it.name }, tr.indexOfFirst { it.id == mp.spuTrack }) { mp.spuTrack = tr[it].id }
      stepper("Subtitle delay", { "${mp.spuDelay / 1000} ms" }) { mp.spuDelay += it * 500000L } } }
  private fun applyEq() { try { mp.setEqualizer(if (eqOn) eq else null) } catch (_: Throwable) {} }
  private fun eqP() { if (eq == null) eq = MediaPlayer.Equalizer.create()
    openPanel("Equalizer") { check("Equalizer on", eqOn) { eqOn = it; applyEq(); renderQA() }
      val pn = (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
      radios(pn, eqPreset) { eqPreset = it; eq = MediaPlayer.Equalizer.createFromPreset(it); eqOn = true; applyEq(); renderQA(); eqP() }
      slider("Preamp", -20, 20, eq!!.preAmp.toInt()) { eq!!.preAmp = it.toFloat(); applyEq() }
      for (b in 0 until MediaPlayer.Equalizer.getBandCount()) slider("${MediaPlayer.Equalizer.getBandFrequency(b).toInt()} Hz", -20, 20, eq!!.getAmp(b).toInt()) { eq!!.setAmp(b, it.toFloat()); applyEq() } } }
  private fun afxP() { openPanel("Audio Effect") { slider("Volume boost ($boost%)", 100, 200, boost) { boost = it; if (!muted) mp.volume = it }; item("Stronger than 100% may distort sound.", 12f) } }
  private fun speedP() { openPanel("Speed") { slider("Playback speed ($speed x)", 25, 400, (speed * 100).toInt()) { speed = it / 100f; mp.rate = speed; renderQA() }
    listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s -> item("${s}x") { speed = s; mp.rate = s; renderQA(); speedP() } } } }
  private fun sleepP() { AlertDialog.Builder(this).setTitle("Sleep Timer").setItems(arrayOf("Off", "15 minutes", "30 minutes", "60 minutes", "90 minutes", "End of video")) { _, w ->
    h.removeCallbacks(sleepRun); sleepOn = false; sleepEnd = w == 5; if (w in 1..4) { sleepOn = true; h.postDelayed(sleepRun, intArrayOf(0, 15, 30, 60, 90)[w] * 60000L) }; renderQA() }.show() }
  private val sleepRun = Runnable { mp.pause(); sleepOn = false; renderQA() }
  private fun custP() { val all = qaList(); val on = (prefs.getStringSet("qa", null) ?: all.map { it.id }.filter { it != "mir" }.toSet()).toMutableSet()
    openPanel("Shortcuts") { val g = GridLayout(this@PlayerActivity).apply { columnCount = 2 }
      all.forEach { q -> g.addView(CheckBox(this@PlayerActivity).apply { text = q.label; isChecked = q.id in on; textSize = 14f; setTextColor(-1)
        layoutParams = GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
        setOnCheckedChangeListener { _, c -> if (c) on += q.id else on -= q.id; prefs.edit().putStringSet("qa", HashSet(on)).apply(); renderQA() } }) }
      addView(g) } }
  private fun moreP() { openPanel("More") {
    item("☰▶  Playing Queue") { queueP() }; item("▣  Aspect Ratio: ${scaleNames[aspI]}") { aspI = (aspI + 1) % scales.size; mp.videoScale = scales[aspI]; moreP() }
    item("🔖  Bookmark") { bmP() }; item("☰  Information") { infoP() }; item("➦  Share") { share() }
    item("⟲  Screen rotation: ${orientNames[rotI]}") { rotI = (rotI + 1) % orients.size; requestedOrientation = orients[rotI]; moreP() }
    item("✎  Customise shortcuts") { custP() } } }
  private fun queueP() { openPanel("Playing Queue") { uris.indices.forEach { i -> item("${i + 1}. ${names.getOrElse(i) { "" }}") { load(i); panelHost.visibility = View.GONE }.also { if (i == idx) it.setTextColor(0xFF5AAEFF.toInt()) } } } }
  private fun bmP() { val k = "bm:" + uris[idx]; val l = (prefs.getString(k, "") ?: "").split(",").filter { it.isNotBlank() }.map { it.toLong() }.toMutableList()
    openPanel("Bookmark") { item("＋  Add at ${fmt(mp.time)}") { l += mp.time; prefs.edit().putString(k, l.joinToString(",")).apply(); bmP() }; l.forEach { t -> item("🔖  ${fmt(t)}") { mp.time = t; panelHost.visibility = View.GONE } } } }
  private fun infoP() { openPanel("Information") { item(names.getOrElse(idx) { "" }, 14f); val t = try { mp.currentVideoTrack } catch (_: Throwable) { null }
    if (t != null) item("${t.width} × ${t.height}"); item("Duration ${fmt(mp.length)}"); item("Decoder: " + listOf("HW", "HW+", "SW")[hw]); item("Speed ${speed}x") } }
  private fun share() { try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/*").putExtra(Intent.EXTRA_STREAM, Uri.parse(uris[idx])).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share")) } catch (_: Exception) { hud("Cannot share") } }
  private fun shot() { val u = Uri.parse(uris[idx]); val t = mp.time * 1000
    Thread { try { val r = MediaMetadataRetriever(); if (u.scheme == "http" || u.scheme == "https") r.setDataSource(u.toString(), HashMap()) else r.setDataSource(this, u)
      val bmp = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST); r.release()
      @Suppress("DEPRECATION") MediaStore.Images.Media.insertImage(contentResolver, bmp, "ESDY_${System.currentTimeMillis()}", null); runOnUiThread { hud("Screenshot saved") } } catch (_: Throwable) { runOnUiThread { hud("Screenshot failed") } } }.start() }
  private fun pip() { if (Build.VERSION.SDK_INT >= 26) try { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) } catch (_: Exception) {} else hud("PiP needs Android 8+") }
  override fun onUserLeaveHint() { if (mp.isPlaying) pip() }
  override fun onBackPressed() { if (panelHost.visibility == View.VISIBLE) panelHost.visibility = View.GONE else super.onBackPressed() }
  override fun onStop() { super.onStop(); prefs.edit().putLong("pos:" + uris[idx], mp.time).apply(); val inPip = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode; if (!bg && !inPip) mp.pause() }
  override fun onDestroy() { super.onDestroy(); h.removeCallbacksAndMessages(null); try { pfd?.close() } catch (_: Exception) {}; mp.stop(); mp.detachViews(); mp.release(); lib.release() }
}
