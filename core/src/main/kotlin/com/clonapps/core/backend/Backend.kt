package com.clonapps.core.backend

/** Backend yang dievaluasi. Hanya WORK_PROFILE yang diimplementasikan untuk instalasi. */
enum class BackendId { WORK_PROFILE, SYSTEM_CLONE_PROFILE, VIRTUALIZATION_ENGINE }

enum class BackendAvailability {
    AVAILABLE,

    /** Ada di perangkat, tetapi tidak dapat dikendalikan aplikasi biasa (mis. Dual Apps ROM). */
    DETECTED_NOT_CONTROLLABLE,
    NOT_AVAILABLE,

    /** Sengaja tidak diimplementasikan; lihat docs/BACKENDS.md. */
    NOT_IMPLEMENTED,
}

data class BackendCapabilities(
    val id: BackendId,
    val availability: BackendAvailability,
    /** Jumlah profil yang dapat dibuat/dikelola backend ini. */
    val maxProfiles: Int,
    /** Jumlah instance per aplikasi dalam satu profil. */
    val maxInstancesPerAppPerProfile: Int,
    val keepsOriginalSignature: Boolean,
    val requiresUserConfirmationPerInstall: Boolean,
    val canPauseProfileDirectly: Boolean,
)

object Capacity {
    /**
     * Android mengizinkan satu profil kerja (managed profile) per pengguna pada implementasi AOSP,
     * dan satu paket hanya dapat terpasang sekali per profil. Jadi kapasitas backend profil kerja =
     * 1 salinan tambahan per aplikasi. Tidak ada janji "clone tanpa batas".
     */
    fun workProfile(available: Boolean, canPauseDirectly: Boolean) = BackendCapabilities(
        id = BackendId.WORK_PROFILE,
        availability = if (available) BackendAvailability.AVAILABLE else BackendAvailability.NOT_AVAILABLE,
        maxProfiles = 1,
        maxInstancesPerAppPerProfile = 1,
        keepsOriginalSignature = true,
        requiresUserConfirmationPerInstall = true,
        canPauseProfileDirectly = canPauseDirectly,
    )

    fun instancesAvailable(caps: List<BackendCapabilities>): Int =
        caps.filter { it.availability == BackendAvailability.AVAILABLE }
            .sumOf { it.maxProfiles * it.maxInstancesPerAppPerProfile }
}
