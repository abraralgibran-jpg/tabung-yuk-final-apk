# Tabung Yuk (proyek Android)

Aplikasi WebView: seluruh tampilan & logika ada di satu file HTML, bagian native hanya
notifikasi, widget, dan pemilih foto.

## Struktur
| Bagian | Lokasi |
|---|---|
| Tampilan & logika (edit di sini) | `app/src/main/assets/index.html` |
| Nama aplikasi | `app/src/main/res/values/strings.xml` (`app_name`) |
| Ikon aplikasi (logo huruf C + tanda + + kalender) | `app/src/main/res/mipmap-*/` dan `drawable-nodpi/ic_launcher_foreground.png` |
| Widget Tabungan (gambar, terkumpul, kurang) | `TabungWidget.kt`, `res/layout/widget.xml`, `res/xml/widget_info.xml` |
| Widget Kalender (bulanan, ‹ ›) | `KalenderWidget.kt`, `res/layout/widget_kal.xml`, `res/xml/widget_kal_info.xml` |
| Data & gambar widget, pilih tabungan | `WidgetKit.kt`, `WidgetConfig.kt`; warna terang/gelap di `values/` dan `values-night/colors.xml` |
| Notifikasi pengingat | `Remind.kt` |
| Alarm nabung (bunyi + layar penuh, tunda/matikan) | `AlarmRing.kt` (`AlarmRx`, `AlarmActivity`) |
| Jembatan JS ke Android | `MainActivity.kt` (kelas `Bridge`) |
| Versi | `app/build.gradle.kts` (`versionCode`, `versionName`) |

## Cara jadi APK (pilih salah satu)
1. **Android Studio**: File > Open folder ini, tunggu sync, lalu Build > Build APK(s).
   Hasil: `app/build/outputs/apk/debug/app-debug.apk`
2. **Terminal** (perlu JDK 17, Android SDK, Gradle 8.7+): `gradle assembleDebug`
3. **GitHub**: upload isi folder ini ke repo, lalu buka tab Actions > Build APK > Run workflow.
   APK ada di bagian Artifacts. (`.github/workflows/build-apk.yml` sudah disiapkan)

File `gradle-wrapper.jar` tidak disertakan; Android Studio membuatnya otomatis.

## Fungsi yang dipanggil HTML (`window.Android`)
`sync(json)` ringkasan untuk widget/notifikasi (berisi daftar tabungan `L`), `img(id, dataUrl)` gambar tabungan untuk widget, `remind(on, jam, menit, maskHari)` jadwal alarm,
`canNotify()`, `ask()`, `test()` izin & tes notifikasi, `pickImage()` pilih foto, `openUrl(url)` buka tautan https di browser (menu Gift).
Native memanggil balik fungsi HTML: `render()`, `onPhoto()`, `refreshTK(); am(TK)`, `isOpen()`, `home()`.

Tambahan v2.7: `saveFile(nama, mime, teks)` simpan backup/CSV ke file (hasil: `onSaved(status)`), `pickFile()` pilih file backup (hasil: `onFileText(teks, error)`), `shareImg(dataUrl, teks)` bagikan gambar progres (lewat FileProvider), `weekly(on)` ringkasan mingguan tiap Minggu 19.00.

Tambahan v2.8: `alarmSet(on, jam, menit, maskHari)` jadwal alarm nabung, `alarmTest()` tes alarm.

## Catatan
Data tabungan disimpan di localStorage WebView. Ganti kunci tanda tangan atau uninstall
membuat data lama hilang.

## Streak api (tombol di header)
Tombol api di sebelah titik tiga (`#fbtn`), dibuka lewat `openStreak()`. Semua kodenya ada di `index.html`, blok "Streak api" tepat sebelum `function op`.
- **Cara hitung** (`fireCalc()`): hari dihitung jika ada nabung di tabungan mana pun, lewat tab Tabungan maupun Kalender (keduanya lewat `sv()` → `setL()`). Hitungan mulai dari "Tanggal dibuat" tabungan pertama (`S.fs`). Hari libur tanpa nabung tidak memutus.
- **Data tambahan di `S`**: `fs` (awal hitungan), `fa` (hari nabung dari tabungan yang tercapai/dihapus, supaya streak tidak putus), `fb` (streak terpanjang).
- **Tingkat warna** (`FIRE_T`): Padam → Percikan (1 hari, kuning) → Api (7, oranye) → Bara (15, merah) → Inferno (30, pink) → Plasma (50, ungu) → Nebula (100, biru) → Kosmik (200, cyan). Ubah angka `min` atau warna `c` di sana.
- **Gambar api**: `fireSvg()` (SVG 3 lapis: luar, tengah, inti). Animasinya CSS, hanya aktif jika streak > 0.

## Transisi menu dan halaman
Semua kodenya ada di `index.html`: blok JS "Transisi halaman & menu" (tepat sebelum `function cl`) dan blok CSS "Transisi menu" (paling bawah di `<style>`).
- **Menu/dialog** (`op()` dan `cl()`): dialog kecil muncul di tengah dengan zoom halus, layar penuh (Pengaturan, Streak, dll.) masuk dari kanan sambil halaman belakang ikut bergeser pelan. Dialog dipusatkan dengan `margin:auto`, bukan `translateY(-50%)`, supaya animasi selalu mulai dari posisi yang benar. Isi layar penuh muncul berurutan.
- **Beranda <-> Detail** (`pgGo()`): halaman lama keluar sebagai bayangan, halaman baru masuk. `os()` dan `home()` tetap sinkron, jadi tombol Kembali Android tidak berubah.
- **Pindah tab** (`tabIn()`): header diam, isi baru meluncur masuk berurutan.
- **Menu titik tiga** dan **dialog pilihan** punya animasi masuk dan keluar. **Layar Streak**: api menyala, angka naik (`countUp()`), bar terisi, daftar tingkat muncul berurutan.
- Jika HP memakai `prefers-reduced-motion`, semua animasi ini dimatikan otomatis.
