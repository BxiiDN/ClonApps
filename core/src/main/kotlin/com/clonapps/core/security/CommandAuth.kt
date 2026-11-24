package com.clonapps.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Otorisasi perintah lintas profil.
 *
 * Kunci rahasia dibuat di profil utama dan diteruskan ke ClonApps di dalam profil kerja
 * melalui EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE pada saat provisioning (jalur resmi sistem).
 * Aplikasi lain yang mengirim intent dengan action yang sama tidak memiliki kunci ini
 * sehingga perintah mereka ditolak.
 */
class CommandAuth(private val secret: ByteArray, private val clock: () -> Long) {

    init {
        require(secret.size >= 32) { "Kunci minimal 32 byte" }
    }

    data class Signed(val command: String, val packageName: String, val payload: String, val nonce: String, val timestamp: Long, val mac: String)

    fun sign(command: String, packageName: String, payload: String = ""): Signed {
        val nonce = randomHex(16)
        val ts = clock()
        return Signed(command, packageName, payload, nonce, ts, mac(command, packageName, payload, nonce, ts))
    }

    sealed class Verdict {
        object Ok : Verdict()
        data class Rejected(val reason: String) : Verdict()
    }

    fun verify(s: Signed, nonces: NonceStore): Verdict {
        val now = clock()
        if (kotlin.math.abs(now - s.timestamp) > WINDOW_MS) return Verdict.Rejected("stempel waktu di luar jendela")
        if (s.nonce.length !in 16..128) return Verdict.Rejected("nonce tidak valid")
        val expected = mac(s.command, s.packageName, s.payload, s.nonce, s.timestamp)
        if (!MessageDigest.isEqual(expected.toByteArray(), s.mac.toByteArray())) return Verdict.Rejected("tanda tangan tidak cocok")
        if (!nonces.markIfNew(s.nonce, s.timestamp, now)) return Verdict.Rejected("nonce dipakai ulang")
        return Verdict.Ok
    }

    private fun mac(command: String, pkg: String, payload: String, nonce: String, ts: Long): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(secret, "HmacSHA256"))
        val canonical = listOf("v1", command, pkg, sha256Hex(payload), nonce, ts.toString()).joinToString("\n")
        return m.doFinal(canonical.toByteArray(Charsets.UTF_8)).toHex()
    }

    companion object {
        const val WINDOW_MS = 2 * 60 * 1000L
        private val rng = SecureRandom()

        fun newSecret(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }
        fun randomHex(bytes: Int): String = ByteArray(bytes).also { rng.nextBytes(it) }.toHex()
        fun sha256Hex(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).toHex()
        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
        fun fromHex(hex: String): ByteArray? {
            if (hex.length % 2 != 0) return null
            return try { ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } } catch (e: NumberFormatException) { null }
        }
    }
}

/** Penyimpan nonce untuk mencegah replay. */
interface NonceStore {
    /** True bila nonce belum pernah dilihat (lalu dicatat), false bila replay. */
    fun markIfNew(nonce: String, timestamp: Long, now: Long): Boolean
}

class InMemoryNonceStore(private val capacity: Int = 512) : NonceStore {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    override fun markIfNew(nonce: String, timestamp: Long, now: Long): Boolean {
        val it = seen.entries.iterator()
        while (it.hasNext()) if (now - it.next().value > CommandAuth.WINDOW_MS * 2) it.remove()
        if (seen.containsKey(nonce)) return false
        seen[nonce] = timestamp
        while (seen.size > capacity) seen.remove(seen.keys.first())
        return true
    }

    @Synchronized
    fun snapshot(): Map<String, Long> = LinkedHashMap(seen)

    @Synchronized
    fun restore(entries: Map<String, Long>) { seen.clear(); seen.putAll(entries) }
}
