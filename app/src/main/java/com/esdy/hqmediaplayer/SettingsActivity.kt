package com.esdy.hqmediaplayer

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {
  private val prefs by lazy { getSharedPreferences("esdy", 0) }
  private lateinit var box: LinearLayout
  private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    val sv = ScrollView(this).apply { setBackgroundColor(T.bg(this@SettingsActivity)) }
    box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(30), 0, dp(24)) }; sv.addView(box); setContentView(sv); build()
  }
  private fun build() { box.removeAllViews()
    val head = TextView(this).apply { text = "←   Settings"; textSize = 22f; setTextColor(T.text(this@SettingsActivity)); setPadding(dp(18), dp(12), dp(18), dp(12)); setOnClickListener { finish() } }; box.addView(head)
    sec("Appearance")
    row("Theme", T.THEME_N[T.mode(this)]) { AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(T.THEME_N.toTypedArray(), T.mode(this)) { d, w -> prefs.edit().putInt("theme", w).apply(); d.dismiss(); recreate() }.show() }
    row("Accent colour", T.ACCENT_N[prefs.getInt("accent", 0).coerceIn(0, 7)]) { AlertDialog.Builder(this).setTitle("Accent colour").setSingleChoiceItems(T.ACCENT_N.toTypedArray(), prefs.getInt("accent", 0)) { d, w -> prefs.edit().putInt("accent", w).apply(); d.dismiss(); recreate() }.show() }
    sw("Big cards on home", "Show folders and videos as large cards", "cards")
    sec("General"); sw("Resume where you left off", "Continue a video from its last position", "resume"); sw("Keep screen on while playing", "Stops the screen from sleeping", "keep_on")
    sec("Gestures"); sw("Swipe to seek", "Swipe left or right on the video", "g_seek"); sw("Brightness swipe", "Swipe up or down on the left side", "g_br"); sw("Volume swipe", "Swipe up or down on the right side", "g_vol")
    sw("Double-tap to skip", "Double-tap the left or right side", "g_dbl"); sw("Pinch to zoom", "Pinch with two fingers", "g_zoom"); sw("Press and hold for 2x speed", "Hold your finger on the video", "g_long")
    row("Skip step", "${prefs.getInt("seek_step", 10)} seconds") { AlertDialog.Builder(this).setTitle("Skip step").setItems(arrayOf("5 seconds", "10 seconds", "20 seconds", "30 seconds")) { _, w -> prefs.edit().putInt("seek_step", intArrayOf(5, 10, 20, 30)[w]).apply(); build() }.show() }
    sec("Privacy"); row("App lock PIN", if (prefs.getString("pin", null) == null) "Off" else "On") { pinDlg() }
    val hid = prefs.getStringSet("hidden", emptySet()) ?: emptySet<String>(); row("Hidden folders", "${hid.size} hidden") { hideDlg() }
    sec("About"); row("ESDY HQ Media Player", "Version 3.0 · LibVLC engine") {}
  }
  private fun sec(t: String) { box.addView(TextView(this).apply { text = t; textSize = 13f; setTextColor(T.accent(this@SettingsActivity)); setPadding(dp(18), dp(20), dp(18), dp(4)) }) }
  private fun row(t: String, s: String, f: () -> Unit) {
    val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)); setOnClickListener { f() } }
    c.addView(TextView(this).apply { text = t; textSize = 16f; setTextColor(T.text(this@SettingsActivity)) }); c.addView(TextView(this).apply { text = s; textSize = 12f; setTextColor(T.sub(this@SettingsActivity)) }); box.addView(c) }
  private fun sw(t: String, s: String, key: String) {
    val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(10)) }
    val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
    c.addView(TextView(this).apply { text = t; textSize = 16f; setTextColor(T.text(this@SettingsActivity)) }); c.addView(TextView(this).apply { text = s; textSize = 12f; setTextColor(T.sub(this@SettingsActivity)) })
    r.addView(c); r.addView(SwitchCompat(this).apply { isChecked = prefs.getBoolean(key, true); setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() } }); box.addView(r) }
  private fun ask(title: String, cb: (String) -> Unit) { val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
    AlertDialog.Builder(this).setTitle(title).setView(et).setPositiveButton("OK") { _, _ -> cb(et.text.toString()) }.setNegativeButton("Cancel", null).show() }
  private fun setPin() { ask("New PIN (4 to 8 digits)") { p -> if (p.length in 4..8) { prefs.edit().putString("pin", p).apply(); build() } else Toast.makeText(this, "PIN must be 4 to 8 digits", Toast.LENGTH_SHORT).show() } }
  private fun pinDlg() { val cur = prefs.getString("pin", null)
    if (cur == null) { setPin(); return }
    AlertDialog.Builder(this).setItems(arrayOf("Change PIN", "Remove PIN")) { _, w -> ask("Enter current PIN") { c ->
      if (c != cur) Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show() else if (w == 0) setPin() else { prefs.edit().remove("pin").apply(); build() } } }.show() }
  private fun hideDlg() { val names = intent.getStringArrayListExtra("folders") ?: arrayListOf(); val hid = (prefs.getStringSet("hidden", emptySet()) ?: emptySet<String>()).toMutableSet()
    if (names.isEmpty()) { Toast.makeText(this, "No folders found yet", Toast.LENGTH_SHORT).show(); return }
    AlertDialog.Builder(this).setTitle("Hide folders").setMultiChoiceItems(names.toTypedArray(), BooleanArray(names.size) { names[it] in hid }) { _, i, c -> if (c) hid.add(names[i]) else hid.remove(names[i]) }
      .setPositiveButton("Save") { _, _ -> prefs.edit().putStringSet("hidden", HashSet(hid)).apply(); build() }.setNegativeButton("Cancel", null).show() }
}
