package com.clonapps.core.json

/**
 * JSON minimal tanpa dependensi untuk konfigurasi dan antrean lokal.
 * Parser membatasi ukuran dan kedalaman karena dipakai juga untuk berkas impor dari pengguna.
 */
sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()

    /** Angka disimpan sebagai teks agar Long tidak kehilangan presisi. */
    data class Num(val raw: String) : JsonValue() {
        fun toLong(): Long? = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.let { d -> if (d % 1.0 == 0.0) d.toLong() else null }
        fun toInt(): Int? = toLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    }
    data class Str(val value: String) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue? = fields[key]
        fun str(key: String): String? = (fields[key] as? Str)?.value
        fun long(key: String): Long? = (fields[key] as? Num)?.toLong()
        fun int(key: String): Int? = (fields[key] as? Num)?.toInt()
        fun bool(key: String): Boolean? = (fields[key] as? Bool)?.value
        fun obj(key: String): Obj? = fields[key] as? Obj
        fun arr(key: String): List<JsonValue> = (fields[key] as? Arr)?.items ?: emptyList()
    }
}

class JsonParseException(message: String) : IllegalArgumentException(message)

object Json {
    const val MAX_INPUT_CHARS = 2 * 1024 * 1024
    const val MAX_DEPTH = 32

    fun parse(text: String): JsonValue {
        if (text.length > MAX_INPUT_CHARS) throw JsonParseException("Berkas terlalu besar")
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != text.length) throw JsonParseException("Karakter berlebih pada posisi ${p.i}")
        return v
    }

    fun write(value: JsonValue, indent: Boolean = false): String = StringBuilder().also { write(value, it, indent, 0) }.toString()

    private fun write(v: JsonValue, sb: StringBuilder, indent: Boolean, level: Int) {
        when (v) {
            JsonValue.Null -> sb.append("null")
            is JsonValue.Bool -> sb.append(v.value)
            is JsonValue.Num -> sb.append(v.raw)
            is JsonValue.Str -> quote(v.value, sb)
            is JsonValue.Arr -> {
                sb.append('[')
                v.items.forEachIndexed { idx, item ->
                    if (idx > 0) sb.append(',')
                    nl(sb, indent, level + 1)
                    write(item, sb, indent, level + 1)
                }
                if (v.items.isNotEmpty()) nl(sb, indent, level)
                sb.append(']')
            }
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                for ((k, item) in v.fields) {
                    if (!first) sb.append(',')
                    first = false
                    nl(sb, indent, level + 1)
                    quote(k, sb)
                    sb.append(if (indent) ": " else ":")
                    write(item, sb, indent, level + 1)
                }
                if (v.fields.isNotEmpty()) nl(sb, indent, level)
                sb.append('}')
            }
        }
    }

    private fun nl(sb: StringBuilder, indent: Boolean, level: Int) {
        if (!indent) return
        sb.append('\n')
        repeat(level) { sb.append("  ") }
    }

    private fun quote(s: String, sb: StringBuilder) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) throw JsonParseException("Struktur terlalu dalam")
            if (i >= s.length) throw JsonParseException("Data berakhir tiba-tiba")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> JsonValue.Str(str())
                't' -> lit("true", JsonValue.Bool(true))
                'f' -> lit("false", JsonValue.Bool(false))
                'n' -> lit("null", JsonValue.Null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonParseException("Karakter tak terduga '$c' pada $i")
            }
        }

        private fun lit(word: String, v: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) throw JsonParseException("Literal tidak valid pada $i")
            i += word.length
            return v
        }

        private fun num(): JsonValue {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val raw = s.substring(start, i)
            if (raw.toDoubleOrNull() == null) throw JsonParseException("Angka tidak valid '$raw'")
            return JsonValue.Num(raw)
        }

        private fun str(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw JsonParseException("String tidak ditutup")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw JsonParseException("Escape tidak lengkap")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonParseException("Escape unicode tidak lengkap")
                                val hex = s.substring(i, i + 4)
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw JsonParseException("Escape unicode tidak valid"))
                                i += 4
                            }
                            else -> throw JsonParseException("Escape tidak dikenal '\\$e'")
                        }
                    }
                    c < ' ' -> throw JsonParseException("Karakter kontrol dalam string")
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(depth: Int): JsonValue {
            i++
            val items = mutableListOf<JsonValue>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return JsonValue.Arr(items) }
            while (true) {
                ws(); items += value(depth + 1); ws()
                if (i >= s.length) throw JsonParseException("Array tidak ditutup")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return JsonValue.Arr(items)
                    else -> throw JsonParseException("Diharapkan ',' atau ']' pada ${i - 1}")
                }
            }
        }

        private fun obj(depth: Int): JsonValue {
            i++
            val fields = LinkedHashMap<String, JsonValue>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return JsonValue.Obj(fields) }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw JsonParseException("Diharapkan nama field pada $i")
                val key = str()
                ws()
                if (i >= s.length || s[i++] != ':') throw JsonParseException("Diharapkan ':' pada ${i - 1}")
                ws()
                fields[key] = value(depth + 1)
                ws()
                if (i >= s.length) throw JsonParseException("Objek tidak ditutup")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return JsonValue.Obj(fields)
                    else -> throw JsonParseException("Diharapkan ',' atau '}' pada ${i - 1}")
                }
            }
        }
    }
}

/** Builder kecil agar kode codec ringkas. */
fun jObj(vararg pairs: Pair<String, JsonValue?>): JsonValue.Obj =
    JsonValue.Obj(LinkedHashMap<String, JsonValue>().apply { pairs.forEach { (k, v) -> if (v != null) put(k, v) } })

fun jStr(v: String?): JsonValue? = v?.let { JsonValue.Str(it) }
fun jNum(v: Long?): JsonValue? = v?.let { JsonValue.Num(it.toString()) }
fun jNum(v: Int?): JsonValue? = v?.let { JsonValue.Num(it.toString()) }
fun jBool(v: Boolean?): JsonValue? = v?.let { JsonValue.Bool(it) }
fun jArr(items: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(items)
