package com.clonapps.profile

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebView
import com.clonapps.admin.ClonAppsAdminReceiver
import com.clonapps.core.json.JsonValue
import com.clonapps.core.json.jArr
import com.clonapps.core.json.jBool
import com.clonapps.core.json.jNum
import com.clonapps.core.json.jObj
import com.clonapps.core.json.jStr
import com.clonapps.core.model.InstallMethod
import com.clonapps.platform.Pm
import com.clonapps.profile.ProfileProtocol.Result
import com.clonapps.profile.ProfileProtocol.ResultCode
import java.io.File
import java.io.FileInputStream

/** Operasi yang dijalankan ClonApps sebagai profile owner DI DALAM profil kerja. */
class ProfileOps(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager
    private val admin = ClonAppsAdminReceiver.component(context)

    val isProfileOwner: Boolean get() = dpm.isProfileOwnerApp(context.packageName)

    sealed class Outcome {
        data class Done(val result: Result) : Outcome()

        /** Menunggu hasil PackageInstaller (mungkin perlu konfirmasi pengguna). */
        data class Waiting(val opKey: String, val sessionId: Int?, val steps: List<String>, val method: InstallMethod) : Outcome()
    }

    fun installedVersion(pkg: String): Long? = try {
        Pm.packageInfo(pm, pkg, 0).longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Urutan mekanisme (setiap langkah dicatat di [Result.steps]):
     * 1) aplikasi sistem → enableSystemApp; 2) sudah ada → selesai;
     * 3) installExistingPackage (sering ditolak untuk profil non-affiliated — hasil dicatat apa adanya);
     * 4) PackageInstaller session dari base + split APK yang dapat dibaca, dengan konfirmasi pengguna.
     */
    fun install(pkg: String, payload: ProfileProtocol.InstallPayload): Outcome {
        val steps = mutableListOf<String>()
        if (!isProfileOwner) return Outcome.Done(Result(ResultCode.NOT_PROFILE_OWNER))

        installedVersion(pkg)?.let { v ->
            if (v >= payload.versionCode) {
                return Outcome.Done(Result(ResultCode.OK, method = InstallMethod.ALREADY_PRESENT, versionCode = v, steps = listOf("sudah ada versi $v")))
            }
            steps += "versi lama di profil: $v"
        }

        if (payload.isSystemApp) {
            return try {
                dpm.enableSystemApp(admin, pkg)
                steps += "enableSystemApp=ok"
                val v = installedVersion(pkg)
                if (v != null) {
                    Outcome.Done(Result(ResultCode.OK, method = InstallMethod.ENABLE_SYSTEM_APP, versionCode = v, steps = steps))
                } else {
                    Outcome.Done(Result(ResultCode.NOT_INSTALLED, "enableSystemApp tidak menghadirkan paket", steps = steps))
                }
            } catch (e: IllegalArgumentException) {
                steps += "enableSystemApp=${e.javaClass.simpleName}"
                Outcome.Done(Result(ResultCode.SYSTEM_APP_ENABLE_FAILED, e.message, steps = steps))
            } catch (e: SecurityException) {
                steps += "enableSystemApp=SecurityException"
                Outcome.Done(Result(ResultCode.SYSTEM_APP_ENABLE_FAILED, e.message, steps = steps))
            }
        }

        // installExistingPackage: dokumentasi mensyaratkan profil affiliated. Dicoba sekali dan hasil nyatanya dicatat.
        try {
            val ok = dpm.installExistingPackage(admin, pkg)
            steps += "installExistingPackage=$ok"
            if (ok) {
                installedVersion(pkg)?.let { v ->
                    return Outcome.Done(Result(ResultCode.OK, method = InstallMethod.INSTALL_EXISTING_PACKAGE, versionCode = v, steps = steps))
                }
            }
        } catch (e: SecurityException) {
            steps += "installExistingPackage=SecurityException"
        } catch (e: RuntimeException) {
            steps += "installExistingPackage=${e.javaClass.simpleName}"
        }

        // Salin base + split.
        val files = listOf(payload.basePath) + payload.splitPaths
        val unreadable = files.filterNot { File(it).let { f -> f.isFile && f.canRead() } }
        if (unreadable.isNotEmpty()) {
            steps += "tidak terbaca: ${unreadable.joinToString { it.substringAfterLast('/') }}"
            return Outcome.Done(Result(ResultCode.FILES_UNREADABLE, steps = steps))
        }
        val archive = Pm.archiveInfo(pm, payload.basePath)
        if (archive == null || archive.packageName != pkg || archive.longVersionCode != payload.versionCode) {
            steps += "arsip=${archive?.packageName}@${archive?.longVersionCode}"
            return Outcome.Done(Result(ResultCode.ARCHIVE_MISMATCH, steps = steps))
        }

        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setSize(files.sumOf { File(it).length() })
        }
        val sessionId = installer.createSession(params)
        steps += "session=$sessionId files=${files.size}"
        try {
            installer.openSession(sessionId).use { session ->
                files.forEachIndexed { idx, path ->
                    val f = File(path)
                    val name = if (idx == 0) "base.apk" else "split_$idx.apk"
                    FileInputStream(f).use { input ->
                        session.openWrite(name, 0, f.length()).use { out ->
                            input.copyTo(out, 1 shl 20)
                            session.fsync(out)
                        }
                    }
                }
                val key = InstallStatusReceiver.installKey(sessionId)
                session.commit(statusSender(key, sessionId).intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "session write/commit gagal", e)
            runCatching { installer.abandonSession(sessionId) }
            steps += "commit=${e.javaClass.simpleName}"
            val code = if (e is java.io.IOException && e.message?.contains("ENOSPC", true) == true) ResultCode.INSTALLER_FAILED else ResultCode.INTERNAL_ERROR
            return Outcome.Done(Result(code, e.message, installerStatus = if (code == ResultCode.INSTALLER_FAILED) PackageInstaller.STATUS_FAILURE_STORAGE else null, sessionId = sessionId, steps = steps))
        }
        return Outcome.Waiting(InstallStatusReceiver.installKey(sessionId), sessionId, steps, InstallMethod.PACKAGE_INSTALLER_COPY)
    }

    fun uninstall(pkg: String): Outcome {
        if (!isProfileOwner) return Outcome.Done(Result(ResultCode.NOT_PROFILE_OWNER))
        if (pkg == context.packageName) return Outcome.Done(Result(ResultCode.BAD_REQUEST, "ClonApps tidak menghapus dirinya sendiri"))
        val info = try { Pm.packageInfo(pm, pkg, 0) } catch (e: PackageManager.NameNotFoundException) { null }
            ?: return Outcome.Done(Result(ResultCode.OK, "sudah tidak ada di profil", steps = listOf("tidak terpasang")))
        val ai = info.applicationInfo
        if (ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
            // Aplikasi sistem tidak dapat dihapus dari image; profile owner dapat menyembunyikannya di profil ini saja.
            val hidden = runCatching { dpm.setApplicationHidden(admin, pkg, true) }.getOrDefault(false)
            return Outcome.Done(
                Result(
                    if (hidden) ResultCode.OK else ResultCode.INTERNAL_ERROR,
                    if (hidden) "aplikasi sistem disembunyikan di profil" else "gagal menyembunyikan",
                    method = InstallMethod.ENABLE_SYSTEM_APP,
                    steps = listOf("setApplicationHidden=$hidden"),
                ),
            )
        }
        val key = InstallStatusReceiver.uninstallKey(pkg)
        pm.packageInstaller.uninstall(pkg, statusSender(key, pkg.hashCode()).intentSender)
        return Outcome.Waiting(key, null, listOf("PackageInstaller.uninstall"), InstallMethod.PACKAGE_INSTALLER_COPY)
    }

    fun abandon(sessionId: Int): Result {
        val installer = pm.packageInstaller
        val mine = installer.mySessions.any { it.sessionId == sessionId }
        if (!mine) return Result(ResultCode.OK, "session sudah tidak ada")
        return runCatching { installer.abandonSession(sessionId); Result(ResultCode.OK, "session dibatalkan") }
            .getOrElse { Result(ResultCode.INTERNAL_ERROR, it.message) }
    }

    fun launch(pkg: String): Result {
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return Result(ResultCode.LAUNCH_FAILED, "tidak ada activity peluncur di profil")
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result(ResultCode.OK, steps = listOf("startActivity di profil kerja"))
        } catch (e: RuntimeException) {
            Result(ResultCode.LAUNCH_FAILED, "${e.javaClass.name}: ${e.message}")
        }
    }

    /** Informasi profil untuk diagnosis. Tidak memuat data milik aplikasi lain. */
    fun info(): JsonValue.Obj {
        val um = context.getSystemService(UserManager::class.java)
        val restrictions = um.userRestrictions
        val active = restrictions.keySet().filter { restrictions.getBoolean(it, false) }.sorted()
        fun resolvable(i: Intent): Boolean = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null
        fun present(p: String): Boolean = try { Pm.packageInfo(pm, p, 0).applicationInfo?.enabled == true } catch (e: PackageManager.NameNotFoundException) { false }
        val webview = runCatching { WebView.getCurrentWebViewPackage()?.packageName }.getOrNull()
        val packages = Pm.installedPackages(pm).filter { p ->
            val ai = p.applicationInfo ?: return@filter false
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            p.packageName != context.packageName && (!system || pm.getLaunchIntentForPackage(p.packageName) != null)
        }
        return jObj(
            "profileOwner" to jBool(isProfileOwner),
            "sdk" to jNum(Build.VERSION.SDK_INT),
            "canRequestPackageInstalls" to jBool(pm.canRequestPackageInstalls()),
            "unknownSourcesRestricted" to jBool(
                active.contains(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) ||
                    (Build.VERSION.SDK_INT >= 29 && active.contains(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)),
            ),
            "restrictions" to jArr(active.map { JsonValue.Str(it) }),
            "integration" to jObj(
                "camera" to jBool(resolvable(Intent(MediaStore.ACTION_IMAGE_CAPTURE))),
                "filePicker" to jBool(resolvable(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))),
                "browser" to jBool(resolvable(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))),
                "share" to jBool(resolvable(Intent(Intent.ACTION_SEND).setType("text/plain"))),
                "googlePlayServices" to jBool(present("com.google.android.gms")),
                "playStore" to jBool(present("com.android.vending")),
                "webViewProvider" to jStr(webview),
            ),
            "packages" to jArr(
                packages.map { p ->
                    jObj(
                        "package" to jStr(p.packageName),
                        "versionCode" to jNum(p.longVersionCode),
                        "system" to jBool(((p.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0),
                    )
                },
            ),
        )
    }

    fun requestProfileLock(): Result = try {
        context.startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Result(ResultCode.OK, "layar kunci profil kerja dibuka")
    } catch (e: RuntimeException) {
        Result(ResultCode.INTERNAL_ERROR, e.message)
    }

    /** Menghapus SELURUH profil kerja milik ClonApps. Hanya dipanggil setelah konfirmasi ganda. */
    fun wipeProfile(): Result {
        if (!isProfileOwner) return Result(ResultCode.NOT_PROFILE_OWNER)
        dpm.wipeData(0)
        return Result(ResultCode.OK)
    }

    private fun statusSender(key: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, InstallStatusReceiver::class.java)
            .setPackage(context.packageName)
            .putExtra(InstallStatusReceiver.EXTRA_OP_KEY, key)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private companion object {
        const val TAG = "ClonAppsProfileOps"
    }
}

/** Ringkasan PackageInfo untuk pemanggil yang hanya butuh identitas. */
internal val PackageInfo.identity: String get() = "$packageName@$longVersionCode"
