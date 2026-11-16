package com.example.tabung

import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Alarm nabung: berbunyi + layar penuh (AlarmActivity), bisa ditunda atau dimatikan. */
class AlarmRx : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent) {
        try {
            when (i.action) {
                ACT_FIRE -> {
                    plan(c) // jadwalkan hari berikutnya dulu
                    if (shouldRing(c)) ring(c)
                }
                ACT_SNOOZE_FIRE -> ring(c)
                ACT_SNOOZE -> snooze(c)
                ACT_STOP -> stop(c)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memproses alarm", e)
        }
    }

    companion object {
        private const val TAG = "AlarmRx"
        const val ACT_FIRE = "com.example.tabung.ALARM_FIRE"
        const val ACT_SNOOZE_FIRE = "com.example.tabung.ALARM_SNOOZE_FIRE"
        const val ACT_SNOOZE = "com.example.tabung.ALARM_SNOOZE"
        const val ACT_STOP = "com.example.tabung.ALARM_STOP"
        private const val CH = "al"
        private const val NID = 3
        private const val SNOOZE_MIN = 10
        private const val RING_MAX_MS = 120_000L

        private fun prefs(c: Context) = c.getSharedPreferences("w", Context.MODE_PRIVATE)

        private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        private fun rx(c: Context, code: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(c, code, Intent(c, AlarmRx::class.java).setAction(action), flags())

        /** Simpan pengaturan alarm lalu jadwalkan ulang. dm: bit 0 = Minggu ... bit 6 = Sabtu. */
        fun set(c: Context, on: Boolean, h: Int, m: Int, dm: Int) {
            prefs(c).edit().putBoolean("al_on", on).putInt("al_h", h).putInt("al_m", m).putInt("al_dm", dm).apply()
            plan(c)
        }

        /** Jadwalkan alarm berikutnya sesuai pengaturan tersimpan. */
        fun plan(c: Context) {
            val p = prefs(c)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = rx(c, 7, ACT_FIRE)
            am.cancel(pi)
            if (!p.getBoolean("al_on", false)) return
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR_OF_DAY, p.getInt("al_h", 20))
            t.set(Calendar.MINUTE, p.getInt("al_m", 0))
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            if (t.timeInMillis <= System.currentTimeMillis() + 1000) t.add(Calendar.DAY_OF_YEAR, 1)
            schedule(c, am, t.timeInMillis, pi)
            Log.i(TAG, "Alarm dijadwalkan: " + Date(t.timeInMillis))
        }

        private fun schedule(c: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
            val open = PendingIntent.getActivity(c, 8, Intent(c, MainActivity::class.java), flags())
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, open), pi)
            } catch (e: Exception) {
                Log.w(TAG, "setAlarmClock gagal, pakai alarm biasa")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        /** Bunyi hanya di hari yang dipilih dan kalau hari ini belum menabung. */
        private fun shouldRing(c: Context): Boolean {
            val p = prefs(c)
            val j = try {
                JSONObject(p.getString("j", "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val done = j.optBoolean("d") && j.optString("dt") == today
            val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // 0 = Minggu
            val chosen = ((p.getInt("al_dm", 127) shr dow) and 1) == 1
            Log.i(TAG, "Alarm berbunyi: sudah nabung=$done, hari dipilih=$chosen")
            return !done && chosen
        }

        fun summary(c: Context): String {
            val j = try {
                JSONObject(prefs(c).getString("j", "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val n = j.optString("n")
            return if (n.isNotEmpty()) "Tabungan “$n”: ${j.optInt("p").coerceIn(0, 100)}%"
            else "Ketuk untuk mengisi hari ini"
        }

        /** Bunyikan alarm: notifikasi bersuara alarm + layar penuh. */
        fun ring(c: Context) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(CH, "Alarm nabung", NotificationManager.IMPORTANCE_HIGH)
                ch.setSound(sound, attr)
                ch.enableVibration(true)
                ch.vibrationPattern = longArrayOf(0, 700, 400, 700, 400, 700)
                ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                nm.createNotificationChannel(ch)
            }
            val screen = PendingIntent.getActivity(
                c, 9,
                Intent(c, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags()
            )
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CH) else Notification.Builder(c)
            if (Build.VERSION.SDK_INT < 26) {
                b.setPriority(Notification.PRIORITY_MAX)
                b.setSound(sound, android.media.AudioManager.STREAM_ALARM)
                b.setVibrate(longArrayOf(0, 700, 400, 700, 400, 700))
            }
            if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(RING_MAX_MS)
            b.setSmallIcon(R.drawable.ic_stat_c)
                .setColor(Color.parseColor("#0A9C8C"))
                .setContentTitle("Alarm nabung")
                .setContentText(summary(c))
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(screen)
                .setFullScreenIntent(screen, true)
                .setOngoing(true)
                .addAction(0, "Tunda $SNOOZE_MIN mnt", rx(c, 10, ACT_SNOOZE))
                .addAction(0, "Matikan", rx(c, 11, ACT_STOP))
            val n = b.build()
            n.flags = n.flags or Notification.FLAG_INSISTENT
            nm.notify(NID, n)
        }

        /** Matikan bunyi dan tutup notifikasi alarm. */
        fun stop(c: Context) {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NID)
        }

        /** Tunda SNOOZE_MIN menit. */
        fun snooze(c: Context) {
            stop(c)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            schedule(c, am, System.currentTimeMillis() + SNOOZE_MIN * 60_000L, rx(c, 12, ACT_SNOOZE_FIRE))
        }
    }
}

/** Layar alarm penuh: tampil di atas layar kunci. */
class AlarmActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#0B1F1D"))
        root.setPadding(dp(28), dp(28), dp(28), dp(28))

        fun label(text: String, sp: Float, bold: Boolean, color: String): TextView {
            val t = TextView(this)
            t.text = text
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            t.setTextColor(Color.parseColor(color))
            t.gravity = Gravity.CENTER
            if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            return t
        }

        fun button(text: String, bg: String, fg: String, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            b.setAllCaps(false)
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            b.setTextColor(Color.parseColor(fg))
            val shape = GradientDrawable()
            shape.cornerRadius = dp(28).toFloat()
            shape.setColor(Color.parseColor(bg))
            b.background = shape
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
            lp.topMargin = dp(14)
            b.layoutParams = lp
            b.setOnClickListener { onClick() }
            return b
        }

        root.addView(label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 64f, true, "#FFFFFF"))
        root.addView(label("Saatnya menabung!", 24f, true, "#7FE0D3"))
        val sub = label(AlarmRx.summary(this), 16f, false, "#C9D6D4")
        sub.setPadding(0, dp(8), 0, dp(32))
        root.addView(sub)

        root.addView(button("Isi tabungan sekarang", "#0A9C8C", "#FFFFFF") {
            AlarmRx.stop(this)
            startActivity(
                Intent(this, MainActivity::class.java).putExtra("fill", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        })
        root.addView(button("Tunda 10 menit", "#24403D", "#FFFFFF") {
            AlarmRx.snooze(this)
            finish()
        })
        root.addView(button("Matikan", "#00000000", "#C9D6D4") {
            AlarmRx.stop(this)
            finish()
        })
        setContentView(root)
    }
}
