package com.example.tabung

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Alarm pengingat nabung + penjadwalan ulang setelah HP restart / aplikasi diperbarui. */
class Remind : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent) {
        val p = c.getSharedPreferences("w", Context.MODE_PRIVATE)
        val on = p.getBoolean("on", false)
        val h = p.getInt("h", 20)
        val m = p.getInt("m", 0)
        val dm = p.getInt("dm", ALL_DAYS)
        val a = i.action
        if (a == ACT_WEEKLY) {
            try {
                val wk = p.getBoolean("wk", false)
                planWeekly(c, wk)
                if (wk) showWeekly(c)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menampilkan ringkasan mingguan", e)
            }
            return
        }
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            plan(c, on, h, m, dm)
            planWeekly(c, p.getBoolean("wk", false))
            AlarmRx.plan(c)
            try {
                WidgetKit.refresh(c)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menyegarkan widget", e)
            }
            return
        }
        try {
            plan(c, on, h, m, dm) // jadwalkan besok
            if (!on) return
            val j = JSONObject(p.getString("j", "{}") ?: "{}")
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val done = j.optBoolean("d") && j.optString("dt") == today
            val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // 0 = Minggu
            val chosen = ((dm shr dow) and 1) == 1
            Log.i(TAG, "Alarm berbunyi: sudah nabung=$done, hari dipilih=$chosen")
            if (!done && chosen) show(c)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menampilkan pengingat", e)
        }
    }

    companion object {
        private const val TAG = "Remind"
        const val ALL_DAYS = 127
        const val ACT_WEEKLY = "com.example.tabung.WEEKLY"
        private const val WEEKLY_HOUR = 19

        /** Alarm mingguan: ringkasan tabungan tiap Minggu pukul 19.00. */
        fun planWeekly(c: Context, on: Boolean) {
            c.getSharedPreferences("w", Context.MODE_PRIVATE).edit().putBoolean("wk", on).apply()
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(c, 5, Intent(c, Remind::class.java).setAction(ACT_WEEKLY), flags)
            am.cancel(pi)
            if (!on) return
            val t = Calendar.getInstance()
            t.add(Calendar.DAY_OF_YEAR, (Calendar.SUNDAY - t.get(Calendar.DAY_OF_WEEK) + 7) % 7)
            t.set(Calendar.HOUR_OF_DAY, WEEKLY_HOUR)
            t.set(Calendar.MINUTE, 0)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            if (t.timeInMillis <= System.currentTimeMillis() + 1000) t.add(Calendar.DAY_OF_YEAR, 7)
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeInMillis, pi)
            } catch (e: Exception) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeInMillis, pi)
            }
        }

        private fun showWeekly(c: Context) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel("wk", "Ringkasan mingguan", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val days = HashSet<String>()
            val cal = Calendar.getInstance()
            repeat(7) {
                days.add(fmt.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            val lines = ArrayList<String>()
            for (tb in WidgetKit.items(c)) {
                var net = 0.0
                for ((k, v) in tb.v) if (k in days) net += v
                if (net > 0) {
                    lines.add("“" + tb.n.ifEmpty { "Tabungan" } + "”: +" + WidgetKit.rp(net, tb.c) + " · " + tb.pct + "% dari target")
                }
            }
            val judul = "Ringkasan tabunganmu minggu ini"
            val text = if (lines.isEmpty()) "Belum ada tabungan minggu ini. Yuk mulai lagi besok!"
            else lines[0] + (if (lines.size > 1) " (+" + (lines.size - 1) + " lainnya)" else "")
            val big = if (lines.isEmpty()) text else lines.joinToString("\n")
            val open = PendingIntent.getActivity(
                c, 6,
                Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, "wk") else Notification.Builder(c)
            b.setSmallIcon(R.drawable.ic_stat_c)
                .setColor(Color.parseColor("#0A9C8C"))
                .setContentTitle(judul)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(big))
                .setContentIntent(open)
                .setAutoCancel(true)
            nm.notify(2, b.build())
        }


        fun plan(c: Context, on: Boolean, h: Int, m: Int, dm: Int): Long {
            c.getSharedPreferences("w", Context.MODE_PRIVATE).edit()
                .putBoolean("on", on).putInt("h", h).putInt("m", m).putInt("dm", dm).apply()
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(c, 1, Intent(c, Remind::class.java), flags)
            am.cancel(pi)
            if (!on) return 0L
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR_OF_DAY, h)
            t.set(Calendar.MINUTE, m)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            if (t.timeInMillis <= System.currentTimeMillis() + 1000) t.add(Calendar.DAY_OF_YEAR, 1)
            val open = PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java), flags)
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t.timeInMillis, open), pi)
                Log.i(TAG, "Alarm tepat waktu dijadwalkan: " + Date(t.timeInMillis))
            } catch (e: Exception) {
                Log.w(TAG, "Alarm tepat waktu gagal, pakai alarm biasa")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeInMillis, pi)
            }
            return t.timeInMillis
        }

        /** Waktu pengingat yang benar-benar tampil: alarm harian jatuh di hari yang dipilih (dm). 0 = tidak ada. */
        fun nextShow(at: Long, dm: Int): Long {
            if (at == 0L) return 0L
            val s = Calendar.getInstance()
            s.timeInMillis = at
            for (n in 0 until 7) {
                if (((dm shr (s.get(Calendar.DAY_OF_WEEK) - 1)) and 1) == 1) return s.timeInMillis
                s.add(Calendar.DAY_OF_YEAR, 1)
            }
            return 0L
        }

        fun show(c: Context) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel("r", "Pengingat nabung", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val j = try {
                JSONObject(c.getSharedPreferences("w", Context.MODE_PRIVATE).getString("j", "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val n = j.optString("n")
            val p = j.optInt("p")
            val judul = "Saatnya mengisi tabunganmu"
            val ada = n.isNotEmpty()
            val persen = p.coerceIn(0, 100)
            val text = if (ada) "Tabungan “$n”: $persen%" else "Ketuk untuk mengisi hari ini"
            val open = PendingIntent.getActivity(
                c, 3,
                Intent(c, MainActivity::class.java).putExtra("fill", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val rv = RemoteViews(c.packageName, R.layout.notif_simple)
            rv.setTextViewText(R.id.nt, judul)
            rv.setTextViewText(R.id.np, text)
            if (ada) rv.setProgressBar(R.id.npb, 100, persen, false)
            else rv.setViewVisibility(R.id.npb, View.GONE)
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, "r") else Notification.Builder(c)
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH)
            b.setSmallIcon(R.drawable.ic_stat_c)
                .setColor(Color.parseColor("#0A9C8C"))
                .setContentTitle(judul)
                .setContentText(text)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(rv)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
            nm.notify(1, b.build())
        }
    }
}
