package com.esdy.hqmediaplayer

import android.content.Context
import android.graphics.*
import android.view.View

class DeckView(c: Context) : View(c) {
  var mode = 0; var art: Bitmap? = null; var spinning = false; var artist = ""
  private var angle = 0f; private var arm = -14f; private val p = Paint(Paint.ANTI_ALIAS_FLAG)
  private val loop = object : Runnable { override fun run() { if (spinning) angle = (angle + 1.3f) % 360f
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
  private fun cassette(cv: Canvas) {
    val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val bw = w * 0.9f; val bh = bw * 0.64f; val l = (w - bw) / 2; val t = (h - bh) / 2; p.style = Paint.Style.FILL
    p.color = 0xFF202228.toInt(); cv.drawRoundRect(RectF(l, t, l + bw, t + bh), bw * 0.04f, bw * 0.04f, p)
    p.color = 0xFF3B4150.toInt(); cv.drawRoundRect(RectF(l + bw * 0.06f, t + bh * 0.1f, l + bw * 0.94f, t + bh * 0.64f), 14f, 14f, p)
    p.color = 0xFFC9CED8.toInt(); cv.drawRect(l + bw * 0.06f, t + bh * 0.1f, l + bw * 0.94f, t + bh * 0.14f, p)
    p.color = -1; p.textAlign = Paint.Align.CENTER; p.textSize = bw * 0.055f; p.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); cv.drawText(artist.take(24), cx, t + bh * 0.3f, p)
    val wy = t + bh * 0.46f; p.color = 0xFF111317.toInt(); cv.drawRoundRect(RectF(cx - bw * 0.34f, wy - bw * 0.1f, cx + bw * 0.34f, wy + bw * 0.1f), bw * 0.1f, bw * 0.1f, p)
    for (k in intArrayOf(-1, 1)) reel(cv, cx + k * bw * 0.2f, wy, bw * 0.085f, if (k < 0) angle else -angle)
    p.style = Paint.Style.FILL; p.color = 0xFF17181C.toInt(); cv.drawRect(l + bw * 0.18f, t + bh * 0.72f, l + bw * 0.82f, t + bh, p)
    p.color = 0xFF8A8D94.toInt(); for (sx in floatArrayOf(0.04f, 0.96f)) for (sy in floatArrayOf(0.06f, 0.94f)) cv.drawCircle(l + bw * sx, t + bh * sy, bw * 0.014f, p)
  }
  private fun reel(cv: Canvas, x: Float, y: Float, r: Float, a: Float) {
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.25f; p.color = 0xFF9A9CA3.toInt(); cv.drawCircle(x, y, r, p)
    p.strokeWidth = r * 0.12f; for (i in 0 until 6) { val ang = Math.toRadians((a + i * 60).toDouble()); cv.drawLine(x, y, x + (r * 0.9f * Math.cos(ang)).toFloat(), y + (r * 0.9f * Math.sin(ang)).toFloat(), p) }
    p.style = Paint.Style.FILL; p.color = 0xFFD8D8DC.toInt(); cv.drawCircle(x, y, r * 0.22f, p)
  }
}
