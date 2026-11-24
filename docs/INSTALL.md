# Panduan pemasangan

1. Unduh artefak `ClonApps-debug-<commit>` dari tab **Actions** repository (atau Release pra-rilis bila tersedia).
2. Cocokkan checksum: `sha256sum ClonApps-v*-debug-*.apk` harus sama dengan baris di `SHA256SUMS.txt`.
3. Di tablet: izinkan pemasangan dari sumber ini, lalu pasang APK. Atau via ADB: `adb install -r ClonApps-v*-debug-*.apk`.
4. Buka ClonApps → **Buat profil kerja** → ikuti layar Android sampai selesai.
5. **Pilih Aplikasi** → centang aplikasi → **Periksa persyaratan** → **Mulai clone**. Setujui setiap layar konfirmasi
   Android (termasuk izin "instal aplikasi tidak dikenal" untuk ClonApps di profil kerja bila diminta).
6. Buka clone dari Beranda, shortcut, atau ikon berlencana di launcher. Login sendiri di dalam aplikasi.
7. Bila clone benar-benar terbuka dan bekerja, tekan **"Ya, aplikasi terbuka di profil kerja"** di Detail Clone.

## Xiaomi / HyperOS / MIUI
- Bila ada **Second Space** atau profil kerja lain, ClonApps akan melaporkannya dan tidak menyentuhnya.
- Bila tombol pembuatan profil gagal, buka **Diagnostik** dan kirim laporannya (sudah tersensor).
- Izinkan ClonApps berjalan di latar belakang bila ROM mematikan proses saat menunggu konfirmasi instalasi;
  ClonApps tetap mencocokkan ulang antrean saat dibuka lagi.

## Menghapus
- Satu clone: Detail Clone → **Hapus clone** (hanya salinan di profil kerja).
- Semua: Pengaturan → **Hapus seluruh profil**, atau Pengaturan Android › Akun › Hapus profil kerja.
- Aplikasi ClonApps: hapus seperti biasa (hapus profil kerjanya lebih dulu bila tidak lagi dibutuhkan).
