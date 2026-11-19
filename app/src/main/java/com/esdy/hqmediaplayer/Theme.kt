package com.esdy.hqmediaplayer

import android.content.Context

object T {
  val ACCENTS = intArrayOf(0xFF2F6BEB.toInt(), 0xFF1FB5AD.toInt(), 0xFF2E9E57.toInt(), 0xFFF28C28.toInt(), 0xFFE0402B.toInt(), 0xFFE0449B.toInt(), 0xFF8E5CF0.toInt(), 0xFFE8B923.toInt())
  val ACCENT_N = listOf("Blue", "Teal", "Green", "Orange", "Red", "Pink", "Purple", "Yellow")
  val THEME_N = listOf("Light", "Dark", "AMOLED black")
  private fun p(c: Context) = c.getSharedPreferences("esdy", 0)
  fun mode(c: Context) = p(c).getInt("theme", 0).coerceIn(0, 2)
  fun accent(c: Context) = ACCENTS[p(c).getInt("accent", 0).coerceIn(0, ACCENTS.size - 1)]
  private fun pick(c: Context, l: Long, d: Long, a: Long): Int = when (mode(c)) { 0 -> l; 1 -> d; else -> a }.toInt()
  fun bg(c: Context) = pick(c, 0xFFF4F7FC, 0xFF14161B, 0xFF000000)
  fun nav(c: Context) = pick(c, 0xFFFFFFFF, 0xFF1A1D23, 0xFF000000)
  fun card(c: Context) = pick(c, 0xFFFFFFFF, 0xFF1E2128, 0xFF121212)
  fun chip(c: Context) = pick(c, 0xFFE3EAF8, 0xFF262B35, 0xFF1C1C1C)
  fun text(c: Context) = pick(c, 0xFF1B1F3B, 0xFFF1F3F8, 0xFFF1F3F8)
  fun sub(c: Context) = pick(c, 0xFF7A8094, 0xFF9AA0B2, 0xFF8E93A3)
  fun dark(c: Int, f: Float): Int { val r = (((c shr 16) and 0xFF) * f).toInt(); val g = (((c shr 8) and 0xFF) * f).toInt(); val b = ((c and 0xFF) * f).toInt(); return (0xFF shl 24) or (r shl 16) or (g shl 8) or b }
}
