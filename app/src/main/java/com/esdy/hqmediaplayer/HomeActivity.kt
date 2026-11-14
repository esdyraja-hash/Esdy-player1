package com.esdy.hqmediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity() {
  data class V(val uri: Uri, val name: String, val ms: Long, val size: Long, val folder: String)
  private val all = mutableListOf<V>(); private var shown = listOf<V>(); private var tab = 0; private var folder: String? = null
  private val ex = Executors.newFixedThreadPool(2); private val cache = HashMap<Uri, Bitmap?>()
  private lateinit var lv: ListView; private lateinit var t1: TextView; private lateinit var t2: TextView

  override fun onCreate(b: Bundle?) {
    super.onCreate(b); setContentView(R.layout.activity_home)
    lv = findViewById(R.id.list); t1 = findViewById(R.id.t1); t2 = findViewById(R.id.t2)
    findViewById<Button>(R.id.btnBrowse).setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE), 1) }
    findViewById<Button>(R.id.btnUrl).setOnClickListener {
      val et = EditText(this); et.hint = "https://…/video.mkv"
      AlertDialog.Builder(this).setTitle("Play network stream").setView(et).setPositiveButton("Play") { _, _ -> startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(et.text.toString().trim()))) }.show() }
    t1.setOnClickListener { tab = 0; folder = null; render() }; t2.setOnClickListener { tab = 1; folder = null; render() }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    if (checkSelfPermission(perm) == 0) load() else requestPermissions(arrayOf(perm), 2)
  }
  override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { if (r.firstOrNull() == 0) load() }
  override fun onActivityResult(r: Int, c: Int, d: Intent?) { d?.data?.let { contentResolver.takePersistableUriPermission(it, 1); startActivity(Intent(this, PlayerActivity::class.java).setData(it)) } }
  override fun onBackPressed() { if (folder != null) { folder = null; render() } else super.onBackPressed() }

  private fun load() {
    all.clear();
    contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME), null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use {
      while (it.moveToNext()) all += V(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.getLong(0)), it.getString(1) ?: "video", it.getLong(2), it.getLong(3), it.getString(4) ?: "Internal storage") }
    findViewById<TextView>(R.id.count).text = "${all.size} videos on this device"; render()
  }
  private fun open(list: List<V>, i: Int) {
    val s = maxOf(0, i - 100); val sub = list.drop(s).take(300)
    startActivity(Intent(this, PlayerActivity::class.java).setData(list[i].uri).putStringArrayListExtra("uris", ArrayList(sub.map { it.uri.toString() }))
      .putStringArrayListExtra("names", ArrayList(sub.map { it.name })).putExtra("index", i - s))
  }
  private fun render() {
    t1.setTextColor(if (tab == 0) 0xFF5AAEFF.toInt() else 0xFF9A9A9A.toInt()); t2.setTextColor(if (tab == 1) 0xFF5AAEFF.toInt() else 0xFF9A9A9A.toInt())
    if (tab == 1 && folder == null) {
      val g = all.groupBy { it.folder }.toList()
      lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, g.map { "📁  ${it.first}  (${it.second.size})" })
      lv.setOnItemClickListener { _, _, i, _ -> folder = g[i].first; shown = g[i].second; tab = 0; render() }; return
    }
    shown = if (folder != null) all.filter { it.folder == folder } else all
    lv.adapter = object : BaseAdapter() {
      override fun getCount() = shown.size; override fun getItem(i: Int) = shown[i]; override fun getItemId(i: Int) = i.toLong()
      override fun getView(i: Int, v: android.view.View?, p: ViewGroup): android.view.View {
        val x = shown[i]; val row = LinearLayout(this@HomeActivity).apply { setPadding(24, 18, 24, 18) }
        val th = ImageView(this@HomeActivity).apply { layoutParams = LinearLayout.LayoutParams(240, 135); scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF2A2A2A.toInt()); tag = x.uri }
        ex.execute { val bmp = cache.getOrPut(x.uri) { try { if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(x.uri, Size(240, 135), null) else null } catch (_: Throwable) { null } }
          runOnUiThread { if (th.tag == x.uri && bmp != null) th.setImageBitmap(bmp) } }
        val t = TextView(this@HomeActivity).apply { setPadding(24, 0, 0, 0); setTextColor(-1); textSize = 14f; val s = x.ms / 1000
          text = "${x.name}\n${s / 60}:%02d  ·  %.1f MB".format(s % 60, x.size / 1048576.0) }
        row.addView(th); row.addView(t); return row
      }
    }
    lv.setOnItemClickListener { _, _, i, _ -> open(shown, i) }
  }
}
