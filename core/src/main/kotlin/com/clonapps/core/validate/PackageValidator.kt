package com.clonapps.core.validate

import com.clonapps.core.model.FailureCause

/** Informasi satu file APK yang dapat diakses (base atau split). */
data class ApkFile(
    val path: String,
    val exists: Boolean,
    val readable: Boolean,
    val sizeBytes: Long,
)

/** Hasil membaca ulang base APK dari disk (PackageManager.getPackageArchiveInfo). */
data class ArchiveIdentity(val packageName: String, val versionCode: Long)

/** Potret paket asli di profil utama, dikumpulkan dari API PackageManager yang sah. */
data class PackageSnapshot(
    val packageName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val hasLauncherActivity: Boolean,
    val base: ApkFile?,
    /** Nama split yang dideklarasikan PackageInfo.splitNames. */
    val declaredSplitNames: List<String>,
    /** File split yang ditemukan di ApplicationInfo.splitSourceDirs. */
    val splitFiles: List<ApkFile>,
    /** Identitas base APK yang dibaca ulang dari arsip; null bila tidak dapat dibaca. */
    val archiveIdentity: ArchiveIdentity?,
    /** ABI yang dibutuhkan, dari entri lib/<abi>/ di APK atau nama split config.<abi>. */
    val nativeAbis: Set<String>,
    /** ApplicationInfo.sharedLibraryFiles — dependensi pustaka bersama, bila ada. */
    val sharedLibraryFiles: List<String> = emptyList(),
) {
    val totalApkBytes: Long get() = (base?.sizeBytes ?: 0L) + splitFiles.sumOf { it.sizeBytes }
}

enum class CheckOutcome { PASS, WARN, FAIL, UNKNOWN }

enum class CheckId {
    NOT_SELF,
    PROFILE_READY,
    PROFILE_ACTIVE,
    INSTALL_CAPABILITY,
    BASE_APK,
    SPLITS_COMPLETE,
    ARCHIVE_IDENTITY,
    ABI,
    SHARED_LIBRARIES,
    STORAGE,
    SYSTEM_APP_IN_PROFILE,
    ALREADY_IN_PROFILE,
}

data class CheckItem(
    val id: CheckId,
    val outcome: CheckOutcome,
    val cause: FailureCause? = null,
    /** Detail teknis (tanpa data pribadi), mis. nama split yang hilang. */
    val detail: String? = null,
)

/** ABI yang dikenal Android, dalam format nama folder lib/ di APK. */
object Abis {
    val KNOWN = setOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86", "x86_64", "riscv64")

    /** "config.arm64_v8a" -> "arm64-v8a". Null bila bukan split ABI. */
    fun fromSplitName(split: String): String? {
        val name = split.removePrefix("config.").replace('_', '-')
        return name.takeIf { it in KNOWN }
    }

    /** "lib/arm64-v8a/libfoo.so" -> "arm64-v8a". */
    fun fromZipEntry(entry: String): String? {
        if (!entry.startsWith("lib/") || !entry.endsWith(".so")) return null
        val abi = entry.substring(4).substringBefore('/')
        return abi.takeIf { it in KNOWN }
    }
}

object PackageValidator {

    fun validate(snapshot: PackageSnapshot, deviceAbis: List<String>): List<CheckItem> {
        val items = mutableListOf<CheckItem>()

        // Base APK
        val base = snapshot.base
        items += when {
            base == null || !base.exists -> CheckItem(CheckId.BASE_APK, CheckOutcome.FAIL, FailureCause.BASE_APK_MISSING)
            !base.readable -> CheckItem(CheckId.BASE_APK, CheckOutcome.FAIL, FailureCause.APK_UNREADABLE, base.path.substringAfterLast('/'))
            else -> CheckItem(CheckId.BASE_APK, CheckOutcome.PASS)
        }

        // Split APK lengkap: jumlah split terdeklarasi harus sama dengan file yang ada & terbaca
        val missingSplits = mutableListOf<String>()
        if (snapshot.declaredSplitNames.size != snapshot.splitFiles.size) {
            missingSplits += "declared=${snapshot.declaredSplitNames.size} files=${snapshot.splitFiles.size}"
        }
        snapshot.splitFiles.forEach { f ->
            if (!f.exists || !f.readable) missingSplits += f.path.substringAfterLast('/')
        }
        items += if (missingSplits.isEmpty()) {
            CheckItem(CheckId.SPLITS_COMPLETE, CheckOutcome.PASS, detail = "split=${snapshot.splitFiles.size}")
        } else {
            CheckItem(CheckId.SPLITS_COMPLETE, CheckOutcome.FAIL, FailureCause.SPLIT_MISSING, missingSplits.joinToString())
        }

        // Identitas arsip harus cocok dengan paket terpasang (mendeteksi update di tengah jalan)
        val id = snapshot.archiveIdentity
        items += when {
            id == null -> CheckItem(CheckId.ARCHIVE_IDENTITY, CheckOutcome.UNKNOWN, detail = "arsip tidak dapat diurai")
            id.packageName != snapshot.packageName || id.versionCode != snapshot.versionCode ->
                CheckItem(
                    CheckId.ARCHIVE_IDENTITY, CheckOutcome.FAIL, FailureCause.ARCHIVE_MISMATCH,
                    "arsip=${id.packageName}@${id.versionCode} terpasang=${snapshot.packageName}@${snapshot.versionCode}",
                )
            else -> CheckItem(CheckId.ARCHIVE_IDENTITY, CheckOutcome.PASS)
        }

        // ABI
        items += abiCheck(snapshot, deviceAbis)

        // Pustaka bersama (mis. TrichromeLibrary untuk Chrome) harus juga tersedia di profil
        items += if (snapshot.sharedLibraryFiles.isEmpty()) {
            CheckItem(CheckId.SHARED_LIBRARIES, CheckOutcome.PASS)
        } else {
            CheckItem(
                CheckId.SHARED_LIBRARIES, CheckOutcome.WARN, FailureCause.MISSING_SHARED_LIBRARY,
                snapshot.sharedLibraryFiles.joinToString { it.substringAfterLast('/') },
            )
        }
        return items
    }

    fun abiCheck(snapshot: PackageSnapshot, deviceAbis: List<String>): CheckItem {
        if (snapshot.nativeAbis.isEmpty()) return CheckItem(CheckId.ABI, CheckOutcome.PASS, detail = "tanpa native library")
        val match = snapshot.nativeAbis.intersect(deviceAbis.toSet())
        return if (match.isNotEmpty()) {
            CheckItem(CheckId.ABI, CheckOutcome.PASS, detail = "cocok: ${match.sorted().joinToString()}")
        } else {
            CheckItem(
                CheckId.ABI, CheckOutcome.FAIL, FailureCause.NO_MATCHING_ABI,
                "aplikasi=${snapshot.nativeAbis.sorted().joinToString()} perangkat=${deviceAbis.joinToString()}",
            )
        }
    }
}
