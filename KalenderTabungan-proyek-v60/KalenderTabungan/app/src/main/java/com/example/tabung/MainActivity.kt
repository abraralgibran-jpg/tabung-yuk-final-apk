package com.example.tabung

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Satu-satunya layar: WebView yang memuat assets/index.html (seluruh tampilan ada di HTML). */
class MainActivity : FragmentActivity() {
    private var wentBg = false
    private var skipOnce = false
    private lateinit var wv: WebView
    private var cb: ValueCallback<Array<Uri>>? = null
    private var pendingText: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        // Tanpa garis scrollbar saat menggulir
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
        wv.addJavascriptInterface(Bridge(), "Android")
        wv.webChromeClient = object : WebChromeClient() {
            // <input type="file"> di HTML
            override fun onShowFileChooser(
                w: WebView?, callback: ValueCallback<Array<Uri>>?, p: FileChooserParams?
            ): Boolean {
                cb?.onReceiveValue(null)
                cb = callback
                val i = Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                startActivityForResult(i, 1)
                return true
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                applyTheme()
                fill(intent)
            }
        }
        wv.setBackgroundColor(if (isNight()) Color.BLACK else Color.parseColor("#F2F2F7"))
        setContentView(wv)
        wv.loadUrl("file:///android_asset/index.html")
    }

    private fun js(s: String) = wv.evaluateJavascript(s, null)

    private fun isNight() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun applyTheme() {
        val n = isNight()
        wv.setBackgroundColor(if (n) Color.BLACK else Color.parseColor("#F2F2F7"))
        val bar = if (n) Color.BLACK else Color.parseColor("#F2F2F7")
        window.statusBarColor = bar
        window.navigationBarColor = bar
        val ic = androidx.core.view.WindowCompat.getInsetsController(window, wv)
        ic.isAppearanceLightStatusBars = !n
        ic.isAppearanceLightNavigationBars = !n
        js("document.documentElement.setAttribute('data-theme','" + (if (n) "dark" else "light") + "')")
    }

    /** Dibuka dari widget / notifikasi: buka tabungan tertentu (id) dan/atau lembar "isi hari ini". */
    private fun fill(i: Intent?) {
        if (i == null) return
        val id = i.getLongExtra("id", 0L)
        if (id != 0L) {
            i.removeExtra("id")
            js("os($id)")
        }
        if (i.getBooleanExtra("fill", false)) {
            i.removeExtra("fill")
            js("refreshTK();am(TK)")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        fill(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!skipOnce) wentBg = true
    }

    override fun onStart() {
        super.onStart()
        // kembali dari latar belakang -> kunci aplikasi
        if (wentBg && !skipOnce) js("lkBg()")
        wentBg = false
        skipOnce = false
    }

    override fun onResume() {
        super.onResume()
        js("render();setRefresh()")
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        applyTheme()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // tutup lembar -> kembali ke daftar dari detail -> baru keluar
        wv.evaluateJavascript(
            "(function(){if(isOpen()){cl();return 1}" +
                "if(document.body.classList.contains('dt')){home();return 1}return 0})()"
        ) { r -> if (r != "1") finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val u: Uri? = if (res == RESULT_OK) data?.data else null
        if (req == 1) {
            cb?.onReceiveValue(if (u != null) arrayOf(u) else null)
            cb = null
            return
        }
        if (req == 3) {
            if (u == null) return
            Thread {
                val t = try {
                    contentResolver.openInputStream(u)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
                runOnUiThread {
                    if (t != null) js("onFileText(" + JSONObject.quote(t) + ",'')")
                    else js("onFileText('','File tidak bisa dibaca.')")
                }
            }.start()
            return
        }
        if (req == 4) {
            val txt = pendingText
            pendingText = null
            if (u == null || txt == null) {
                js("onSaved('cancel')")
                return
            }
            Thread {
                val ok = try {
                    contentResolver.openOutputStream(u)?.use { it.write(txt.toByteArray(Charsets.UTF_8)) } != null
                } catch (e: Exception) {
                    false
                }
                runOnUiThread { js("onSaved('" + (if (ok) "ok" else "err") + "')") }
            }.start()
            return
        }
        if (req == 2) {
            if (u == null) { js("onPhoto('','')"); return }
            Thread {
                val d = photo(u)
                runOnUiThread {
                    if (d != null) js("onPhoto(" + JSONObject.quote(d) + ",'')")
                    else js("onPhoto('','Foto tidak bisa dibaca. Coba foto lain.')")
                }
            }.start()
        }
    }

    /** Baca foto, putar sesuai EXIF, kecilkan, jadikan data:image/jpeg;base64. */
    private fun photo(u: Uri): String? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var ss = 1
            while (maxOf(o.outWidth, o.outHeight) / ss > 2400) ss *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = ss
            val bm = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: return null
            val ori = contentResolver.openInputStream(u)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            } ?: 1
            val mx = Matrix()
            when (ori) {
                6 -> mx.postRotate(90f)
                3 -> mx.postRotate(180f)
                8 -> mx.postRotate(270f)
            }
            val sc = 1200f / maxOf(bm.width, bm.height)
            if (sc < 1f) mx.postScale(sc, sc)
            val r = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, mx, true)
            val out = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            cv.drawColor(Color.WHITE)
            cv.drawBitmap(r, 0f, 0f, null)
            val b = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 85, b)
            "data:image/jpeg;base64," + Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun askNotifPermission() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= 33)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun notifOk(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    }

    /** Fungsi yang bisa dipanggil dari JavaScript: Android.xxx(...) */
    inner class Bridge {
        @JavascriptInterface
        fun pickImage() {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_GET_CONTENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                    startActivityForResult(Intent.createChooser(i, "Pilih foto"), 2)
                } catch (e: Exception) {
                    js("onPhoto('','Tidak ada aplikasi galeri atau file di HP ini.')")
                }
            }
        }

        @JavascriptInterface
        fun ask() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    val p = getSharedPreferences("w", MODE_PRIVATE)
                    if (p.getBoolean("askedN", false) &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        openAppSettings()
                    } else {
                        p.edit().putBoolean("askedN", true).apply()
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                } else {
                    openAppSettings()
                }
            }
        }

        @JavascriptInterface
        fun bioAvail(): Boolean = BiometricManager.from(this@MainActivity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        @JavascriptInterface
        fun bio() {
            runOnUiThread {
                try {
                    val exec = java.util.concurrent.Executor { r -> runOnUiThread(r) }
                    val prompt = BiometricPrompt(this@MainActivity, exec, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            js("bioOk()")
                        }

                        override fun onAuthenticationError(code: Int, msg: CharSequence) {
                            js("bioErr(" + JSONObject.quote(msg.toString()) + ")")
                        }
                    })
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Buka Tabung Yuk")
                        .setSubtitle("Gunakan sidik jari untuk membuka")
                        .setNegativeButtonText("Batal")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()
                    prompt.authenticate(info)
                } catch (e: Exception) {
                    js("bioErr('Sidik jari tidak bisa dipakai di HP ini.')")
                }
            }
        }

        @JavascriptInterface
        fun alarmOk(): Boolean =
            Build.VERSION.SDK_INT < 31 ||
                (getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

        @JavascriptInterface
        fun alarmAsk() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        skipOnce = true
                        startActivity(
                            Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM")
                                .setData(Uri.parse("package:$packageName"))
                        )
                    } catch (e: Exception) {
                        openAppSettings()
                    }
                }
            }
        }

        private fun openAppSettings() {
            skipOnce = true
            try {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                skipOnce = false
            }
        }

        @JavascriptInterface
        fun canNotify(): Boolean = notifOk()

        @JavascriptInterface
        fun test(): Boolean {
            if (!notifOk()) { askNotifPermission(); return false }
            Remind.show(this@MainActivity)
            return true
        }

        @JavascriptInterface
        fun remind(on: Boolean, h: Int, m: Int, dm: Int): String {
            val at = Remind.nextShow(Remind.plan(this@MainActivity, on, h, m, dm), dm)
            if (at == 0L) return ""
            return java.text.SimpleDateFormat("EEEE, d MMM HH.mm", java.util.Locale("id", "ID"))
                .format(java.util.Date(at))
        }

        /** Alarm nabung (berbunyi + layar penuh). dm: bit 0 = Minggu ... bit 6 = Sabtu. */
        @JavascriptInterface
        fun alarmSet(on: Boolean, h: Int, m: Int, dm: Int) {
            AlarmRx.set(this@MainActivity, on, h, m, dm)
        }

        @JavascriptInterface
        fun alarmTest(): Boolean {
            if (!notifOk()) { askNotifPermission(); return false }
            AlarmRx.ring(this@MainActivity)
            return true
        }

        /** Buka tautan https di browser HP (dipakai menu Gift). */
        @JavascriptInterface
        fun openUrl(u: String) {
            runOnUiThread {
                try {
                    if (u.startsWith("https://")) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                } catch (e: Exception) {
                }
            }
        }

        /** Simpan teks ke file pilihan pengguna (backup .json, ekspor .csv). */
        @JavascriptInterface
        fun saveFile(name: String, mime: String, text: String) {
            runOnUiThread {
                try {
                    pendingText = text
                    skipOnce = true
                    val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
                        .putExtra(Intent.EXTRA_TITLE, name)
                    startActivityForResult(i, 4)
                } catch (e: Exception) {
                    skipOnce = false
                    pendingText = null
                    js("onSaved('err')")
                }
            }
        }

        /** Pilih file backup; isinya dikirim balik ke HTML lewat onFileText(teks, pesanError). */
        @JavascriptInterface
        fun pickFile() {
            runOnUiThread {
                try {
                    skipOnce = true
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    startActivityForResult(i, 3)
                } catch (e: Exception) {
                    skipOnce = false
                    js("onFileText('','Tidak ada aplikasi file di HP ini.')")
                }
            }
        }

        /** Bagikan gambar progres (data URL JPEG) beserta teks. */
        @JavascriptInterface
        fun shareImg(dataUrl: String, text: String) {
            Thread {
                try {
                    val raw = Base64.decode(dataUrl.substringAfter("base64,", ""), Base64.DEFAULT)
                    val dir = java.io.File(cacheDir, "share")
                    dir.mkdirs()
                    val f = java.io.File(dir, "progres.jpg")
                    f.writeBytes(raw)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity, packageName + ".fileprovider", f
                    )
                    runOnUiThread {
                        try {
                            val i = Intent(Intent.ACTION_SEND).setType("image/jpeg")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .putExtra(Intent.EXTRA_TEXT, text)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            i.clipData = android.content.ClipData.newRawUri("", uri)
                            skipOnce = true
                            startActivity(Intent.createChooser(i, "Bagikan progres"))
                        } catch (e: Exception) {
                            skipOnce = false
                            js("onShared('err')")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { js("onShared('err')") }
                }
            }.start()
        }

        /** Nyalakan/matikan ringkasan mingguan (notifikasi tiap Minggu 19.00). */
        @JavascriptInterface
        fun weekly(on: Boolean) {
            Remind.planWeekly(this@MainActivity, on)
        }

        /** HTML mengirim ringkasan tabungan (JSON) untuk widget & notifikasi. */
        @JavascriptInterface
        fun sync(j: String) {
            getSharedPreferences("w", MODE_PRIVATE).edit().putString("j", j).apply()
            try {
                WidgetKit.cleanThumbs(this@MainActivity, WidgetKit.items(this@MainActivity).map { it.id }.toSet())
            } catch (e: Exception) {
            }
            WidgetKit.refresh(this@MainActivity)
        }

        /** Gambar tabungan (data URL) untuk widget; string kosong = tidak ada gambar. */
        @JavascriptInterface
        fun img(id: String, d: String) {
            Thread {
                WidgetKit.saveThumb(this@MainActivity, id, d)
                WidgetKit.refresh(this@MainActivity)
            }.start()
        }
    }
}
