package com.clonapps.profile

import android.content.Intent
import com.clonapps.core.json.Json
import com.clonapps.core.json.JsonParseException
import com.clonapps.core.json.JsonValue
import com.clonapps.core.json.jArr
import com.clonapps.core.json.jBool
import com.clonapps.core.json.jNum
import com.clonapps.core.json.jObj
import com.clonapps.core.json.jStr
import com.clonapps.core.model.InstallMethod
import com.clonapps.core.security.CommandAuth

/**
 * Protokol perintah dari ClonApps di profil utama ke ClonApps (profile owner) di profil kerja.
 *
 * Transport: startActivityForResult dengan action [ACTION]. Intent ini hanya dapat menyeberang ke
 * profil kerja karena profile owner memasang cross-profile intent filter (FLAG_PARENT_CAN_ACCESS_MANAGED)
 * untuk action ini. Activity penerima dilindungi permission signature dan setiap perintah yang
 * mengubah keadaan wajib membawa HMAC dari kunci pemasangan.
 */
object ProfileProtocol {
    const val ACTION = "com.clonapps.action.PROFILE_COMMAND"

    const val EXTRA_CMD = "com.clonapps.extra.CMD"
    const val EXTRA_PKG = "com.clonapps.extra.PKG"
    const val EXTRA_PAYLOAD = "com.clonapps.extra.PAYLOAD"
    const val EXTRA_NONCE = "com.clonapps.extra.NONCE"
    const val EXTRA_TS = "com.clonapps.extra.TS"
    const val EXTRA_MAC = "com.clonapps.extra.MAC"
    const val EXTRA_REQUEST_ID = "com.clonapps.extra.REQUEST_ID"
    const val EXTRA_RESULT = "com.clonapps.extra.RESULT"

    /** Kunci pada admin extras bundle provisioning. */
    const val ADMIN_EXTRA_SECRET = "clonapps_pairing_secret"
    const val ADMIN_EXTRA_PAIRING_ID = "clonapps_pairing_id"

    enum class Command(val requiresAuth: Boolean) {
        /** Status dasar tanpa data sensitif; dipakai untuk mengenali profil milik ClonApps. */
        HELLO(false),

        /** Pemasangan ulang kunci; wajib dikonfirmasi pengguna di layar profil kerja. */
        PAIR(false),
        INFO(true),
        INSTALL(true),
        UNINSTALL(true),
        LAUNCH(true),
        ABANDON_SESSION(true),
        SET_PROFILE_LOCK(true),
        WIPE_PROFILE(true),
    }

    enum class ResultCode {
        OK,
        AUTH_FAILED,
        NOT_PROFILE_OWNER,
        NOT_PAIRED,
        BAD_REQUEST,
        FILES_UNREADABLE,
        ARCHIVE_MISMATCH,
        INSTALLER_FAILED,
        SYSTEM_APP_ENABLE_FAILED,
        NOT_INSTALLED,
        LAUNCH_FAILED,
        USER_DECLINED,
        TIMEOUT,
        INTERNAL_ERROR,
    }

    data class InstallPayload(
        val basePath: String,
        val splitPaths: List<String>,
        val versionCode: Long,
        val isSystemApp: Boolean,
        val totalBytes: Long,
    ) {
        fun encode(): String = Json.write(
            jObj(
                "base" to jStr(basePath),
                "splits" to jArr(splitPaths.map { JsonValue.Str(it) }),
                "versionCode" to jNum(versionCode),
                "system" to jBool(isSystemApp),
                "bytes" to jNum(totalBytes),
            ),
        )

        companion object {
            fun decode(text: String?): InstallPayload? {
                val o = parseObj(text) ?: return null
                return InstallPayload(
                    basePath = o.str("base") ?: return null,
                    splitPaths = o.arr("splits").mapNotNull { (it as? JsonValue.Str)?.value },
                    versionCode = o.long("versionCode") ?: return null,
                    isSystemApp = o.bool("system") ?: false,
                    totalBytes = o.long("bytes") ?: 0L,
                )
            }
        }
    }

    data class Result(
        val code: ResultCode,
        val message: String? = null,
        val installerStatus: Int? = null,
        val method: InstallMethod? = null,
        val versionCode: Long? = null,
        val sessionId: Int? = null,
        /** Langkah yang benar-benar dicoba, untuk diagnosis (mis. "installExistingPackage=false"). */
        val steps: List<String> = emptyList(),
        val info: JsonValue.Obj? = null,
    ) {
        val ok: Boolean get() = code == ResultCode.OK

        fun encode(): String = Json.write(
            jObj(
                "code" to jStr(code.name),
                "message" to jStr(message),
                "installerStatus" to jNum(installerStatus),
                "method" to jStr(method?.name),
                "versionCode" to jNum(versionCode),
                "sessionId" to jNum(sessionId),
                "steps" to jArr(steps.map { JsonValue.Str(it) }),
                "info" to info,
            ),
        )

        companion object {
            fun decode(text: String?): Result? {
                val o = parseObj(text) ?: return null
                return Result(
                    code = o.str("code")?.let { c -> ResultCode.values().firstOrNull { it.name == c } } ?: ResultCode.INTERNAL_ERROR,
                    message = o.str("message"),
                    installerStatus = o.int("installerStatus"),
                    method = o.str("method")?.let { m -> InstallMethod.values().firstOrNull { it.name == m } },
                    versionCode = o.long("versionCode"),
                    sessionId = o.int("sessionId"),
                    steps = o.arr("steps").mapNotNull { (it as? JsonValue.Str)?.value },
                    info = o.obj("info"),
                )
            }
        }
    }

    private fun parseObj(text: String?): JsonValue.Obj? =
        try { text?.let { Json.parse(it) as? JsonValue.Obj } } catch (e: JsonParseException) { null }

    /** Membangun intent perintah. [auth] null hanya untuk perintah tanpa otorisasi. */
    fun buildIntent(command: Command, packageName: String, payload: String, requestId: String, auth: CommandAuth?): Intent {
        val intent = Intent(ACTION)
            .putExtra(EXTRA_CMD, command.name)
            .putExtra(EXTRA_PKG, packageName)
            .putExtra(EXTRA_PAYLOAD, payload)
            .putExtra(EXTRA_REQUEST_ID, requestId)
        if (command.requiresAuth) {
            requireNotNull(auth) { "Perintah $command membutuhkan kunci pemasangan" }
        }
        if (auth != null) {
            val s = auth.sign(command.name, packageName, payload)
            intent.putExtra(EXTRA_NONCE, s.nonce).putExtra(EXTRA_TS, s.timestamp).putExtra(EXTRA_MAC, s.mac)
        }
        return intent
    }

    fun readSigned(intent: Intent): CommandAuth.Signed? {
        val cmd = intent.getStringExtra(EXTRA_CMD) ?: return null
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return null
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: ""
        val nonce = intent.getStringExtra(EXTRA_NONCE) ?: return null
        val mac = intent.getStringExtra(EXTRA_MAC) ?: return null
        val ts = intent.getLongExtra(EXTRA_TS, -1L).takeIf { it > 0 } ?: return null
        return CommandAuth.Signed(cmd, pkg, payload, nonce, ts, mac)
    }
}
