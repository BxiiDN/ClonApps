package com.clonapps.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.clonapps.core.validate.Abis
import com.clonapps.core.validate.ApkFile
import com.clonapps.core.validate.ArchiveIdentity
import com.clonapps.core.validate.PackageSnapshot
import java.io.File
import java.util.zip.ZipFile

data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val hasLauncher: Boolean,
    /** Total ukuran file APK (base + split). Null bila file tidak dapat dibaca. */
    val apkBytes: Long?,
    val splitCount: Int,
)

/**
 * Menemukan aplikasi terpasang di profil utama. Memakai QUERY_ALL_PACKAGES (diperlukan karena fungsi
 * inti ClonApps adalah memilih aplikasi terpasang) dan hanya membaca metadata publik serta file APK
 * yang memang dapat dibaca aplikasi lain. Tidak membaca data privat aplikasi.
 */
class AppDiscovery(private val context: Context) {
    private val pm = context.packageManager

    fun listApps(includeSystem: Boolean): List<AppEntry> {
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName }.toSet()
        return Pm.installedPackages(pm).mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            if (info.packageName == context.packageName) return@mapNotNull null
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val hasLauncher = info.packageName in launchable
            // Komponen sistem tanpa activity peluncur (layanan, provider, overlay) tidak ditawarkan sama sekali.
            if (system && !hasLauncher) return@mapNotNull null
            if (system && !includeSystem) return@mapNotNull null
            toEntry(info, ai, system, hasLauncher)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun entry(pkg: String): AppEntry? = try {
        val info = Pm.packageInfo(pm, pkg, 0)
        val ai = info.applicationInfo ?: return null
        val launch = pm.getLaunchIntentForPackage(pkg) != null
        toEntry(info, ai, (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0, launch)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun toEntry(info: PackageInfo, ai: ApplicationInfo, system: Boolean, hasLauncher: Boolean): AppEntry {
        val files = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        val sizes = files.map { File(it).let { f -> if (f.canRead()) f.length() else -1L } }
        return AppEntry(
            packageName = info.packageName,
            label = ai.loadLabel(pm).toString(),
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            isSystem = system,
            isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            hasLauncher = hasLauncher,
            apkBytes = if (sizes.any { it < 0 }) null else sizes.sum(),
            splitCount = ai.splitSourceDirs?.size ?: 0,
        )
    }

    /** Potret lengkap untuk validasi. Dipanggil di thread IO. */
    fun snapshot(pkg: String): PackageSnapshot? {
        val info = try { Pm.packageInfo(pm, pkg, 0) } catch (e: PackageManager.NameNotFoundException) { return null }
        val ai = info.applicationInfo ?: return null
        fun apk(path: String) = File(path).let { ApkFile(path, it.exists(), it.canRead(), if (it.canRead()) it.length() else 0L) }
        val base = ai.sourceDir?.let { apk(it) }
        val splits = ai.splitSourceDirs?.map { apk(it) } ?: emptyList()
        val declared = info.splitNames?.toList() ?: emptyList()
        val abis = mutableSetOf<String>()
        declared.mapNotNullTo(abis) { Abis.fromSplitName(it) }
        if (base?.readable == true) {
            runCatching {
                ZipFile(base.path).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        Abis.fromZipEntry(entries.nextElement().name)?.let { abis += it }
                    }
                }
            }
        }
        val archive = base?.takeIf { it.readable }?.let { Pm.archiveInfo(pm, it.path) }
        return PackageSnapshot(
            packageName = pkg,
            versionCode = info.longVersionCode,
            isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            hasLauncherActivity = pm.getLaunchIntentForPackage(pkg) != null,
            base = base,
            declaredSplitNames = declared,
            splitFiles = splits,
            archiveIdentity = archive?.let { ArchiveIdentity(it.packageName, it.longVersionCode) },
            nativeAbis = abis,
            sharedLibraryFiles = ai.sharedLibraryFiles?.filter { it.endsWith(".apk") }?.toList() ?: emptyList(),
        )
    }

    fun installPaths(pkg: String): Pair<String, List<String>>? = try {
        val ai = Pm.packageInfo(pm, pkg, 0).applicationInfo ?: return null
        val base = ai.sourceDir ?: return null
        base to (ai.splitSourceDirs?.toList() ?: emptyList())
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
