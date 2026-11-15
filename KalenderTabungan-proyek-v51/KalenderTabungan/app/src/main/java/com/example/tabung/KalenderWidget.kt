package com.example.tabung

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Locale

/** Widget 2: kalender bulanan; hari yang sudah menabung diberi warna. */
class KalenderWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        draw(c, m, ids)
    }

    override fun onDeleted(c: Context, ids: IntArray) {
        WidgetKit.clear(c, ids)
    }

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        val a = i.action ?: return
        if (a != PREV && a != NEXT && a != NOW) return
        val w = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        if (w == 0) return
        try {
            val p = c.getSharedPreferences("w", Context.MODE_PRIVATE)
            val cur = p.getInt("mo_$w", 0)
            val nx = when (a) {
                PREV -> cur - 1
                NEXT -> cur + 1
                else -> 0
            }
            p.edit().putInt("mo_$w", nx).apply()
            draw(c, AppWidgetManager.getInstance(c), intArrayOf(w))
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengganti bulan widget $w", e)
        }
    }

    companion object {
        private const val TAG = "KalenderWidget"
        const val PREV = "com.example.tabung.KAL_PREV"
        const val NEXT = "com.example.tabung.KAL_NEXT"
        const val NOW = "com.example.tabung.KAL_NOW"

        private val CELLS = intArrayOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6, R.id.c7, R.id.c8, R.id.c9, R.id.c10, R.id.c11, R.id.c12, R.id.c13, R.id.c14, R.id.c15, R.id.c16, R.id.c17, R.id.c18, R.id.c19, R.id.c20, R.id.c21, R.id.c22, R.id.c23, R.id.c24, R.id.c25, R.id.c26, R.id.c27, R.id.c28, R.id.c29, R.id.c30, R.id.c31, R.id.c32, R.id.c33, R.id.c34, R.id.c35, R.id.c36, R.id.c37, R.id.c38, R.id.c39, R.id.c40, R.id.c41)
        private val MON = arrayOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        )

        private fun bc(c: Context, w: Int, a: String, k: Int): PendingIntent =
            PendingIntent.getBroadcast(
                c, w * 8 + k,
                Intent(c, KalenderWidget::class.java).setAction(a)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, w),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun draw(c: Context, m: AppWidgetManager, ids: IntArray) {
            for (w in ids) {
                try {
                    m.updateAppWidget(w, build(c, w))
                } catch (e: Exception) {
                    // Jangan sampai widget tampil "Masalah saat memuat widget": pakai tampilan darurat.
                    Log.e(TAG, "Gagal menggambar widget $w", e)
                    try {
                        m.updateAppWidget(w, fallback(c, w))
                    } catch (e2: Exception) {
                        Log.e(TAG, "Tampilan darurat widget $w juga gagal", e2)
                    }
                }
            }
        }

        private fun fallback(c: Context, w: Int): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_kal)
            rv.setTextViewText(R.id.ttl, "Tabung Yuk")
            rv.setTextViewText(R.id.ft, "Ketuk untuk membuka")
            rv.setViewVisibility(R.id.row6, View.GONE)
            rv.setOnClickPendingIntent(R.id.root, WidgetKit.open(c, w * 8, 0L, false))
            return rv
        }

        private fun build(c: Context, w: Int): RemoteViews {
            val n = Calendar.getInstance()
            val today = String.format(
                Locale.US, "%04d-%02d-%02d",
                n.get(Calendar.YEAR), n.get(Calendar.MONTH) + 1, n.get(Calendar.DAY_OF_MONTH)
            )
            val tb = WidgetKit.pick(c, w)
                val off = c.getSharedPreferences("w", Context.MODE_PRIVATE).getInt("mo_$w", 0)
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, off)
                val y = cal.get(Calendar.YEAR)
                val mo = cal.get(Calendar.MONTH)
                val first = cal.get(Calendar.DAY_OF_WEEK) - 1
                val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val rv = RemoteViews(c.packageName, R.layout.widget_kal)
                rv.setTextViewText(R.id.ttl, MON[mo] + " " + y)
                rv.setViewVisibility(R.id.row6, if (first + days > 35) View.VISIBLE else View.GONE)

                for (i in 0 until 42) {
                    val d = i - first + 1
                    if (d < 1 || d > days) {
                        rv.setTextViewText(CELLS[i], "")
                        rv.setInt(CELLS[i], "setBackgroundResource", 0)
                        continue
                    }
                    val k = String.format(Locale.US, "%04d-%02d-%02d", y, mo + 1, d)
                    val v = tb?.v?.get(k)
                    val dow = (first + d - 1) % 7
                    val isToday = k == today
                    val saved = v != null && v > 0
                    val bg = when {
                        isToday && saved -> R.drawable.cell_today_saved
                        isToday -> R.drawable.cell_today
                        tb != null && k == tb.e -> R.drawable.cell_goal
                        saved -> R.drawable.cell_saved
                        v != null && v < 0 -> R.drawable.cell_minus
                        tb != null && v == null && k < today && k >= tb.st && k <= tb.e &&
                            dow !in tb.off -> R.drawable.cell_missed
                        else -> 0
                    }
                    rv.setTextViewText(CELLS[i], d.toString())
                    rv.setInt(CELLS[i], "setBackgroundResource", bg)
                }

                val pre = String.format(Locale.US, "%04d-%02d", y, mo + 1)
                var ms = 0.0
                tb?.v?.forEach { (kk, vv) -> if (kk.startsWith(pre)) ms += vv }
                rv.setTextViewText(
                    R.id.ft,
                    if (tb == null) "Belum ada tabungan · ketuk untuk membuat"
                    else tb.n.ifEmpty { "Tabungan" } + " · bulan ini " + WidgetKit.rp(ms, tb.c)
                )

                val id = tb?.id ?: 0L
                rv.setOnClickPendingIntent(R.id.root, WidgetKit.open(c, w * 8, id, false))
                rv.setOnClickPendingIntent(R.id.f, WidgetKit.open(c, w * 8 + 1, id, tb != null))
                rv.setOnClickPendingIntent(R.id.pv, bc(c, w, PREV, 2))
                rv.setOnClickPendingIntent(R.id.nx, bc(c, w, NEXT, 3))
                rv.setOnClickPendingIntent(R.id.ttl, bc(c, w, NOW, 4))
                return rv
        }
    }
}
