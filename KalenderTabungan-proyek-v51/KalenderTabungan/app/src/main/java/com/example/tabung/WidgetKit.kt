package com.example.tabung

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Satu tabungan (diambil dari ringkasan JSON yang dikirim HTML lewat Android.sync). */
class Item(
    val id: Long,
    val n: String,
    val t: Double,
    val st: String,
    val e: String,
    val off: Set<Int>,
    val v: Map<String, Double>,
    val c: String = "IDR"
) {
    val saved: Double get() = v.values.sum()
    val pct: Int get() = if (t > 0) (saved / t * 100).toInt().coerceIn(0, 100) else 0
}

/** Bahan bersama untuk kedua widget. */
object WidgetKit {
    private fun prefs(c: Context) = c.getSharedPreferences("w", Context.MODE_PRIVATE)

    fun items(c: Context): List<Item> {
        val out = ArrayList<Item>()
        try {
            val j = JSONObject(prefs(c).getString("j", "{}") ?: "{}")
            val a = j.optJSONArray("L") ?: return out
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val v = HashMap<String, Double>()
                val vo = o.optJSONObject("v")
                if (vo != null) for (k in vo.keys()) v[k] = vo.optDouble(k, 0.0)
                val off = HashSet<Int>()
                val oa = o.optJSONArray("o")
                if (oa != null) for (x in 0 until oa.length()) off.add(oa.optInt(x))
                out.add(
                    Item(
                        o.optLong("id"), o.optString("n"), o.optDouble("t", 0.0),
                        o.optString("st"), o.optString("e"), off, v,
                        o.optString("c", "IDR").ifEmpty { "IDR" }
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun setSel(c: Context, wid: Int, id: Long) {
        prefs(c).edit().putLong("sel_$wid", id).apply()
    }

    /** Tabungan yang dipilih untuk widget ini; kalau belum/tidak ada, pakai yang pertama. */
    fun pick(c: Context, wid: Int): Item? {
        val l = items(c)
        val s = prefs(c).getLong("sel_$wid", 0L)
        return l.firstOrNull { it.id == s } ?: l.firstOrNull()
    }

    fun clear(c: Context, ids: IntArray) {
        val e = prefs(c).edit()
        for (i in ids) {
            e.remove("sel_$i")
            e.remove("mo_$i")
        }
        e.apply()
    }

    fun rp(n: Double, c: String = "IDR"): String {
        val f = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US).apply { groupingSeparator = '.' })
        val r = Math.round(n)
        if (c == "IDR") return (if (r < 0) "-Rp " else "Rp ") + f.format(Math.abs(r))
        return try {
            val nf = java.text.NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            nf.currency = java.util.Currency.getInstance(c)
            nf.maximumFractionDigits = 0
            nf.minimumFractionDigits = 0
            nf.format(r)
        } catch (e: Exception) {
            (if (r < 0) "-" else "") + c + " " + f.format(Math.abs(r))
        }
    }

    // ---------- gambar kecil (thumbnail) untuk widget ----------
    private fun tf(c: Context, id: String) = File(c.filesDir, "th_" + id.filter { it.isDigit() } + ".png")

    fun thumb(c: Context, id: Long): Bitmap? {
        val f = tf(c, id.toString())
        return try {
            if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        } catch (e: Throwable) {
            null
        }
    }

    /** data:image/...;base64 -> persegi 240px bersudut membulat. String kosong = hapus. */
    fun saveThumb(c: Context, id: String, data: String) {
        val f = tf(c, id)
        if (data.isEmpty()) {
            f.delete()
            return
        }
        try {
            val raw = Base64.decode(data.substringAfter("base64,", ""), Base64.DEFAULT)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
            var ss = 1
            while (minOf(o.outWidth, o.outHeight) / ss > 480) ss *= 2
            val bm = BitmapFactory.decodeByteArray(
                raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = ss }
            ) ?: return
            val sz = 240
            val out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            // seluruh foto dimuat utuh (tidak dipotong), diletakkan di tengah
            val sc = minOf(sz.toFloat() / bm.width, sz.toFloat() / bm.height)
            val w = bm.width * sc
            val h = bm.height * sc
            val dst = RectF((sz - w) / 2, (sz - h) / 2, (sz + w) / 2, (sz + h) / 2)
            cv.drawRoundRect(dst, 40f, 40f, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            cv.drawBitmap(bm, null, dst, p)
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
        }
    }

    fun cleanThumbs(c: Context, keep: Set<Long>) {
        val ok = keep.map { "th_$it.png" }.toSet()
        c.filesDir.listFiles()?.forEach {
            if (it.name.startsWith("th_") && it.name.endsWith(".png") && it.name !in ok) it.delete()
        }
    }

    // ---------- klik ----------
    fun open(c: Context, rc: Int, id: Long, fill: Boolean): PendingIntent =
        PendingIntent.getActivity(
            c, rc, Intent(c, MainActivity::class.java).putExtra("id", id).putExtra("fill", fill),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun refresh(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        try {
            TabungWidget.draw(c, m, m.getAppWidgetIds(ComponentName(c, TabungWidget::class.java)))
        } catch (e: Exception) {
            android.util.Log.e("WidgetKit", "Refresh widget Tabungan gagal", e)
        }
        try {
            KalenderWidget.draw(c, m, m.getAppWidgetIds(ComponentName(c, KalenderWidget::class.java)))
        } catch (e: Exception) {
            android.util.Log.e("WidgetKit", "Refresh widget Kalender gagal", e)
        }
    }
}
