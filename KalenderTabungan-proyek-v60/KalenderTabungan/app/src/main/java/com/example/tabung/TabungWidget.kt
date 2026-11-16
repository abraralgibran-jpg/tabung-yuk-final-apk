package com.example.tabung

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/** Widget 1: gambar tabungan, jumlah terkumpul, dan kurangnya. */
class TabungWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        draw(c, m, ids)
    }

    override fun onDeleted(c: Context, ids: IntArray) {
        WidgetKit.clear(c, ids)
    }

    companion object {
        private const val TAG = "TabungWidget"

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
            val rv = RemoteViews(c.packageName, R.layout.widget)
            rv.setTextViewText(R.id.t, "Tabung Yuk")
            rv.setTextViewText(R.id.a, "Ketuk untuk membuka")
            rv.setTextViewText(R.id.o, "")
            rv.setViewVisibility(R.id.b, View.GONE)
            rv.setViewVisibility(R.id.k, View.GONE)
            rv.setViewVisibility(R.id.ph, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.root, WidgetKit.open(c, w * 8, 0L, false))
            return rv
        }

        private fun build(c: Context, w: Int): RemoteViews {
            val tb = WidgetKit.pick(c, w)
            val rv = RemoteViews(c.packageName, R.layout.widget)
            if (tb == null) {
                rv.setTextViewText(R.id.t, "Tabung Yuk")
                rv.setTextViewTextSize(R.id.a, TypedValue.COMPLEX_UNIT_SP, 15f)
                rv.setTextViewText(R.id.a, "Belum ada tabungan")
                rv.setTextViewText(R.id.o, "Ketuk untuk membuat")
                rv.setViewVisibility(R.id.b, View.GONE)
                rv.setViewVisibility(R.id.k, View.GONE)
                rv.setImageViewResource(R.id.img, R.drawable.widget_img_bg)
                rv.setViewVisibility(R.id.ph, View.VISIBLE)
                rv.setOnClickPendingIntent(R.id.root, WidgetKit.open(c, w * 8, 0L, false))
                rv.setOnClickPendingIntent(R.id.f, WidgetKit.open(c, w * 8 + 1, 0L, false))
            } else {
                val s = tb.saved
                rv.setTextViewText(R.id.t, tb.n.ifEmpty { "Tabungan" })
                rv.setTextViewTextSize(R.id.a, TypedValue.COMPLEX_UNIT_SP, 19f)
                rv.setTextViewText(R.id.a, WidgetKit.rp(s, tb.c))
                rv.setTextViewText(R.id.o, "dari " + WidgetKit.rp(tb.t, tb.c) + " · " + tb.pct + "%")
                rv.setViewVisibility(R.id.b, View.VISIBLE)
                rv.setProgressBar(R.id.b, 100, tb.pct, false)
                rv.setViewVisibility(R.id.k, View.VISIBLE)
                rv.setTextViewText(
                    R.id.k,
                    if (tb.t > 0 && s >= tb.t) "\uD83C\uDF89 Target tercapai!"
                    else "Kurang " + WidgetKit.rp(maxOf(0.0, tb.t - s), tb.c)
                )
                val th = WidgetKit.thumb(c, tb.id)
                if (th != null) {
                    rv.setImageViewBitmap(R.id.img, th)
                    rv.setViewVisibility(R.id.ph, View.GONE)
                } else {
                    rv.setImageViewResource(R.id.img, R.drawable.widget_img_bg)
                    rv.setViewVisibility(R.id.ph, View.VISIBLE)
                }
                rv.setOnClickPendingIntent(R.id.root, WidgetKit.open(c, w * 8, tb.id, false))
                rv.setOnClickPendingIntent(R.id.f, WidgetKit.open(c, w * 8 + 1, tb.id, true))
            }
            return rv
        }
    }
}
