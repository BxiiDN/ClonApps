package com.clonapps.domain

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import com.clonapps.core.security.CommandAuth
import com.clonapps.profile.ProfileProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Activity yang dapat menjalankan intent lintas profil dan menerima hasilnya. */
interface CommandHost {
    fun launchCommand(intent: Intent)
    fun launchProvisioning(intent: Intent)
}

sealed class CommandOutcome {
    data class Replied(val result: ProfileProtocol.Result) : CommandOutcome()

    /** Tidak ada penerima di profil (filter lintas profil belum ada / bukan profil ClonApps). */
    data class NoHandler(val message: String?) : CommandOutcome()

    /** Hasil kosong: pengguna menutup layar, profil dijeda, atau proses profil berhenti. */
    object NoResult : CommandOutcome()

    /** Tidak ada layar ClonApps terbuka untuk meluncurkan perintah. */
    object NoHost : CommandOutcome()
}

/**
 * Mengirim satu perintah lintas profil pada satu waktu. Hasil dikirim kembali oleh activity host
 * melalui [onResult]; bila host dibuat ulang (rotasi) hasil tetap sampai karena callback
 * ActivityResultRegistry didaftarkan ulang dan diteruskan ke sini.
 */
class CommandChannel {
    val host = MutableStateFlow<CommandHost?>(null)
    private val mutex = Mutex()
    private var pendingId: String? = null
    private var pending: CompletableDeferred<CommandOutcome>? = null

    /** Dipanggil bila hasil tiba tanpa ada yang menunggu (mis. setelah process death). */
    var orphanListener: ((ProfileProtocol.Result?) -> Unit)? = null

    suspend fun send(
        command: ProfileProtocol.Command,
        packageName: String,
        payload: String,
        auth: CommandAuth?,
        waitForHostMs: Long = 15_000,
    ): CommandOutcome = mutex.withLock {
        val h = withTimeoutOrNull(waitForHostMs) { host.filterNotNull().first() } ?: return CommandOutcome.NoHost
        val requestId = CommandAuth.randomHex(12)
        val intent = ProfileProtocol.buildIntent(command, packageName, payload, requestId, auth)
        val deferred = CompletableDeferred<CommandOutcome>()
        pending = deferred
        pendingId = requestId
        try {
            withContext(Dispatchers.Main) { h.launchCommand(intent) }
        } catch (e: ActivityNotFoundException) {
            clear()
            return CommandOutcome.NoHandler(e.message)
        } catch (e: SecurityException) {
            clear()
            return CommandOutcome.NoHandler(e.message)
        }
        // Batas waktu mencegah antrean macet bila layar host ditutup permanen sebelum hasil tiba.
        // Sisi profil sendiri berhenti menunggu konfirmasi setelah 15 menit.
        val outcome = withTimeoutOrNull(RESULT_TIMEOUT_MS) { deferred.await() } ?: CommandOutcome.NoResult
        clear()
        outcome
    }

    fun onResult(resultCode: Int, data: Intent?) {
        val result = ProfileProtocol.Result.decode(data?.getStringExtra(ProfileProtocol.EXTRA_RESULT))
        val rid = data?.getStringExtra(ProfileProtocol.EXTRA_REQUEST_ID)
        val waiter = pending
        if (waiter == null || (rid != null && rid != pendingId)) {
            orphanListener?.invoke(result)
            return
        }
        waiter.complete(
            if (resultCode == Activity.RESULT_OK && result != null) CommandOutcome.Replied(result) else CommandOutcome.NoResult,
        )
    }

    companion object {
        const val RESULT_TIMEOUT_MS = 20 * 60 * 1000L
    }

    private fun clear() {
        pending = null
        pendingId = null
    }
}
