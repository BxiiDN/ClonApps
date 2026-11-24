package com.clonapps.data

import android.content.Context
import com.clonapps.core.security.CommandAuth
import com.clonapps.core.security.CommandAuth.Companion.toHex
import com.clonapps.core.security.InMemoryNonceStore
import com.clonapps.core.security.NonceStore

/**
 * Menyimpan kunci pemasangan antara ClonApps di profil utama dan di profil kerja.
 * Disimpan di penyimpanan privat aplikasi (backup dinonaktifkan di manifest).
 * Setiap pengguna/profil Android punya salinan data ClonApps sendiri, jadi kedua sisi
 * menyimpan kunci yang sama di tempat yang terpisah.
 */
class PairingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pairing", Context.MODE_PRIVATE)
    private val nonces = InMemoryNonceStore()

    init {
        // Pulihkan nonce yang baru dipakai agar replay tetap ditolak setelah proses mati.
        val saved = prefs.getStringSet(KEY_NONCES, emptySet()).orEmpty()
        nonces.restore(saved.mapNotNull { e -> e.split('@').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) } }.toMap())
    }

    val secret: ByteArray? get() = prefs.getString(KEY_SECRET, null)?.let { CommandAuth.fromHex(it) }
    val pairingId: String? get() = prefs.getString(KEY_PAIRING_ID, null)

    /** Nomor seri profil yang terhubung dengan kunci (hanya di sisi profil utama). */
    val profileSerial: Long? get() = prefs.getLong(KEY_SERIAL, -1L).takeIf { it >= 0 }

    fun hasSecret(): Boolean = secret != null

    fun newPendingSecret(): Pair<String, String> {
        val hex = CommandAuth.newSecret().toHex()
        val id = CommandAuth.randomHex(8)
        prefs.edit().putString(KEY_SECRET, hex).putString(KEY_PAIRING_ID, id).remove(KEY_SERIAL).apply()
        return hex to id
    }

    fun save(secretHex: String, pairingId: String?) {
        require(CommandAuth.fromHex(secretHex)?.size ?: 0 >= 32) { "kunci tidak valid" }
        prefs.edit().putString(KEY_SECRET, secretHex).putString(KEY_PAIRING_ID, pairingId).apply()
    }

    fun bindSerial(serial: Long) = prefs.edit().putLong(KEY_SERIAL, serial).apply()

    fun clear() = prefs.edit().clear().apply()

    fun auth(): CommandAuth? = secret?.let { CommandAuth(it) { System.currentTimeMillis() } }

    val nonceStore: NonceStore = object : NonceStore {
        override fun markIfNew(nonce: String, timestamp: Long, now: Long): Boolean {
            val fresh = nonces.markIfNew(nonce, timestamp, now)
            if (fresh) {
                prefs.edit().putStringSet(KEY_NONCES, nonces.snapshot().map { "${it.key}@${it.value}" }.toSet()).commit()
            }
            return fresh
        }
    }

    private companion object {
        const val KEY_SECRET = "secret"
        const val KEY_PAIRING_ID = "pairing_id"
        const val KEY_SERIAL = "profile_serial"
        const val KEY_NONCES = "nonces"
    }
}
