# Arsitektur ClonApps

## 1. Definisi "cloning"

Cloning di ClonApps = **memasang atau mengaktifkan aplikasi yang sama di lingkungan Android terpisah**
(profil kerja), lalu pengguna login sendiri di dalamnya. ClonApps **tidak** menyalin sesi login, token,
kredensial, atau data privat aplikasi asli. Package name dan tanda tangan asli dipertahankan.

## 2. Audit dokumentasi resmi (24 Nov 2026) dan keputusan

| Temuan | Sumber | Dampak pada desain |
|---|---|---|
| Profil kerja memisahkan aplikasi & data dari profil utama; launcher dibagi | `DevicePolicyManager` reference, CDD 3.9.2 | Fondasi backend |
| Instalasi tanpa konfirmasi hanya untuk *device owner* atau *affiliated profile owner* | Javadoc `PackageInstaller` / `PackageInstallerService` (AOSP) | Setiap instalasi clone **selalu** meminta konfirmasi Android. Tidak ada janji instalasi diam-diam. |
| `installExistingPackage` untuk profil kerja mensyaratkan afiliasi | `DevicePolicyManager` | Dicoba sekali, hasil nyata dicatat di langkah diagnostik; fallback ke `PackageInstaller` |
| `ACTION_PROVISION_MANAGED_PROFILE` hanya untuk provisioning "DPC-first"; DPC Android 12+ wajib menangani `GET_PROVISIONING_MODE` & `ADMIN_POLICY_COMPLIANCE` | source.android.com › Provision for device management; Android 12 enterprise changes | Ketiga handler diimplementasikan, dilindungi `BIND_DEVICE_ADMIN` |
| `requestQuietModeEnabled` hanya untuk launcher default foreground atau pemegang `MANAGE_USERS`/`MODIFY_QUIET_MODE` | `UserManager` reference | Tombol "Jeda profil" memanggil API asli lalu melaporkan penolakan apa adanya + jalan alternatif |
| `LauncherApps.startMainActivity/getActivityList` dapat dipakai untuk profil terkelola; pembatasan khusus hanya untuk *private space* | `LauncherApps` reference | Peluncuran clone memakai komponen milik profil tujuan sehingga tidak membuka aplikasi asli |
| Sejak Android 12 izin sensor di profil kerja tidak dapat diberikan DPC (`setPermissionGrantState`) | Android 12 enterprise changes | Kamera/mikrofon diizinkan sendiri oleh pengguna di aplikasi clone |
| Satu profil kerja per pengguna (AOSP) | CDD 3.9.2 | Kapasitas: 1 profil, 1 salinan per aplikasi; ditampilkan di Beranda |
| Clone profile / Dual Apps bawaan ROM tidak dapat dikendalikan aplikasi biasa | (API sistem/tersembunyi) | Hanya dideteksi sebagai "profil lain", tidak disentuh |

## 3. Pemisahan modul

- `:core` (Kotlin murni) — model status, antrean, transisi, validasi, pemeriksaan, pemetaan kesalahan, JSON, HMAC, sensor.
- `:app` — `admin` (DPC), `profile` (sisi profil kerja), `platform` (API Android), `data` (penyimpanan), `domain` (koordinator), `ui` (Compose).
- `:fixture` — aplikasi uji.

Antarmuka, pengelolaan profil (`ProfileEnvironment`, `ProfileSetup`), penemuan aplikasi (`AppDiscovery`), instalasi
(`ProfileOps`), peluncuran (`ProfileLauncher`), konfigurasi (`Stores`, `PairingStore`) dan diagnostik (`DeviceProbe`,
`DiagnosticReport`) berada di kelas terpisah.

## 4. Alur

### Provisioning
1. Cek `FEATURE_MANAGED_USERS`, profil yang sudah ada, `isProvisioningAllowed`, dan ketersediaan layar provisioning.
2. Bila ada profil lain yang bukan milik ClonApps → berhenti dengan diagnosis (**tidak** menghapus/mengambil alih).
3. Buat kunci pemasangan 256-bit → kirim melalui `EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE` (jalur resmi).
4. Android menampilkan layar persetujuan. Hasil `RESULT_CANCELED` dicatat sebagai `PROVISIONING_CANCELLED`.
5. Di dalam profil, `ProfileSetup.finish` (dari ketiga jalur sistem, idempoten): simpan kunci, `setProfileName`,
   `setProfileEnabled`, `addCrossProfileIntentFilter` **hanya** untuk action perintah ClonApps, aktifkan
   `ProfileCommandActivity` hanya di profil kerja, coba hapus pembatasan sumber tidak dikenal (bila kewenangan PO).

