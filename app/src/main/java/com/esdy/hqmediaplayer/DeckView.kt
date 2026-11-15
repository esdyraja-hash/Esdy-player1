package com.esdy.hqmediaplayer

import android.content.Context
import android.graphics.*
import android.view.View

class DeckView(c: Context) : View(c) {
  var mode = 0; var art: Bitmap? = null; var spinning = false; var artist = ""; var title = ""; var skin = 0; var progress = 0f
  private var angle = 0f; private var angL = 0f; private var angR = 0f; private var arm = -14f; private val p = Paint(Paint.ANTI_ALIAS_FLAG)
  // 8 cassette skins: shell, label, stripe, label text
  private val SH = intArrayOf(0xFF1F2126.toInt(), 0xFF1B2A4A.toInt(), 0xFF3B2F1B.toInt(), 0xFF16301F.toInt(), 0xFF4A1414.toInt(), 0xFF2B1B45.toInt(), 0xFFE6E8EC.toInt(), 0xFF10373A.toInt())
  private val LB = intArrayOf(0xFFD9D4C4.toInt(), 0xFFEAF0FA.toInt(), 0xFFF2E3B0.toInt(), 0xFFE3F0DE.toInt(), 0xFFF6E0DC.toInt(), 0xFFEBE3F7.toInt(), 0xFF2B2F38.toInt(), 0xFFDDF3F2.toInt())
  private val ST = intArrayOf(0xFFE0402B.toInt(), 0xFF2F80ED.toInt(), 0xFFC8922A.toInt(), 0xFF2E9E57.toInt(), 0xFFFF5A47.toInt(), 0xFF8E5CF0.toInt(), 0xFFFFC107.toInt(), 0xFF1FB5AD.toInt())
  private val TX = intArrayOf(0xFF23262B.toInt(), 0xFF14213D.toInt(), 0xFF3A2C0A.toInt(), 0xFF12301C.toInt(), 0xFF4A1414.toInt(), 0xFF2B1B45.toInt(), 0xFFF5F5F5.toInt(), 0xFF0E2E30.toInt())
  private fun rl() = 0.07f + 0.055f * (1 - progress.coerceIn(0f, 1f))   // left roll shrinks as the song plays
  private fun rr() = 0.07f + 0.055f * progress.coerceIn(0f, 1f)         // right roll grows
  private val loop = object : Runnable { override fun run() { if (spinning) { angle = (angle + 1.3f) % 360f
      angL = (angL - 0.4f / rl()) % 360f; angR = (angR - 0.4f / rr()) % 360f }   // smaller roll spins faster, like a real tape
    arm += ((if (spinning) 12f else -14f) - arm) * 0.08f; invalidate(); postOnAnimation(this) } }
  override fun onAttachedToWindow() { super.onAttachedToWindow(); postOnAnimation(loop) }
  override fun onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(loop) }
  override fun onDraw(cv: Canvas) { if (mode == 0) vinyl(cv) else cassette(cv) }
  private fun vinyl(cv: Canvas) {
    val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val cy = h * 0.56f; val r = minOf(w * 0.46f, h * 0.44f)
    p.style = Paint.Style.FILL; p.color = 0xFF6B4A44.toInt(); cv.drawCircle(cx, cy, r, p); p.color = 0xFF0B0B0B.toInt(); cv.drawCircle(cx, cy, r * 0.95f, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0x33FFFFFF; var g = r * 0.58f; while (g < r * 0.93f) { cv.drawCircle(cx, cy, g, p); g += r * 0.035f }
    val ar = r * 0.5f; cv.save(); cv.rotate(angle, cx, cy); val path = Path(); path.addCircle(cx, cy, ar, Path.Direction.CW); cv.clipPath(path)
    val a = art; p.style = Paint.Style.FILL
    if (a != null) cv.drawBitmap(a, null, RectF(cx - ar, cy - ar, cx + ar, cy + ar), p) else { p.color = 0xFFC23B22.toInt(); cv.drawCircle(cx, cy, ar, p); p.color = -1; p.textSize = ar; p.textAlign = Paint.Align.CENTER; cv.drawText("♪", cx, cy + ar * 0.35f, p) }
    cv.restore(); p.color = 0xFF000000.toInt(); cv.drawCircle(cx, cy, r * 0.05f, p)
    val px = cx + r * 0.82f; val py = cy - r * 1.02f; cv.save(); cv.rotate(arm, px, py)
    p.color = 0xFFE6E6E6.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.035f; p.strokeCap = Paint.Cap.ROUND; cv.drawLine(px, py, px, py + r * 1.2f, p)
    p.style = Paint.Style.FILL; cv.drawCircle(px, py, r * 0.09f, p); p.color = 0xFFB0B0B0.toInt(); cv.drawRoundRect(RectF(px - r * 0.05f, py + r * 1.15f, px + r * 0.05f, py + r * 1.3f), 6f, 6f, p); cv.restore()
  }
  private fun fit(s: String, maxW: Float): String { var t = s; while (t.length > 1 && p.measureText(t) > maxW) t = t.dropLast(1); return if (t.length < s.length) t.trimEnd() + "…" else t }
  private fun cassette(cv: Canvas) {
    val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val bw = w * 0.92f; val bh = bw * 0.64f; val l = cx - bw / 2; val t = (h - bh) / 2; val k = ((skin % 8) + 8) % 8
    fun X(f: Float) = l + bw * f
    fun Y(f: Float) = t + bw * f
    p.shader = null; p.style = Paint.Style.FILL; p.color = SH[k]; cv.drawRoundRect(RectF(l, t, l + bw, t + bh), bw * 0.035f, bw * 0.035f, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = bw * 0.006f; p.color = 0x66000000; cv.drawRoundRect(RectF(l, t, l + bw, t + bh), bw * 0.035f, bw * 0.035f, p)
    // label
    p.style = Paint.Style.FILL; p.color = LB[k]; cv.drawRoundRect(RectF(X(.05f), Y(.04f), X(.95f), Y(.43f)), bw * 0.012f, bw * 0.012f, p)
    p.color = ST[k]; cv.drawRect(X(.05f), Y(.04f), X(.95f), Y(.095f), p)
    p.color = -1; p.typeface = Typeface.DEFAULT_BOLD; p.textSize = bw * 0.032f; p.textAlign = Paint.Align.LEFT; cv.drawText("ESDY HQ", X(.07f), Y(.082f), p)
    p.textAlign = Paint.Align.RIGHT; cv.drawText("C-90 · SIDE A", X(.93f), Y(.082f), p)
    p.color = TX[k]; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); p.textSize = bw * 0.05f; cv.drawText(fit(title, bw * 0.84f), cx, Y(.16f), p)
    p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC); p.textSize = bw * 0.038f; cv.drawText(fit(artist, bw * 0.84f), cx, Y(.205f), p)
    // bottom trapezoid with tape path, pins and head opening
    val tz = Path(); tz.moveTo(X(.11f), Y(.45f)); tz.lineTo(X(.89f), Y(.45f)); tz.lineTo(X(.81f), t + bh); tz.lineTo(X(.19f), t + bh); tz.close()
    p.style = Paint.Style.FILL; p.color = 0xFF14151A.toInt(); cv.drawPath(tz, p)
    val xL = cx - bw * 0.215f; val xR = cx + bw * 0.215f; val wy = Y(.315f); val hh = bw * 0.095f
    p.style = Paint.Style.STROKE; p.strokeWidth = bw * 0.008f; p.color = 0xFF5A4030.toInt(); p.strokeCap = Paint.Cap.ROUND
    cv.drawLine(xL, Y(.45f), X(.25f), Y(.585f), p); cv.drawLine(xR, Y(.45f), X(.75f), Y(.585f), p); cv.drawLine(X(.25f), Y(.585f), X(.75f), Y(.585f), p)
    p.style = Paint.Style.FILL; p.color = 0xFFAEB0B8.toInt(); cv.drawCircle(X(.25f), Y(.585f), bw * 0.014f, p); cv.drawCircle(X(.75f), Y(.585f), bw * 0.014f, p)
    p.color = 0xFF050506.toInt(); cv.drawRect(X(.40f), Y(.598f), X(.60f), t + bh, p)
    cv.drawRoundRect(RectF(X(.29f), Y(.60f), X(.35f), t + bh), 5f, 5f, p); cv.drawRoundRect(RectF(X(.65f), Y(.60f), X(.71f), t + bh), 5f, 5f, p)
    // window with tape rolls and hubs
    val win = RectF(X(.12f), wy - hh, X(.88f), wy + hh)
    p.color = 0xFF0D0F12.toInt(); cv.drawRoundRect(win, bw * 0.09f, bw * 0.09f, p)
    cv.save(); val wp = Path(); wp.addRoundRect(win, bw * 0.09f, bw * 0.09f, Path.Direction.CW); cv.clipPath(wp)
    p.color = 0xFF2B1E15.toInt(); cv.drawCircle(xL, wy, rl() * bw, p); cv.drawCircle(xR, wy, rr() * bw, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x22FFFFFF; cv.drawCircle(xL, wy, rl() * bw, p); cv.drawCircle(xR, wy, rr() * bw, p)
    hub(cv, xL, wy, bw * 0.055f, angL); hub(cv, xR, wy, bw * 0.055f, angR); cv.restore()
    // screws
    for (sx in floatArrayOf(0.035f, 0.965f)) for (sy in floatArrayOf(0.035f, 0.605f)) { p.style = Paint.Style.FILL; p.color = 0xFF8A8D94.toInt(); cv.drawCircle(X(sx), Y(sy), bw * 0.014f, p)
      p.style = Paint.Style.STROKE; p.strokeWidth = bw * 0.003f; p.color = 0xFF3A3C40.toInt(); cv.drawLine(X(sx) - bw * 0.009f, Y(sy), X(sx) + bw * 0.009f, Y(sy), p) }
  }
  private fun hub(cv: Canvas, x: Float, y: Float, r: Float, ang: Float) {
    p.style = Paint.Style.FILL; p.color = 0xFFE8E8EC.toInt(); cv.drawCircle(x, y, r, p); p.color = 0xFF0D0F12.toInt(); cv.drawCircle(x, y, r * 0.68f, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.2f; p.color = 0xFFE8E8EC.toInt(); p.strokeCap = Paint.Cap.BUTT
    for (i in 0 until 6) { val a = Math.toRadians((ang + i * 60).toDouble()); val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat(); cv.drawLine(x + r * 0.7f * c, y + r * 0.7f * s, x + r * 0.34f * c, y + r * 0.34f * s, p) }
  }
}
