package com.clonapps.data

import android.content.Context
import android.util.AtomicFile
import com.clonapps.core.json.Json
import com.clonapps.core.json.JsonParseException
import com.clonapps.core.json.JsonValue
import com.clonapps.core.json.jArr
import com.clonapps.core.json.jNum
import com.clonapps.core.json.jObj
import com.clonapps.core.json.jStr
import com.clonapps.core.model.CloneRecord
import com.clonapps.core.queue.InstallQueue
import com.clonapps.core.store.AppSettings
import com.clonapps.core.store.QueueCodec
import com.clonapps.core.store.RecordCodec
import com.clonapps.core.store.ThemeMode
import java.io.File

/** Berkas JSON yang ditulis atomik (tulis ke berkas sementara lalu ganti), aman terhadap crash. */
class JsonFile(dir: File, name: String) {
    private val file = AtomicFile(File(dir, name))

    @Synchronized
    fun read(): String? = try {
        String(file.readFully(), Charsets.UTF_8)
    } catch (e: java.io.FileNotFoundException) {
        null
    }

    @Synchronized
    fun write(text: String) {
        val out = file.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}

enum class LogLevel { INFO, WARN, ERROR }

/** Kode peristiwa; teks tampilannya ada di resource string agar dapat dilokalkan. */
enum class LogCode {
    PROVISION_STARTED, PROVISION_OK, PROVISION_FAILED, PROFILE_VERIFIED, PAIRED, PAIR_FAILED,
    ENQUEUED, JOB_STARTED, JOB_SUCCEEDED, JOB_FAILED, JOB_CANCELLED, JOB_RECONCILED, JOB_RETRY,
    LAUNCH_REQUESTED, LAUNCH_FAILED, USER_CONFIRMED_RUNNING, USER_REPORTED_PROBLEM,
    CLONE_REMOVED, SHORTCUT_REQUESTED, SETTINGS_EXPORTED, SETTINGS_IMPORTED,
    PROFILE_PAUSE_REQUESTED, PROFILE_PAUSE_DENIED, PROFILE_LOCK_REQUESTED, PROFILE_REMOVE_REQUESTED,
    ORPHAN_RESULT, INFO_REFRESHED,
}

data class LogEntry(val at: Long, val level: LogLevel, val code: LogCode, val packageName: String?, val detail: String?)

class Stores(context: Context) {
    private val dir = File(context.noBackupFilesDir, "state").apply { mkdirs() }
    private val queueFile = JsonFile(dir, "queue.json")
    private val recordsFile = JsonFile(dir, "records.json")
    private val logFile = JsonFile(dir, "activity_log.json")
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadQueue(): InstallQueue = queueFile.read()?.let { QueueCodec.decode(it) } ?: InstallQueue()
    fun saveQueue(q: InstallQueue) = queueFile.write(QueueCodec.encode(q))

    fun loadRecords(): List<CloneRecord> = recordsFile.read()?.let { RecordCodec.decode(it) } ?: emptyList()
    fun saveRecords(r: Collection<CloneRecord>) = recordsFile.write(RecordCodec.encode(r))

    fun loadSettings(): AppSettings = AppSettings(
        themeMode = ThemeMode.values().firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.SYSTEM,
        dashboardLock = prefs.getBoolean("dashboard_lock", false),
        showSystemApps = prefs.getBoolean("show_system", false),
    )

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.themeMode.name)
            .putBoolean("dashboard_lock", s.dashboardLock)
            .putBoolean("show_system", s.showSystemApps)
            .apply()
    }

    fun loadLog(): List<LogEntry> {
        val root = try { logFile.read()?.let { Json.parse(it) as? JsonValue.Obj } } catch (e: JsonParseException) { null } ?: return emptyList()
        return root.arr("entries").mapNotNull { v ->
            val o = v as? JsonValue.Obj ?: return@mapNotNull null
            LogEntry(
                at = o.long("at") ?: return@mapNotNull null,
                level = LogLevel.values().firstOrNull { it.name == o.str("level") } ?: LogLevel.INFO,
                code = LogCode.values().firstOrNull { it.name == o.str("code") } ?: return@mapNotNull null,
                packageName = o.str("package"),
                detail = o.str("detail"),
            )
        }
    }

    fun saveLog(entries: List<LogEntry>) = logFile.write(
        Json.write(
            jObj(
                "schema" to jNum(1),
                "entries" to jArr(
                    entries.map {
                        jObj("at" to jNum(it.at), "level" to jStr(it.level.name), "code" to jStr(it.code.name), "package" to jStr(it.packageName), "detail" to jStr(it.detail))
                    },
                ),
            ),
        ),
    )

    /** Hasil HELLO terakhir: apakah profil dengan nomor seri tertentu dikelola ClonApps. */
    fun saveHello(serial: Long, profileOwner: Boolean, paired: Boolean) {
        prefs.edit().putLong("hello_serial", serial).putBoolean("hello_owner", profileOwner).putBoolean("hello_paired", paired).apply()
    }

    fun hello(serial: Long): Pair<Boolean, Boolean>? =
        if (prefs.getLong("hello_serial", -1L) == serial) prefs.getBoolean("hello_owner", false) to prefs.getBoolean("hello_paired", false) else null

    fun clearHello() = prefs.edit().remove("hello_serial").remove("hello_owner").remove("hello_paired").apply()
}
