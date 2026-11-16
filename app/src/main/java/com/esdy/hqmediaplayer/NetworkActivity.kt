package com.esdy.hqmediaplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.MediaBrowser

class NetworkActivity : AppCompatActivity() {
  class Item(val uri: String, val name: String, val dir: Boolean)
  private val items = mutableListOf<Item>(); private val stack = ArrayList<String>()
  private lateinit var lv: ListView; private lateinit var head: TextView; private lateinit var lib: LibVLC
  private var browser: MediaBrowser? = null; private var inServers = true
  private val prefs by lazy { getSharedPreferences("esdy", 0) }; private val ui = Handler(Looper.getMainLooper())
  private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

  override fun onCreate(b: Bundle?) { super.onCreate(b)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF4F7FC.toInt()); setPadding(0, dp(30), 0, 0) }
    head = TextView(this).apply { textSize = 22f; setTextColor(0xFF1B1F3B.toInt()); setPadding(dp(18), dp(12), dp(18), dp(12)); setOnClickListener { onBackPressed() } }
    lv = ListView(this); root.addView(head); root.addView(lv, LinearLayout.LayoutParams(-1, -1)); setContentView(root)
    lib = LibVLC(this, arrayListOf("--no-drop-late-frames")); showServers()
  }
  private fun servers(): JSONArray = try { JSONArray(prefs.getString("servers", "[]")) } catch (e: Exception) { JSONArray() }
  private fun labeled(l: List<String>) = object : ArrayAdapter<String>(this@NetworkActivity, android.R.layout.simple_list_item_1, l) {
    override fun getView(p: Int, v: View?, g: ViewGroup): View { val t = super.getView(p, v, g) as TextView; t.setTextColor(0xFF1B1F3B.toInt()); t.textSize = 16f; t.setPadding(dp(18), dp(14), dp(18), dp(14)); return t } }
  private fun showServers() { inServers = true; stack.clear(); browser?.release(); browser = null; head.text = "←   Network"
    val a = servers(); val labels = mutableListOf("＋  Add server (SMB / FTP)", "▶  Play a stream URL"); for (i in 0 until a.length()) labels.add("🖧  " + a.getJSONObject(i).getString("n"))
    lv.adapter = labeled(labels)
    lv.setOnItemClickListener { _, _, i, _ -> when (i) { 0 -> addServer(); 1 -> urlDlg(); else -> browse(a.getJSONObject(i - 2).getString("u")) } }
    lv.setOnItemLongClickListener { _, _, i, _ -> if (i >= 2) AlertDialog.Builder(this).setTitle("Remove this server?").setPositiveButton("Remove") { _, _ ->
      val na = JSONArray(); for (k in 0 until a.length()) if (k != i - 2) na.put(a.getJSONObject(k)); prefs.edit().putString("servers", na.toString()).apply(); showServers() }.setNegativeButton("Cancel", null).show(); true }
  }
  private fun browse(uri: String) { inServers = false; if (stack.isEmpty() || stack.last() != uri) stack.add(uri)
    val u = Uri.parse(uri); head.text = "←   " + (u.lastPathSegment ?: u.host ?: "Network"); items.clear(); show(); browser?.release()
    browser = MediaBrowser(lib, object : MediaBrowser.EventListener {
      override fun onMediaAdded(index: Int, media: IMedia) { items.add(Item(media.uri.toString(), media.getMeta(IMedia.Meta.Title) ?: media.uri.lastPathSegment ?: "?", media.type == IMedia.Type.Directory))
        items.sortWith(compareBy({ !it.dir }, { it.name.lowercase() })); show() }
      override fun onMediaRemoved(index: Int, media: IMedia) {}
      override fun onBrowseEnd() { if (items.isEmpty()) Toast.makeText(this@NetworkActivity, "Nothing here, or cannot connect", Toast.LENGTH_LONG).show() } }, ui)
    browser!!.browse(u, 0)
  }
  private fun show() { lv.adapter = labeled(items.map { (if (it.dir) "📁  " else "🎬  ") + it.name })
    lv.setOnItemClickListener { _, _, i, _ -> val x = items[i]; if (x.dir) browse(x.uri) else play(i) }; lv.setOnItemLongClickListener { _, _, _, _ -> true } }
  private fun play(i: Int) { val files = items.withIndex().filter { !it.value.dir }; val idx = files.indexOfFirst { it.index == i }
    startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(items[i].uri)).putStringArrayListExtra("uris", ArrayList(files.map { it.value.uri }))
      .putStringArrayListExtra("names", ArrayList(files.map { it.value.name })).putExtra("index", idx)) }
  private fun field(h: String, pw: Boolean = false) = EditText(this).apply { hint = h; if (pw) inputType = 129 }
  private fun addServer() { val n = field("Name (for example My NAS)"); val u = field("Address: smb://192.168.1.5/Movies or ftp://host/path"); val us = field("Username (optional)"); val pw = field("Password (optional)", true)
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0); listOf(n, u, us, pw).forEach { addView(it) } }
    AlertDialog.Builder(this).setTitle("Add server").setView(box).setPositiveButton("Save") { _, _ ->
      var url = u.text.toString().trim()
      if (url.isNotEmpty() && "://" in url) {
        if (us.text.isNotBlank()) url = url.replaceFirst("://", "://" + Uri.encode(us.text.toString()) + (if (pw.text.isNotEmpty()) ":" + Uri.encode(pw.text.toString()) else "") + "@")
        val a = servers(); a.put(JSONObject().put("n", n.text.toString().ifBlank { u.text.toString().trim() }).put("u", url)); prefs.edit().putString("servers", a.toString()).apply(); showServers()
      } else Toast.makeText(this, "The address must start with smb:// or ftp://", Toast.LENGTH_LONG).show() }.setNegativeButton("Cancel", null).show() }
  private fun urlDlg() { val et = EditText(this); et.hint = "https://…/video.mkv"
    AlertDialog.Builder(this).setTitle("Play a stream URL").setView(et).setPositiveButton("Play") { _, _ -> startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(et.text.toString().trim()))) }.setNegativeButton("Cancel", null).show() }
  override fun onBackPressed() { if (inServers) { super.onBackPressed(); return }; stack.removeLastOrNull(); val prev = stack.lastOrNull(); if (prev == null) showServers() else browse(prev) }
  override fun onDestroy() { super.onDestroy(); browser?.release(); lib.release() }
}
