package com.esdy.hqmediaplayer

object M3u {
  class Ch(val name: String, val url: String, val group: String, val logo: String)
  private fun attr(l: String, k: String): String = Regex(k + "=\"([^\"]*)\"").find(l)?.groupValues?.get(1) ?: ""
  fun parse(text: String): List<Ch> {
    val out = ArrayList<Ch>(); var name = ""; var group = ""; var logo = ""
    for (raw in text.lineSequence()) { val l = raw.trim()
      if (l.startsWith("#EXTINF")) { name = l.substringAfterLast(",", "").trim(); group = attr(l, "group-title"); logo = attr(l, "tvg-logo"); if (name.isEmpty()) name = attr(l, "tvg-name") }
      else if (l.isNotEmpty() && !l.startsWith("#")) { if (l.startsWith("http") || l.startsWith("rtmp") || l.startsWith("rtsp")) { if (out.size < 20000) out.add(Ch(name.ifEmpty { l }, l, group.ifEmpty { "Other" }, logo)) }; name = ""; group = ""; logo = "" } }
    return out
  }
}
