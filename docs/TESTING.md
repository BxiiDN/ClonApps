# Pengujian

Hasil dipisah per lapisan. **Build APK yang berhasil tidak membuktikan kompatibilitas aplikasi target.**

## 1. Unit test JVM

| Lapisan | Cakupan | Status |
|---|---|---|
| `:core` (36 test) | antrean & transisi, pembatalan dari tiap fase, retry terbatas, pencocokan setelah process death, job terputus, uninstall, berkas antrean rusak, validasi paket (split hilang/tak terbaca, arsip tidak cocok, ABI, pustaka bersama), precheck (ruang, profil tidak ada/dijeda/asing, sudah ada/perlu update, aplikasi sistem, sumber tidak dikenal), pemetaan kode PackageInstaller & peluncuran, sensor laporan, HMAC (valid/replay/diubah/kunci lain/kedaluwarsa), JSON, ekspor/impor | **Lulus** — di lingkungan pembuatan (Kotlin 2.0.21 + JUnit 4.13.2) dan di CI (Kotlin 2.4.21), run [45337342196](https://github.com/BxiiDN/ClonApps/actions/runs/45337342196) |
| `:app` JVM | kelengkapan teks ID/EN untuk setiap nilai enum, protokol payload/hasil, pemetaan hasil profil → kegagalan, sensor detail | `ProtocolAndFailuresTest` (4 test) **lulus** di lingkungan pembuatan dengan stub `Intent`; `StringResourcesTest` belum dijalankan di sana (pemeriksaan setara dilakukan `tools/gen_strings.py`). Keduanya **lulus** di CI, run 45337342196 |

## 2. Emulator (GitHub Actions, API 34 x86_64)

- `connectedDebugAndroidTest`: deteksi perangkat nyata, snapshot paket sendiri konsisten, daftar aplikasi tanpa
  komponen sistem tanpa launcher, keadaan profil tanpa kunci tidak pernah "siap", berkas atomik.
- `scripts/emulator-profile-test.sh` (profil kerja dibuat lewat **ADB khusus uji**, bukan alur ClonApps):
  fixture sebagai split APK, native library di kedua profil, **data terpisah** (instance ID berbeda),
  menghapus salinan profil tidak menghapus aplikasi asli dan datanya, ClonApps berjalan di kedua profil tanpa crash.
- Belum otomatis: alur instalasi ClonApps end-to-end (butuh mengetuk layar konfirmasi Android).

Status (run [45337342196](https://github.com/BxiiDN/ClonApps/actions/runs/45337342196), 24 Nov 2026, emulator `sdk_gphone64_x86_64` API 34):
- `connectedDebugAndroidTest` (5 test `PlatformInstrumentedTest`): **lulus**.
- `emulator-profile-test.sh`: **16 lulus / 0 gagal** — split APK via bundletool (base + split ABI), native library dimuat
  di kedua profil, instance ID berbeda antara user 0 dan profil kerja uji (user 10), hapus salinan profil tidak
  menghapus aplikasi asli dan instance-nya tetap sama, ClonApps berjalan di profil utama dan sebagai profile owner
  profil uji tanpa crash.
- Catatan: profil kerja uji dibuat lewat ADB, jadi ini menguji premis backend dan ketahanan ClonApps, **bukan**
  alur provisioning + instalasi ClonApps end-to-end.

## 3. Perangkat fisik

Belum ada. Rencana di Xiaomi Pad 6 (deteksi ROM/versi dilakukan aplikasi saat berjalan):

1. Diagnostik → simpan laporan (bukti Android/ROM/ABI/fitur).
2. Buat profil kerja; catat hasil.
3. Fixture: pasang `ClonAppsFixture-split-debug-*.apks` via `bundletool install-apks` atau APK tunggal; clone lewat ClonApps;
   bandingkan `instance=` antara profil utama dan profil kerja (`adb logcat -s ClonAppsFixture`); uji tombol notifikasi,
   pemilih file, kamera, tautan, berbagi, job latar belakang di kedua profil.
4. Kondisi gagal: ruang tidak cukup, profil dijeda, batal di layar konfirmasi, izin ditolak, aplikasi asli diperbarui
   di tengah antrean, tutup paksa ClonApps saat menunggu konfirmasi lalu buka lagi (pencocokan ulang).
5. Hapus clone → pastikan aplikasi asli dan datanya utuh.
6. Aplikasi populer hanya dari sumber resmi (Play Store) — **jangan** memasukkan APK pihak ketiga ke repository.
7. Diagnostik → **Simpan matriks uji (CSV)** lalu salin barisnya ke tabel di bawah.

## Matriks pengujian

| Aplikasi | Versi | Perangkat | Android | ROM | Backend | Instalasi | Peluncuran | Fitur yang benar-benar diperiksa | Catatan |
|---|---|---|---|---|---|---|---|---|---|
| ClonApps Fixture | 1.0-fixture | Emulator API 34 | 14 | AOSP google_apis | Profil kerja (ADB uji) | berhasil (install-existing via ADB) | berhasil (am start) | data terpisah, native lib, split APK, hapus salinan tanpa menyentuh asli | bukan alur ClonApps penuh |
| — | — | Xiaomi Pad 6 | *dideteksi saat uji* | *dideteksi saat uji* | Profil kerja | belum diuji | belum diuji | — | — |
