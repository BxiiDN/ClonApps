package com.clonapps.platform

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Kemampuan perangkat yang DIBACA dari API publik saat aplikasi berjalan — tidak diasumsikan
 * dari nama model. Deteksi ROM bersifat heuristik dan selalu menyertakan bukti yang dipakai.
 */
data class DeviceReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildDisplay: String,
    val buildIncremental: String,
    val rom: String,
    val romEvidence: List<String>,
    val supportedAbis: List<String>,
    val is64Bit: Boolean,
    val totalRamBytes: Long,
    val lowRamDevice: Boolean,
    val dataFreeBytes: Long,
    val dataTotalBytes: Long,
    val featureManagedUsers: Boolean,
    val featureDeviceAdmin: Boolean,
    val provisioningAllowed: Boolean,
    val provisioningUiPresent: Boolean,
    val smallestWidthDp: Int,
)

object DeviceProbe {
    fun probe(context: Context): DeviceReport {
        val pm = context.packageManager
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        val (rom, evidence) = detectRom(pm)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return DeviceReport(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildDisplay = Build.DISPLAY,
            buildIncremental = Build.VERSION.INCREMENTAL,
            rom = rom,
            romEvidence = evidence,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            totalRamBytes = mem.totalMem,
            lowRamDevice = am.isLowRamDevice,
            dataFreeBytes = stat.availableBytes,
            dataTotalBytes = stat.totalBytes,
            featureManagedUsers = pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS),
            featureDeviceAdmin = pm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN),
            provisioningAllowed = runCatching { dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE) }.getOrDefault(false),
            provisioningUiPresent = ProfileEnvironment.provisioningIntent(context, null).resolveActivity(pm) != null,
            smallestWidthDp = context.resources.configuration.smallestScreenWidthDp,
        )
    }

    fun dataFreeBytes(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(-1L)

    private fun installed(pm: PackageManager, pkg: String): Boolean =
        runCatching { Pm.packageInfo(pm, pkg, 0); true }.getOrDefault(false)

    /** Heuristik ROM dari properti Build publik dan paket sistem yang terlihat. */
    private fun detectRom(pm: PackageManager): Pair<String, List<String>> {
        val ev = mutableListOf<String>()
        val display = Build.DISPLAY.lowercase()
        val fp = Build.FINGERPRINT.lowercase()
        val inc = Build.VERSION.INCREMENTAL
        val candidates = mutableListOf<String>()
        if (display.contains("lineage") || fp.contains("lineage") || installed(pm, "org.lineageos.updater")) {
            candidates += "LineageOS"; ev += "lineage pada DISPLAY/FINGERPRINT/paket updater"
        }
        if (display.contains("crdroid") || installed(pm, "com.crdroid.updater")) { candidates += "crDroid"; ev += "crdroid" }
        if (display.contains("evolution") || fp.contains("evolution")) { candidates += "Evolution X"; ev += "evolution" }
        if (display.contains("pixelos") || fp.contains("pixelos")) { candidates += "PixelOS"; ev += "pixelos" }
        if (Build.MANUFACTURER.equals("Xiaomi", true) && candidates.isEmpty()) {
            when {
                inc.startsWith("OS") -> { candidates += "HyperOS"; ev += "INCREMENTAL=$inc" }
                inc.startsWith("V") -> { candidates += "MIUI"; ev += "INCREMENTAL=$inc" }
            }
            if (installed(pm, "com.miui.securitycenter")) ev += "paket com.miui.securitycenter ada"
        }
        if (candidates.isEmpty() && Build.MANUFACTURER.equals("Google", true)) candidates += "Pixel/AOSP-like"
        if (candidates.isEmpty()) { candidates += "Tidak dikenali"; ev += "DISPLAY=${Build.DISPLAY}" }
        return candidates.joinToString(" / ") to ev
    }
}