### Perintah lintas profil
- Transport: `startActivityForResult` → `IntentForwarderActivity` sistem → `ProfileCommandActivity` di profil kerja.
- Lapisan keamanan: (a) cross-profile filter hanya untuk satu action, (b) komponen nonaktif di profil utama,
  (c) permission `signature`, (d) HMAC-SHA256 atas perintah+paket+hash payload+nonce+waktu, jendela 2 menit, nonce anti-replay persisten.
- `HELLO` (tanpa data sensitif) dan `PAIR` (wajib dikonfirmasi pengguna di profil kerja) tidak butuh kunci.

### Instalasi clone
`QUEUED → CHECKING → INSTALLING → AWAITING_USER → VERIFYING → SUCCEEDED` (atau `FAILED`/`CANCELLED`/`INTERRUPTED`).
1. Pemeriksaan ulang: base APK, kelengkapan split (`splitNames` vs `splitSourceDirs`, terbaca), identitas arsip
   (`getPackageArchiveInfo` cocok paket & versi), ABI (`lib/<abi>/` dan split `config.<abi>`), pustaka bersama,
   ruang (perkiraan 2× APK + 100 MB), profil aktif/terbuka, kemampuan instalasi.
2. Profil kerja: aplikasi sistem → `enableSystemApp`; sudah ada versi ≥ → selesai; `installExistingPackage` (dicatat);
   kalau tidak, session `PackageInstaller` (base + semua split) → `STATUS_PENDING_USER_ACTION` → layar konfirmasi Android.
3. Verifikasi independen dari profil utama (`LauncherApps.getApplicationInfo(…, user)`).
4. Hasil instalasi & hasil peluncuran disimpan sebagai status terpisah.

### Ketahanan antrean
- Antrean immutable disimpan atomik (`AtomicFile`) setiap transisi; hanya satu job berjalan.
- Ketuk berulang tidak membuat job ganda (deduplikasi per paket+profil).
- Rotasi: hasil activity diteruskan ulang oleh `ActivityResultRegistry` ke `CommandChannel`.
- Process death / reboot: job yang tercatat berjalan → `INTERRUPTED` → dicocokkan dengan keberadaan nyata paket di profil
  → `SUCCEEDED` / diantrekan ulang / `FAILED` setelah batas percobaan. Pembaruan dengan versi tak terverifikasi tetap
  `INTERRUPTED` sampai dapat diamati.
- Coba lagi dibatasi 3 percobaan dan hanya untuk kegagalan yang `retryable`.

### Peluncuran
`LauncherApps.getActivityList(pkg, profil)` → `startMainActivity(komponen profil, profil)`. Status menjadi
"peluncuran diminta"; "terverifikasi berjalan" hanya setelah konfirmasi pengguna. Bila `LauncherApps` gagal,
alternatif **di profil yang sama** lewat perintah `LAUNCH` (dicatat, bukan pindah backend diam-diam).

### Hapus clone / hapus profil
- Hapus clone → `PackageInstaller.uninstall` di profil kerja (konfirmasi Android) → verifikasi absen → verifikasi
  aplikasi asli masih terpasang (dicatat di riwayat). Aplikasi sistem disembunyikan (`setApplicationHidden`), bukan dihapus.
- Hapus profil → dialog yang menjelaskan data yang hilang + ketik kata kunci → konfirmasi kedua di profil kerja → `wipeData(0)` oleh PO profil itu saja.

## 5. Status

`NOT_CHECKED, READY, NEEDS_USER_ACTION, IN_PROGRESS, INSTALLED, LAUNCH_REQUESTED, VERIFIED_RUNNING, FAILED`
diturunkan oleh `StatusResolver` dari keberadaan nyata di profil, job aktif, catatan lokal, dan hasil pemeriksaan.

Setiap kegagalan menyimpan: tahap, kode mentah, penyebab diketahui, hal yang belum diketahui (per tahap),
tindakan pemulihan, dan apakah masuk akal dicoba lagi.

## 6. Privasi & keamanan
- Tanpa server, akun, atau telemetri. `allowBackup=false` + aturan ekstraksi data mengecualikan semuanya.
- Komponen diekspor hanya yang wajib (launcher, handler DPC dengan `BIND_DEVICE_ADMIN`, penerima perintah dengan permission signature).
- Laporan diagnostik & detail kesalahan melewati `Redactor` (token, JWT, email, IP, nomor telepon, jalur data per-pengguna, URI konten).
- Tidak memakai Accessibility, root, unlock bootloader, factory reset, atau perubahan partisi.
