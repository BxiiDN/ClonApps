package com.clonapps.profile

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.clonapps.R
import com.clonapps.core.diag.Redactor
import com.clonapps.core.json.Json
import com.clonapps.core.json.JsonParseException
import com.clonapps.core.json.JsonValue
import com.clonapps.core.json.jBool
import com.clonapps.core.json.jNum
import com.clonapps.core.json.jObj
import com.clonapps.core.json.jStr
import com.clonapps.core.security.CommandAuth
import com.clonapps.core.store.PackageNames
import com.clonapps.data.PairingStore
import com.clonapps.profile.ProfileProtocol.Command
import com.clonapps.profile.ProfileProtocol.Result
import com.clonapps.profile.ProfileProtocol.ResultCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Penerima perintah di DALAM profil kerja. Komponen ini:
 * - nonaktif secara default dan hanya diaktifkan di profil kerja oleh [ProfileSetup];
 * - dilindungi permission signature `com.clonapps.permission.PROFILE_COMMAND`;
 * - memverifikasi HMAC, stempel waktu, dan nonce untuk setiap perintah yang mengubah keadaan.
 */
class ProfileCommandActivity : ComponentActivity() {
    private lateinit var status: TextView
    private var requestId: String = ""
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        setContentView(buildView())
        if (savedInstanceState?.getBoolean(KEY_STARTED) == true) {
            // configChanges di manifest mencegah ini; bila tetap terjadi, jangan jalankan perintah dua kali.
            reply(Result(ResultCode.INTERNAL_ERROR, "activity dibuat ulang; status dicocokkan ulang oleh profil utama"))
            return
        }
        lifecycleScope.launch { handle(intent) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_STARTED, true)
    }

    private suspend fun handle(intent: Intent) {
        requestId = intent.getStringExtra(ProfileProtocol.EXTRA_REQUEST_ID)?.take(64) ?: ""
        val cmd = intent.getStringExtra(ProfileProtocol.EXTRA_CMD)?.let { c -> Command.values().firstOrNull { it.name == c } }
        val pkg = intent.getStringExtra(ProfileProtocol.EXTRA_PKG).orEmpty()
        val payload = intent.getStringExtra(ProfileProtocol.EXTRA_PAYLOAD).orEmpty()
        if (cmd == null || requestId.isEmpty()) return reply(Result(ResultCode.BAD_REQUEST, "perintah tidak dikenal"))

        val store = PairingStore(this)
        val ops = ProfileOps(this)
        if (cmd.requiresAuth) {
            val auth = store.auth() ?: return reply(Result(ResultCode.NOT_PAIRED))
            val signed = ProfileProtocol.readSigned(intent) ?: return reply(Result(ResultCode.AUTH_FAILED, "tanda tangan tidak ada"))
            if (signed.command != cmd.name) return reply(Result(ResultCode.AUTH_FAILED, "perintah tidak cocok"))
            val verdict = auth.verify(signed, store.nonceStore)
            if (verdict is CommandAuth.Verdict.Rejected) {
                Log.w(TAG, "perintah ditolak: ${verdict.reason}")
                return reply(Result(ResultCode.AUTH_FAILED, verdict.reason))
            }
        }
        if (cmd in setOf(Command.INSTALL, Command.UNINSTALL, Command.LAUNCH) && !PackageNames.isValid(pkg)) {
            return reply(Result(ResultCode.BAD_REQUEST, "nama paket tidak valid"))
        }

        when (cmd) {
            Command.HELLO -> reply(
                Result(
                    ResultCode.OK,
                    info = jObj(
                        "profileOwner" to jBool(ops.isProfileOwner),
                        "paired" to jBool(store.hasSecret()),
                        "pairingId" to jStr(store.pairingId),
                        "sdk" to jNum(android.os.Build.VERSION.SDK_INT),
                        "appVersionCode" to jNum(com.clonapps.platform.Pm.packageInfo(packageManager, packageName, 0).longVersionCode),
                    ),
                ),
            )
            Command.PAIR -> pair(payload, store, ops)
            Command.INFO -> reply(withContext(Dispatchers.IO) { Result(ResultCode.OK, info = ops.info()) })
            Command.INSTALL -> {
                val p = ProfileProtocol.InstallPayload.decode(payload) ?: return reply(Result(ResultCode.BAD_REQUEST, "payload instalasi tidak valid"))
                status.text = getString(R.string.profile_cmd_installing, pkg)
                val outcome = withContext(Dispatchers.IO) { ops.install(pkg, p) }
                complete(outcome, pkg, ops, isInstall = true)
            }
            Command.UNINSTALL -> {
                status.text = getString(R.string.profile_cmd_uninstalling, pkg)
                complete(ops.uninstall(pkg), pkg, ops, isInstall = false)
            }
            Command.LAUNCH -> reply(ops.launch(pkg))
            Command.ABANDON_SESSION -> reply(payload.toIntOrNull()?.let { ops.abandon(it) } ?: Result(ResultCode.BAD_REQUEST))
            Command.SET_PROFILE_LOCK -> reply(ops.requestProfileLock())
            Command.WIPE_PROFILE -> {
                if (payload != WIPE_CONFIRMATION) return reply(Result(ResultCode.BAD_REQUEST, "konfirmasi hapus tidak ada"))
                val ok = confirm(getString(R.string.profile_cmd_wipe_title), getString(R.string.profile_cmd_wipe_body))
                if (!ok) return reply(Result(ResultCode.USER_DECLINED))
                // Balas dulu; setelah wipeData proses profil ini dihentikan sistem.
                reply(Result(ResultCode.OK, "penghapusan profil diminta"))
                ops.wipeProfile()
            }
        }
    }

    private suspend fun pair(payload: String, store: PairingStore, ops: ProfileOps) {
        if (!ops.isProfileOwner) return reply(Result(ResultCode.NOT_PROFILE_OWNER))
        val o = try { Json.parse(payload) as? JsonValue.Obj } catch (e: JsonParseException) { null }
        val secret = o?.str("secret")
        if (secret == null || (CommandAuth.fromHex(secret)?.size ?: 0) < 32) return reply(Result(ResultCode.BAD_REQUEST))
        val ok = confirm(getString(R.string.profile_cmd_pair_title), getString(R.string.profile_cmd_pair_body))
        if (!ok) return reply(Result(ResultCode.USER_DECLINED))
        store.save(secret, o.str("pairingId"))
        ProfileSetup.finish(this, null, source = "pair")
        reply(Result(ResultCode.OK, "dipasangkan ulang"))
    }

    private suspend fun complete(outcome: ProfileOps.Outcome, pkg: String, ops: ProfileOps, isInstall: Boolean) {
        when (outcome) {
            is ProfileOps.Outcome.Done -> reply(outcome.result)
            is ProfileOps.Outcome.Waiting -> {
                val steps = outcome.steps.toMutableList()
                val final = withTimeoutOrNull(WAIT_MS) {
                    InstallerEvents.events.first { e ->
                        if (e.opKey != outcome.opKey) return@first false
                        if (e.status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            steps += "konfirmasi pengguna diminta"
                            status.text = getString(R.string.profile_cmd_waiting_user)
                            e.confirmIntent?.let { runCatching { startActivity(it) }.onFailure { t -> steps += "confirm=${t.javaClass.simpleName}" } }
                            false
                        } else {
                            true
                        }
                    }
                }
                if (final == null) {
                    outcome.sessionId?.let { ops.abandon(it) }
                    return reply(Result(ResultCode.TIMEOUT, sessionId = outcome.sessionId, steps = steps))
                }
                val msg = final.message?.let { Redactor.redact(it) }
                if (final.status != PackageInstaller.STATUS_SUCCESS) {
                    return reply(
                        Result(ResultCode.INSTALLER_FAILED, msg, installerStatus = final.status, method = outcome.method, sessionId = outcome.sessionId, steps = steps),
                    )
                }
                val v = ops.installedVersion(pkg)
                val verified = if (isInstall) v != null else v == null
                steps += if (isInstall) "verifikasi: versi=$v" else "verifikasi: terhapus=${v == null}"
                reply(
                    Result(
                        if (verified) ResultCode.OK else ResultCode.NOT_INSTALLED,
                        msg, installerStatus = final.status, method = outcome.method, versionCode = v,
                        sessionId = outcome.sessionId, steps = steps,
                    ),
                )
            }
        }
    }

    private suspend fun confirm(title: String, body: String): Boolean = suspendCancellableCoroutine { cont ->
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setCancelable(false)
            .setPositiveButton(R.string.action_allow) { _, _ -> if (cont.isActive) cont.resume(true) }
            .setNegativeButton(R.string.action_deny) { _, _ -> if (cont.isActive) cont.resume(false) }
            .show()
        cont.invokeOnCancellation { dialog.dismiss() }
    }

    private fun reply(result: Result) {
        if (finished) return
        finished = true
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(ProfileProtocol.EXTRA_REQUEST_ID, requestId)
                .putExtra(ProfileProtocol.EXTRA_RESULT, result.encode()),
        )
        finish()
    }

    private fun buildView(): LinearLayout {
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply {
            text = getString(R.string.profile_cmd_processing)
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(0, pad / 2, 0, 0)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(ProgressBar(this@ProfileCommandActivity))
            addView(status)
            contentDescription = getString(R.string.profile_cmd_processing)
        }
    }

    companion object {
        private const val TAG = "ClonAppsProfileCmd"
        private const val KEY_STARTED = "started"
        private const val WAIT_MS = 15 * 60 * 1000L
        const val WIPE_CONFIRMATION = "CONFIRM_WIPE_CLONAPPS_PROFILE"
    }
}
