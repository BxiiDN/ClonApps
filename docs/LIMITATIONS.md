# Keterbatasan yang diketahui

## Dari Android (tidak dapat diatasi aplikasi biasa)
- **Satu profil kerja** per pengguna dan **satu salinan** per aplikasi di profil itu.
- **Setiap instalasi butuh konfirmasi** Android (ClonApps bukan device owner/affiliated profile owner).
- Bila sudah ada profil kerja lain (kantor/sekolah/aplikasi lain), ClonApps **tidak dapat** membuat profil sendiri dan tidak akan mengambil alih.
- **Jeda profil** langsung hanya untuk launcher default/aplikasi sistem; ClonApps menampilkan penolakan dan jalan alternatif.
- ClonApps **tidak dapat membaca log/crash** aplikasi lain, dan tidak dapat mengamati apakah aplikasi lain berjalan. Gunakan panduan ADB di Diagnostik.
- **Izin kamera/mikrofon** di profil kerja diberikan pengguna sendiri (DPC Android 12+ tidak boleh memberikannya).
- **Backup data privat** aplikasi lain tidak tersedia secara sah; ekspor hanya mencakup pengaturan ClonApps.
- Aplikasi sistem hanya dapat diaktifkan bila ada di image profil kerja; tidak disalin.

## Dari aplikasi target
- Aplikasi dapat menolak profil kerja, memeriksa lisensi/integritas, atau membatasi satu perangkat per akun. ClonApps mencatat hasilnya dan **tidak** memalsukan atau menonaktifkan pemeriksaan.
- Aplikasi yang bergantung pada **pustaka bersama statis** (mis. Chrome dengan TrichromeLibrary) dapat gagal dengan `INSTALL_FAILED_MISSING_SHARED_LIBRARY` bila pustaka itu tidak ada di profil kerja.
- Aplikasi yang menargetkan API sangat lama dapat ditolak Android baru (`INSTALL_FAILED_DEPRECATED_SDK_VERSION`).
- Salinan yang dipasang ClonApps tidak otomatis diperbarui toko aplikasi; gunakan "Perbarui dari versi utama" atau toko di profil kerja.
- Layanan Google di profil kerja membutuhkan akun Google yang ditambahkan di profil kerja.

## Dari ROM/perangkat (perlu diuji)
- Beberapa ROM (termasuk varian MIUI/HyperOS) dilaporkan membatasi atau mengubah alur profil kerja, dan memiliki fitur Dual Apps/Second Space sendiri. ClonApps mendeteksi dan melaporkan; perilaku di Xiaomi Pad 6 **belum diuji**.
- ROM tanpa layar ManagedProvisioning tidak dapat membuat profil kerja.

## Dari lingkungan pembuatan proyek ini
- Lingkungan pembuatan tidak memiliki Android SDK dan tidak dapat mengunduh dependensi Gradle; APK dibangun dan diuji di GitHub Actions.
- `distributionSha256Sum` wrapper belum diisi (checksum tidak dapat diambil dari lingkungan pembuatan); `gradle/actions/setup-gradle` memvalidasi `gradle-wrapper.jar`.
- Belum ada keystore rilis; semua APK adalah **debug**.
