# Backend & kemampuan

| Backend | Status di ClonApps | Profil | Salinan per aplikasi | Tanda tangan asli | Konfirmasi per instalasi | Jeda langsung |
|---|---|---|---|---|---|---|
| **Profil kerja Android** (DPC-first, ClonApps = profile owner) | Diimplementasikan | 1 | 1 | Ya | Ya (profil non-affiliated) | Tidak (lihat bawah) |
| Clone profile / Dual Apps / Second Space bawaan ROM | Hanya dideteksi, tidak dikendalikan | — | — | Ya | — | — |
| Engine virtualisasi (VirtualApp-like) | Tidak disertakan | — | — | Tidak | — | — |

**Memilih banyak aplikasi ≠ banyak instance.** Banyak aplikasi dapat diantrekan sekaligus; setiap aplikasi
maksimal satu salinan di profil kerja. Jumlah total dibatasi ruang penyimpanan, bukan angka "tanpa batas".

**Jeda.** `requestQuietModeEnabled` hanya untuk launcher default atau aplikasi berizin sistem. ClonApps memanggilnya
lalu melaporkan penolakan; pengguna dapat memakai tombol "Aplikasi kerja" sistem. Label selalu "Jeda profil".

## Evaluasi engine virtualisasi

Kriteria audit yang diminta: kode sumber, lisensi, pemeliharaan, isolasi, kompatibilitas native library, bukti uji.

Keputusan: **tidak disertakan** dalam versi ini. Alasan arsitektural yang dapat diverifikasi tanpa bergantung
pada klaim README:

1. **Isolasi.** Engine semacam ini menjalankan aplikasi tamu di dalam proses/UID aplikasi host. Pemisahan data
   bergantung pada pengalihan jalur di ruang pengguna, bukan pada sandbox per-pengguna milik Android. Semua
   aplikasi tamu berbagi izin dan identitas UID host.
2. **API tersembunyi & hooking.** Engine harus mencegat Binder/framework internal, yang dilarang oleh persyaratan
   proyek ini ("jangan memakai API tersembunyi") dan rapuh terhadap pembaruan Android.
3. **Tanda tangan & integritas.** Aplikasi berjalan di bawah identitas host; pemeriksaan integritas (Play Integrity,
   SafetyNet) dan lisensi sering gagal — ClonApps tidak memalsukan pemeriksaan keamanan.
4. **Pemeliharaan & lisensi.** Proyek populer berlisensi beragam (GPL, komersial, tidak jelas) dan sebagian besar
   tidak aktif dipelihara untuk Android terbaru. Audit kode sumber spesifik per proyek **belum** dilakukan dalam
   sesi pembuatan ini; bila ingin menambah backend ini, audit tersebut wajib lebih dulu dan backend harus dipilih
   pengguna secara eksplisit (akun & data berbeda dari profil kerja — tidak boleh berpindah diam-diam).
