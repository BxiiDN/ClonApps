package com.clonapps.core.model

/**
 * Status yang ditampilkan ke pengguna untuk satu aplikasi pada satu profil tujuan.
 *
 * Status ini sengaja dipisah: "terpasang" tidak sama dengan "peluncuran diminta",
 * dan "peluncuran diminta" tidak sama dengan "terverifikasi berjalan".
 */
enum class CloneStatus {
    /** Belum ada pemeriksaan persyaratan untuk aplikasi ini. */
    NOT_CHECKED,

    /** Semua pemeriksaan yang dapat diverifikasi lolos; boleh dicoba. */
    READY,

    /** Perlu tindakan pengguna (persetujuan sistem, membuka jeda profil, memberi izin, dll.). */
    NEEDS_USER_ACTION,

    /** Sedang dalam antrean atau sedang diproses. */
    IN_PROGRESS,

    /** Paket terverifikasi ada di profil tujuan. */
    INSTALLED,

    /** Permintaan peluncuran sudah dikirim ke sistem. Belum bukti aplikasi berjalan. */
    LAUNCH_REQUESTED,

    /** Pengguna mengonfirmasi aplikasi benar-benar terbuka di profil tujuan. */
    VERIFIED_RUNNING,

    /** Gagal; lihat [Failure] untuk tahap, kode, penyebab dan pemulihan. */
    FAILED,
}

/** Status instalasi tersimpan terpisah dari status peluncuran. */
enum class InstallState { NONE, INSTALLED, REMOVED, FAILED }

/** Status peluncuran tersimpan terpisah dari status instalasi. */
enum class LaunchState { NONE, REQUESTED, USER_CONFIRMED_RUNNING, FAILED }

/** Mekanisme yang benar-benar dipakai untuk menghadirkan aplikasi di profil. */
enum class InstallMethod {
    /** DevicePolicyManager.enableSystemApp — aplikasi sistem yang sudah ada di image. */
    ENABLE_SYSTEM_APP,

    /** DevicePolicyManager.installExistingPackage — hanya bila platform mengizinkan. */
    INSTALL_EXISTING_PACKAGE,

    /** PackageInstaller session dengan base APK + split dari instalasi asli. */
    PACKAGE_INSTALLER_COPY,

    /** Ditemukan sudah ada di profil (dipasang di luar ClonApps, mis. lewat Play Store kerja). */
    ALREADY_PRESENT,
}

/** Jenis profil yang terlihat oleh aplikasi. */
enum class ProfileOwnership {
    /** Tidak ada profil tambahan. */
    NONE,

    /** Profil kerja dengan ClonApps sebagai profile owner dan sudah dipasangkan. */
    OURS_PAIRED,

    /** ClonApps terpasang di profil, tetapi kunci komunikasi belum/tidak cocok. */
    OURS_UNPAIRED,

    /** Profil dikelola aplikasi lain atau oleh ROM (Dual Apps, Second Space, organisasi). */
    FOREIGN,
}
