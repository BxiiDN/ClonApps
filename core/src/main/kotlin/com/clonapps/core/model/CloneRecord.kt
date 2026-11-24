package com.clonapps.core.model

/**
 * Catatan lokal ClonApps tentang satu clone. Ini bukan sumber kebenaran tentang
 * keberadaan paket — keberadaan selalu dicocokkan ulang dengan keadaan profil.
 */
data class CloneRecord(
    val packageName: String,
    /** Nomor seri profil (UserManager.getSerialNumberForUser), stabil lintas reboot. */
    val profileSerial: Long,
    val originalLabel: String,
    /** Label tampilan khusus di ClonApps saja. Tidak mengubah identitas paket target. */
    val customLabel: String? = null,
    val favorite: Boolean = false,
    val installState: InstallState = InstallState.NONE,
    val installMethod: InstallMethod? = null,
    val installedVersionCode: Long? = null,
    val lastInstallAt: Long? = null,
    val launchState: LaunchState = LaunchState.NONE,
    val lastLaunchAt: Long? = null,
    val lastFailure: Failure? = null,
    val updatedAt: Long = 0L,
) {
    val displayLabel: String get() = customLabel?.takeIf { it.isNotBlank() } ?: originalLabel
}
