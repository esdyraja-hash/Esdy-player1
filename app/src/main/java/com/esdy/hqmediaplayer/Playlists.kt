package com.esdy.hqmediaplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Lrc {
  data class L(val ms: Long, val text: String)
  fun parse(raw: String): List<L> {
    val out = ArrayList<L>(); val re = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    for (line in raw.lines()) { val ms = re.findAll(line).toList(); if (ms.isEmpty()) continue; val text = re.replace(line, "").trim()
      for (m in ms) { val fr = m.groupValues[3]; val frac = if (fr.isEmpty()) 0L else fr.padEnd(3, '0').take(3).toLong()
        out.add(L(m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 + frac, text)) } }
    return out.sortedBy { it.ms }
  }
  fun current(l: List<L>, t: Long): Int { var i = -1; for (k in l.indices) { if (l[k].ms <= t) i = k else break }; return i }
}

object Playlists {
  private fun all(c: Context): JSONObject = try { JSONObject(c.getSharedPreferences("esdy", 0).getString("playlists", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
  private fun save(c: Context, o: JSONObject) { c.getSharedPreferences("esdy", 0).edit().putString("playlists", o.toString()).apply() }
  fun names(c: Context): List<String> = all(c).keys().asSequence().toList().sorted()
  fun create(c: Context, n: String) { val o = all(c); if (!o.has(n)) { o.put(n, JSONArray()); save(c, o) } }
  fun delete(c: Context, n: String) { val o = all(c); o.remove(n); save(c, o) }
  fun add(c: Context, n: String, uri: String, name: String, artist: String) { val o = all(c); val a = o.optJSONArray(n) ?: JSONArray()
    for (i in 0 until a.length()) if (a.getJSONObject(i).getString("u") == uri) return
    a.put(JSONObject().put("u", uri).put("n", name).put("a", artist)); o.put(n, a); save(c, o) }
  fun items(c: Context, n: String): Triple<List<String>, List<String>, List<String>> {
    val a = all(c).optJSONArray(n) ?: JSONArray(); val u = ArrayList<String>(); val nn = ArrayList<String>(); val ar = ArrayList<String>()
    for (i in 0 until a.length()) { val x = a.getJSONObject(i); u.add(x.getString("u")); nn.add(x.getString("n")); ar.add(x.getString("a")) }
    return Triple(u, nn, ar) }
}

object Recent {
  fun add(c: Context, u: String, n: String) { val p = c.getSharedPreferences("esdy", 0)
    val a = try { JSONArray(p.getString("recent", "[]")) } catch (e: Exception) { JSONArray() }; val out = JSONArray(); out.put(JSONObject().put("u", u).put("n", n))
    for (i in 0 until a.length()) { val x = a.getJSONObject(i); if (x.getString("u") != u && out.length() < 20) out.put(x) }
    p.edit().putString("recent", out.toString()).apply() }
  fun list(c: Context): List<Pair<String, String>> { val a = try { JSONArray(c.getSharedPreferences("esdy", 0).getString("recent", "[]")) } catch (e: Exception) { JSONArray() }
    return (0 until a.length()).map { val x = a.getJSONObject(it); Pair(x.getString("u"), x.getString("n")) } }
}
