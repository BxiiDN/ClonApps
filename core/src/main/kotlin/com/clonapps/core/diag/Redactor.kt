package com.clonapps.core.diag

/**
 * Menghapus token, kredensial, email, nomor telepon, dan jalur data pribadi dari teks
 * diagnostik sebelum ditampilkan, disimpan, atau diekspor. Nama paket dipertahankan
 * karena dibutuhkan untuk diagnosis.
 */
object Redactor {
    private val rules: List<Pair<Regex, String>> = listOf(
        // Header otorisasi dan pasangan kunci=nilai sensitif
        Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}") to "$1 <redacted>",
        Regex("(?i)\\b(token|access_token|refresh_token|id_token|auth|authorization|password|passwd|pwd|secret|session|sessionid|cookie|apikey|api_key|key|mac|nonce)\\s*[=:]\\s*[^\\s,;&\"']+") to "$1=<redacted>",
        // JWT
        Regex("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b") to "<jwt>",
        // Email
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "<email>",
        // Jalur data per-pengguna & penyimpanan bersama
        Regex("/data/(user|user_de)/\\d+/[^\\s'\"]+") to "/data/$1/<n>/<path>",
        Regex("/storage/emulated/\\d+/[^\\s'\"]+") to "/storage/emulated/<n>/<path>",
        Regex("/sdcard/[^\\s'\"]+") to "/sdcard/<path>",
        // content:// URI dapat memuat nama file pribadi
        Regex("content://[^\\s'\"]+") to "content://<uri>",
        // IPv4
        Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b") to "<ip>",
        // Nomor telepon: berawalan + atau berkelompok dengan pemisah. Angka polos (versionCode) dibiarkan.
        Regex("(?<![\\w.])\\+\\d[\\d -]{7,}\\d(?![\\w.])") to "<number>",
        Regex("\\b\\d{3,4}[ -]\\d{3,4}[ -]\\d{3,5}\\b") to "<number>",
        // Hex/base64 panjang (token, hash, Android ID). Nama paket tidak cocok karena berisi titik.
        Regex("\\b(?=[0-9]*[A-Fa-f])[A-Fa-f0-9]{16,}\\b") to "<hex>",
        // Hanya bila bercampur digit dan huruf kecil, agar kode seperti INSTALL_FAILED_... tetap terbaca.
        Regex("\\b(?=[A-Za-z0-9+/_-]*\\d)(?=[A-Za-z0-9+/_-]*[a-z])[A-Za-z0-9+/_-]{32,}={0,2}") to "<blob>",
    )

    fun redact(input: String): String {
        var out = input
        for ((re, rep) in rules) out = re.replace(out, rep)
        return out
    }
}
