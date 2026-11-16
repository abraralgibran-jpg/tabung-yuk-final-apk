package com.example.tabung

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

/** Muncul saat widget dipasang: pilih tabungan mana yang ditampilkan (dilewati kalau cuma ada satu). */
class WidgetConfig : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setResult(RESULT_CANCELED)
        val w = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (w == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val l = WidgetKit.items(this)
        if (l.size <= 1) {
            done(w, l.firstOrNull()?.id ?: 0L)
            return
        }
        val names = l.map { it.n.ifEmpty { "Tabungan" } + " · " + it.pct + "%" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih tabungan")
            .setItems(names) { _, i -> done(w, l[i].id) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun done(w: Int, id: Long) {
        WidgetKit.setSel(this, w, id)
        val m = AppWidgetManager.getInstance(this)
        val cls = m.getAppWidgetInfo(w)?.provider?.className ?: ""
        if (cls.endsWith("KalenderWidget")) KalenderWidget.draw(this, m, intArrayOf(w))
        else TabungWidget.draw(this, m, intArrayOf(w))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, w))
        finish()
    }
}
