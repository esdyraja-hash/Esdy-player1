package com.esdy.hqmediaplayer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class IptvActivity : AppCompatActivity() {
  private val prefs by lazy { getSharedPreferences("esdy", 0) }
  private var all = listOf<M3u.Ch>(); private var shown = listOf<M3u.Ch>(); private var group = ""; private var q = ""
  private lateinit var lv: ListView; private lateinit var plBtn: TextView; private lateinit var groupBtn: TextView; private lateinit var status: TextView
  private val ex = Executors.newFixedThreadPool(3); private val logos = HashMap<String, Bitmap?>()
  private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

  override fun onCreate(b: Bundle?) { super.onCreate(b); val me = this@IptvActivity
    val root = LinearLayout(me).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg(me)); setPadding(0, dp(30), 0, 0) }
    root.addView(TextView(me).apply { text = "←   TV channels"; textSize = 22f; setTextColor(T.text(me)); setPadding(dp(18), dp(12), dp(18), dp(6)); setOnClickListener { finish() } })
    plBtn = TextView(me).apply { text = "📋  Choose a playlist ▾"; textSize = 15f; setTextColor(T.accent(me)); setPadding(dp(18), dp(8), dp(18), dp(8)); setOnClickListener { playlistDlg() } }; root.addView(plBtn)
    val search = EditText(me).apply { hint = "Search channels"; setTextColor(T.text(me)); setHintTextColor(T.sub(me)); setSingleLine(); setPadding(dp(18), dp(10), dp(18), dp(10))
      addTextChangedListener(object : TextWatcher { override fun afterTextChanged(s: Editable?) { q = s.toString().trim().lowercase(); applyFilter() }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}; override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {} }) }; root.addView(search)
    groupBtn = TextView(me).apply { text = "Group: All ▾"; textSize = 14f; setTextColor(T.sub(me)); setPadding(dp(18), dp(6), dp(18), dp(6)); setOnClickListener { groupDlg() } }; root.addView(groupBtn)
    status = TextView(me).apply { textSize = 12f; setTextColor(T.sub(me)); setPadding(dp(18), 0, dp(18), dp(6)) }; root.addView(status)
    lv = ListView(me).apply { divider = null }; root.addView(lv, LinearLayout.LayoutParams(-1, -1)); setContentView(root)
    val url = prefs.getString("tvurl", null); if (url != null) load(prefs.getString("tvname", "Playlist") ?: "Playlist", url) else playlistDlg()
  }
  private fun lists(): JSONArray = try { JSONArray(prefs.getString("tvlists", "[]")) } catch (e: Exception) { JSONArray() }
  private fun fix(u: String): String { var s = u.trim(); if (s.contains("github.com/") && s.contains("/blob/")) s = s.replace("github.com/", "raw.githubusercontent.com/").replace("/blob/", "/"); return s }
  private fun saveList(n: String, u: String) { val a = lists(); for (i in 0 until a.length()) if (a.getJSONObject(i).getString("u") == u) return; a.put(JSONObject().put("n", n).put("u", u)); prefs.edit().putString("tvlists", a.toString()).apply() }
  private fun playlistDlg() { val a = lists(); val names = mutableListOf("＋  Add playlist link", "★  Examples (GitHub and test stream)", "🗑  Remove a playlist"); for (i in 0 until a.length()) names.add(a.getJSONObject(i).getString("n"))
    AlertDialog.Builder(this).setTitle("Playlists").setItems(names.toTypedArray()) { _, i -> when (i) { 0 -> addDlg(); 1 -> examplesDlg(); 2 -> removeDlg(); else -> { val o = a.getJSONObject(i - 3); load(o.getString("n"), o.getString("u")) } } }.show() }
  private fun removeDlg() { val a = lists(); if (a.length() == 0) { Toast.makeText(this, "No saved playlists", Toast.LENGTH_SHORT).show(); return }
    AlertDialog.Builder(this).setTitle("Remove which?").setItems(Array(a.length()) { a.getJSONObject(it).getString("n") }) { _, i -> val na = JSONArray(); for (k in 0 until a.length()) if (k != i) na.put(a.getJSONObject(k)); prefs.edit().putString("tvlists", na.toString()).apply() }.show() }
  private fun addDlg() { val n = EditText(this).apply { hint = "Name (for example My channels)" }; val u = EditText(this).apply { hint = "Link to an .m3u / .m3u8 playlist (GitHub links work)" }
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0); addView(n); addView(u) }
    AlertDialog.Builder(this).setTitle("Add playlist").setView(box).setPositiveButton("Save") { _, _ -> val url = fix(u.text.toString())
      if (url.startsWith("http")) { val name = n.text.toString().ifBlank { url.substringAfterLast('/') }; saveList(name, url); load(name, url) } else Toast.makeText(this, "Enter a link starting with http", Toast.LENGTH_LONG).show() }.setNegativeButton("Cancel", null).show() }
  private fun examplesDlg() {
    val ex = listOf("▶  Test video stream (Big Buck Bunny, HLS)" to "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "iptv-org · India" to "https://iptv-org.github.io/iptv/countries/in.m3u", "iptv-org · Tamil" to "https://iptv-org.github.io/iptv/languages/tam.m3u",
      "iptv-org · Hindi" to "https://iptv-org.github.io/iptv/languages/hin.m3u", "iptv-org · News" to "https://iptv-org.github.io/iptv/categories/news.m3u", "iptv-org · All channels (large)" to "https://iptv-org.github.io/iptv/index.m3u")
    AlertDialog.Builder(this).setTitle("Examples").setMessage("Public community playlists. Channels come and go, and some may not work in your region.").setItems(ex.map { it.first }.toTypedArray()) { _, i ->
      if (i == 0) startActivity(Intent(this, PlayerActivity::class.java).setData(Uri.parse(ex[0].second))) else { saveList(ex[i].first, ex[i].second); load(ex[i].first, ex[i].second) } }.show() }
  private fun groupDlg() { val g = all.map { it.group }.distinct().sorted(); val names = listOf("All channels", "★  Favourites") + g
    AlertDialog.Builder(this).setTitle("Group").setItems(names.toTypedArray()) { _, i -> group = when (i) { 0 -> ""; 1 -> "★"; else -> g[i - 2] }; applyFilter() }.show() }
  private fun load(name: String, url: String) { plBtn.text = "📋  $name ▾"; status.text = "Loading…"; prefs.edit().putString("tvname", name).putString("tvurl", url).apply()
    Thread { val cache = java.io.File(cacheDir, "tv_" + url.hashCode() + ".m3u")
      val text: String? = try { val c = URL(url).openConnection() as HttpURLConnection; c.connectTimeout = 15000; c.readTimeout = 30000; c.setRequestProperty("User-Agent", "ESDY-Player"); c.instanceFollowRedirects = true
        val t = c.inputStream.bufferedReader().readText(); try { cache.writeText(t) } catch (_: Exception) {}; t } catch (e: Exception) { if (cache.exists()) cache.readText() else null }
      val list = if (text == null) null else M3u.parse(text)
      runOnUiThread { if (list == null || list.isEmpty()) { all = emptyList(); status.text = "Could not load this playlist. Check the link and your internet." } else { all = list; group = ""; status.text = "${list.size} channels · tap to play · long-press for favourite" }; applyFilter() } }.start() }
  private fun applyFilter() { val favs = prefs.getStringSet("tvfavs", emptySet()) ?: emptySet<String>()
    shown = all.filter { (group.isEmpty() || (group == "★" && it.url in favs) || it.group == group) && (q.isEmpty() || it.name.lowercase().contains(q)) }.take(3000)
    groupBtn.text = "Group: ${if (group.isEmpty()) "All" else group} ▾"
    val me = this@IptvActivity
    lv.adapter = object : BaseAdapter() {
      override fun getCount() = shown.size; override fun getItem(i: Int) = shown[i]; override fun getItemId(i: Int) = i.toLong()
      override fun getView(i: Int, v: View?, p: ViewGroup): View { val c = shown[i]; val row = LinearLayout(me).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val img = ImageView(me).apply { layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)); scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(T.chip(me)); tag = c.logo }
        if (c.logo.startsWith("http")) ex.execute { val bmp = logos.getOrPut(c.logo) { try { val h = URL(c.logo).openConnection() as HttpURLConnection; h.connectTimeout = 5000; h.readTimeout = 5000
          BitmapFactory.decodeStream(h.inputStream, null, BitmapFactory.Options().apply { inSampleSize = 2 }) } catch (_: Throwable) { null } }
          runOnUiThread { if (img.tag == c.logo && bmp != null) img.setImageBitmap(bmp) } }
        val box = LinearLayout(me).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        box.addView(TextView(me).apply { text = (if (c.url in favs) "★ " else "") + c.name; textSize = 15f; setTextColor(T.text(me)); maxLines = 2 }); box.addView(TextView(me).apply { text = c.group; textSize = 12f; setTextColor(T.sub(me)) })
        row.addView(img); row.addView(box); return row } }
    lv.setOnItemClickListener { _, _, i, _ -> val s = maxOf(0, i - 200); val sub = shown.drop(s).take(500)
      startActivity(Intent(me, PlayerActivity::class.java).setData(Uri.parse(shown[i].url)).putStringArrayListExtra("uris", ArrayList(sub.map { it.url })).putStringArrayListExtra("names", ArrayList(sub.map { it.name })).putExtra("index", i - s)) }
    lv.setOnItemLongClickListener { _, _, i, _ -> val f = (prefs.getStringSet("tvfavs", emptySet()) ?: emptySet<String>()).toMutableSet(); val u = shown[i].url
      val added = f.add(u); if (!added) f.remove(u); prefs.edit().putStringSet("tvfavs", HashSet(f)).apply(); Toast.makeText(me, if (added) "Added to favourites" else "Removed from favourites", Toast.LENGTH_SHORT).show(); applyFilter(); true }
  }
}
