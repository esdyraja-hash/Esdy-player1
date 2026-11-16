package com.esdy.hqmediaplayer

import android.Manifest
import android.app.DownloadManager
import android.os.Environment
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity() {
  companion object { var unlocked = false }
  data class V(val uri: Uri, val name: String, val ms: Long, val size: Long, val folder: String, val artist: String = "", val added: Long = 0)
  class Row(val uri: Uri?, val icon: String, val t: String, val s: String)
  private val videos = mutableListOf<V>(); private val songs = mutableListOf<V>(); private var shown = listOf<V>()
  private var tab = 0; private var sortI = 0; private var folder: String? = null; private var flat = false; private var q = ""
  private val ex = Executors.newFixedThreadPool(2); private val cache = HashMap<Uri, Bitmap?>(); private val h = Handler(Looper.getMainLooper())
  private val prefs by lazy { getSharedPreferences("esdy", 0) }
  private lateinit var lv: ListView; private lateinit var title: TextView; private lateinit var mini: View; private lateinit var miniTitle: TextView
  private lateinit var miniPlay: TextView; private lateinit var navL: TextView; private lateinit var navM: TextView; private lateinit var fab: View

  override fun onCreate(b: Bundle?) {
    super.onCreate(b); setContentView(R.layout.activity_home)
    lv = findViewById(R.id.list); sortI = prefs.getInt("msort", 0); title = findViewById(R.id.title); mini = findViewById(R.id.mini); miniTitle = findViewById(R.id.miniTitle)
    miniPlay = findViewById(R.id.miniPlay); navL = findViewById(R.id.navL); navM = findViewById(R.id.navM); fab = findViewById(R.id.fab)
    navL.setOnClickListener { tab = 0; folder = null; render() }; navM.setOnClickListener { tab = 1; folder = null; render() }
    findViewById<View>(R.id.bAll).setOnClickListener { flat = !flat; folder = null; tab = 0; render() }
    findViewById<View>(R.id.bSearch).setOnClickListener { val et = EditText(this); et.setText(q)
      AlertDialog.Builder(this).setTitle("Search").setView(et).setPositiveButton("OK") { _, _ -> q = et.text.toString().trim(); render() }.setNegativeButton("Clear") { _, _ -> q = ""; render() }.show() }
    findViewById<ImageView>(R.id.bSet).apply { setColorFilter(0xFF1B1F3B.toInt()); setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java).putStringArrayListExtra("folders", ArrayList(videos.map { it.folder }.distinct().sorted()))) } }
    fab.setOnClickListener { prefs.getString("last", null)?.let { startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(it))) } }
    miniPlay.setOnClickListener { MusicService.instance?.toggle() }; mini.setOnClickListener { startActivity(Intent(this, MusicActivity::class.java)) }; findViewById<View>(R.id.miniNext).setOnClickListener { MusicService.instance?.let { it.next() } }
    findViewById<View>(R.id.miniQ).setOnClickListener { val s = MusicService.instance ?: return@setOnClickListener
      AlertDialog.Builder(this).setTitle("Playing queue").setItems(s.names.toTypedArray()) { _, i -> s.jump(i) }.show() }
    chip("🎵", "Music") { tab = 1; folder = null; render() }; chip("📂", "Browse") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE), 1) }
    chip("🌐", "Network") { val et = EditText(this); et.hint = "https://…/video.mkv"
      AlertDialog.Builder(this).setTitle("Play network stream").setView(et).setPositiveButton("Play") { _, _ -> startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(et.text.toString().trim()))) }.show() }
    chip("⏯", "Resume") { fab.performClick() }; chip("🕘", "Recent") { recentDlg() }; chip("🖧", "LAN") { startActivity(Intent(this, NetworkActivity::class.java)) }; chip("⬇", "Download") { downloadDlg() }
    gate { start() }
  }
  private fun gate(f: () -> Unit) { val pin = prefs.getString("pin", null); if (pin == null || unlocked) { f(); return }
    val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "PIN" }
    AlertDialog.Builder(this).setTitle("Enter PIN").setView(et).setCancelable(false).setPositiveButton("OK") { _, _ ->
      if (et.text.toString() == pin) { unlocked = true; f() } else { Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show(); gate(f) } }.setNegativeButton("Exit") { _, _ -> finish() }.show() }
  private fun start() {
    val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    if (perms.all { checkSelfPermission(it) == 0 }) load() else requestPermissions(perms, 2)
  }
  override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { load() }
  override fun onResume() { super.onResume(); render(); tickMini() }
  override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null) }
  override fun onActivityResult(r: Int, c: Int, d: Intent?) { d?.data?.let { contentResolver.takePersistableUriPermission(it, 1); startActivity(Intent(this, PlayerActivity::class.java).setData(it)) } }
  override fun onBackPressed() { if (folder != null) { folder = null; render() } else if (tab == 1 || flat) { tab = 0; flat = false; render() } else super.onBackPressed() }

  private fun share(u: Uri) { try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(contentResolver.getType(u) ?: "*/*").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share")) } catch (_: Exception) { Toast.makeText(this, "Cannot share this file", Toast.LENGTH_SHORT).show() } }
  private fun downloadDlg() { val et = EditText(this); et.hint = "Direct link to a video file (.mp4, .mkv)"
    AlertDialog.Builder(this).setTitle("Download from link").setMessage("Only download files you have the right to. Links to YouTube and similar sites are not supported.").setView(et)
      .setPositiveButton("Download") { _, _ -> val url = et.text.toString().trim()
        if (!url.startsWith("http")) { Toast.makeText(this, "Enter a link starting with http", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
        val name = Uri.parse(url).lastPathSegment?.takeIf { it.contains('.') } ?: ("video_" + System.currentTimeMillis() + ".mp4")
        try { val r = DownloadManager.Request(Uri.parse(url)).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "ESDY/$name")
          (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r); Toast.makeText(this, "Download started. See the notification.", Toast.LENGTH_LONG).show() } catch (e: Exception) { Toast.makeText(this, "Cannot download this link", Toast.LENGTH_LONG).show() } }
      .setNegativeButton("Cancel", null).show() }
  private fun recentDlg() { val r = Recent.list(this); if (r.isEmpty()) { Toast.makeText(this, "Nothing played yet", Toast.LENGTH_SHORT).show(); return }
    AlertDialog.Builder(this).setTitle("Recently played").setItems(r.map { it.second }.toTypedArray()) { _, i -> startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(r[i].first))) }.show() }
  private fun playlistsDlg() { val n = Playlists.names(this)
    AlertDialog.Builder(this).setTitle("Playlists").setItems((listOf("＋ New playlist") + n).toTypedArray()) { _, i ->
      if (i == 0) { val et = EditText(this); AlertDialog.Builder(this).setTitle("New playlist").setView(et).setPositiveButton("Create") { _, _ -> val s = et.text.toString().trim(); if (s.isNotEmpty()) { Playlists.create(this, s); render() } }.show() }
      else { val name = n[i - 1]; AlertDialog.Builder(this).setTitle(name).setItems(arrayOf("Play", "Start from a random song", "Delete")) { _, w ->
        val (u, nn, ar) = Playlists.items(this, name)
        if (w == 2) { Playlists.delete(this, name); render() }
        else if (u.isEmpty()) Toast.makeText(this, "Playlist is empty. Use the ⋮ menu in the music player to add songs.", Toast.LENGTH_LONG).show()
        else { MusicService.start(this, u, nn, ar, if (w == 1) u.indices.random() else 0); startActivity(Intent(this, MusicActivity::class.java)) } }.show() } }.show() }
  private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
  private fun dur(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
  private fun chip(icon: String, label: String, f: () -> Unit) {
    val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(10), dp(4), dp(10), dp(4)); setOnClickListener { f() } }
    c.addView(TextView(this).apply { text = icon; textSize = 24f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
      background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFE3EAF8.toInt()) } })
    c.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(0xFF1B1F3B.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    findViewById<LinearLayout>(R.id.chips).addView(c)
  }
  private fun tickMini() { val s = MusicService.instance
    mini.visibility = if (s != null && s.hasMedia) View.VISIBLE else View.GONE
    if (s != null && s.hasMedia) { miniTitle.text = s.title; miniPlay.text = if (s.playing) "❚❚" else "▶" }
    h.postDelayed({ tickMini() }, 500) }

  private fun load() {
    videos.clear(); songs.clear()
    try { contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME), null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
      while (c.moveToNext()) videos.add(V(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), c.getString(1) ?: "video", c.getLong(2), c.getLong(3), c.getString(4) ?: "Internal storage")) } } catch (_: Exception) {}
    try { contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATE_ADDED), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE")?.use { c ->
      while (c.moveToNext()) songs.add(V(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), c.getString(1) ?: "song", c.getLong(2), c.getLong(3), "", c.getString(4) ?: "", c.getLong(5))) } } catch (_: Exception) {}
    render()
  }
  private fun open(list: List<V>, i: Int) {
    val s = maxOf(0, i - 100); val sub = list.drop(s).take(300)
    startActivity(Intent(this, PlayerActivity::class.java).setData(list[i].uri).putStringArrayListExtra("uris", ArrayList(sub.map { it.uri.toString() }))
      .putStringArrayListExtra("names", ArrayList(sub.map { it.name })).putExtra("index", i - s))
  }
  private fun render() {
    val hidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet<String>(); val ql = q.lowercase(); val on = 0xFF2F6BEB.toInt(); val off = 0xFF8A90A6.toInt()
    navL.setTextColor(if (tab == 0) on else off); navM.setTextColor(if (tab == 1) on else off)
    title.text = if (tab == 1) "Music" else (folder ?: if (flat) "All videos" else "Folders")
    fab.visibility = if (tab == 0 && prefs.getString("last", null) != null) View.VISIBLE else View.GONE
    val rows = mutableListOf<Row>(); val acts = mutableListOf<() -> Unit>()
    if (tab == 1) { val favs = prefs.getStringSet("favs", emptySet()) ?: emptySet<String>(); val favList = songs.filter { it.uri.toString() in favs }
      rows.add(Row(null, "♥", "Favourites", "${favList.size} songs")); acts.add { if (favList.isEmpty()) Toast.makeText(this, "No favourites yet. Tap the heart in the music player.", Toast.LENGTH_SHORT).show() else { MusicService.start(this, favList.map { it.uri.toString() }, favList.map { it.name }, favList.map { it.artist }, 0); startActivity(Intent(this, MusicActivity::class.java)) } }
      rows.add(Row(null, "📋", "Playlists", "${Playlists.names(this).size} playlists")); acts.add { playlistsDlg() }
      rows.add(Row(null, "⇅", "Sort: ${listOf("A–Z", "Artist", "Recently added")[sortI]}", "Tap to change")); acts.add { sortI = (sortI + 1) % 3; prefs.edit().putInt("msort", sortI).apply(); render() }
      val base = when (sortI) { 1 -> songs.sortedBy { it.artist.lowercase() }; 2 -> songs.sortedByDescending { it.added }; else -> songs }
      val list = base.filter { ql.isEmpty() || it.name.lowercase().contains(ql) }
      list.forEachIndexed { i, s -> rows.add(Row(s.uri, "🎵", s.name, "${s.artist}  ·  ${dur(s.ms)}")); acts.add { MusicService.start(this, list.map { it.uri.toString() }, list.map { it.name }, list.map { it.artist }, i); startActivity(Intent(this, MusicActivity::class.java)) } }
    } else if (folder == null && !flat) {
      videos.filter { it.folder !in hidden && (ql.isEmpty() || it.name.lowercase().contains(ql)) }.groupBy { it.folder }.forEach { (f, l) -> rows.add(Row(null, "📁", f, "${l.size} video${if (l.size > 1) "s" else ""}")); acts.add { folder = f; render() } }
    } else { val list = videos.filter { (folder == null || it.folder == folder) && it.folder !in hidden && (ql.isEmpty() || it.name.lowercase().contains(ql)) }; shown = list
      list.forEachIndexed { i, v -> rows.add(Row(v.uri, "▶", v.name, "${dur(v.ms)}  ·  %.1f MB".format(v.size / 1048576.0))); acts.add { open(list, i) } } }
    lv.adapter = object : BaseAdapter() {
      override fun getCount() = rows.size; override fun getItem(i: Int) = rows[i]; override fun getItemId(i: Int) = i.toLong()
      override fun getView(i: Int, v: View?, p: ViewGroup): View { val r = rows[i]; val row = LinearLayout(this@HomeActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        val th = TextView(this@HomeActivity).apply { text = r.icon; textSize = 30f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(110), dp(62)); setBackgroundColor(0xFFE3E7EF.toInt()) }
        val img = ImageView(this@HomeActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(110), dp(62)); scaleType = ImageView.ScaleType.CENTER_CROP; tag = r.uri; visibility = View.GONE }
        val fl = FrameLayout(this@HomeActivity); fl.addView(th); fl.addView(img)
        r.uri?.let { u -> ex.execute { val bmp = cache.getOrPut(u) { try { if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(u, Size(240, 136), null) else null } catch (_: Throwable) { null } }
          runOnUiThread { if (img.tag == u && bmp != null) { img.setImageBitmap(bmp); img.visibility = View.VISIBLE } } } }
        val box = LinearLayout(this@HomeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        box.addView(TextView(this@HomeActivity).apply { text = r.t; textSize = 15f; setTextColor(0xFF1B1F3B.toInt()); maxLines = 2 })
        box.addView(TextView(this@HomeActivity).apply { text = r.s; textSize = 12f; setTextColor(0xFF7A8094.toInt()) })
        row.addView(fl); row.addView(box); return row }
    }
    lv.setOnItemClickListener { _, _, i, _ -> acts[i]() }
    lv.setOnItemLongClickListener { _, _, i, _ -> rows[i].uri?.let { share(it) }; true }
  }
}
