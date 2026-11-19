package com.esdy.hqmediaplayer

object Subs {
  class Cue(val s: Long, val e: Long, val t: String)
  private fun ts(x: String): Long { val m = x.trim().replace(',', '.').split(':')
    return try { when (m.size) { 3 -> ((m[0].toDouble() * 3600 + m[1].toDouble() * 60 + m[2].toDouble()) * 1000).toLong(); 2 -> ((m[0].toDouble() * 60 + m[1].toDouble()) * 1000).toLong(); else -> 0L } } catch (e: Exception) { 0L } }
  fun parse(raw: String, name: String): List<Cue> {
    val txt = raw.replace("\r", "").removePrefix("\uFEFF"); val out = ArrayList<Cue>()
    if (name.endsWith(".ass") || name.endsWith(".ssa")) {
      for (l in txt.lines()) if (l.startsWith("Dialogue:")) { val p = l.substring(9).split(","); if (p.size >= 10)
        out.add(Cue(ts(p[1]), ts(p[2]), p.drop(9).joinToString(",").replace(Regex("\\{[^}]*\\}"), "").replace("\\N", "\n").replace("\\n", "\n").trim())) }
    } else {
      for (b in txt.split(Regex("\n\n+"))) { val l = b.split("\n"); val i = l.indexOfFirst { it.contains("-->") }; if (i < 0) continue
        val ab = l[i].split("-->"); if (ab.size < 2) continue
        out.add(Cue(ts(ab[0]), ts(ab[1].trim().split(" ")[0]), l.drop(i + 1).joinToString("\n").replace(Regex("<[^>]*>"), "").replace(Regex("\\{[^}]*\\}"), "").trim())) }
    }
    return out.filter { it.t.isNotEmpty() }.sortedBy { it.s }
  }
  fun at(l: List<Cue>, t: Long): String? { for (c in l) { if (c.s > t) break; if (t <= c.e) return c.t }; return null }
}
