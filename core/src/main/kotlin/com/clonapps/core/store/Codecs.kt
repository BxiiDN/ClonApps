package com.clonapps.core.store

import com.clonapps.core.json.Json
import com.clonapps.core.json.JsonParseException
import com.clonapps.core.json.JsonValue
import com.clonapps.core.json.jArr
import com.clonapps.core.json.jBool
import com.clonapps.core.json.jNum
import com.clonapps.core.json.jObj
import com.clonapps.core.json.jStr
import com.clonapps.core.model.CloneRecord
import com.clonapps.core.model.Failure
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.InstallMethod
import com.clonapps.core.model.InstallState
import com.clonapps.core.model.LaunchState
import com.clonapps.core.model.RecoveryAction
import com.clonapps.core.queue.CloneJob
import com.clonapps.core.queue.InstallQueue
import com.clonapps.core.queue.JobKind
import com.clonapps.core.queue.JobPhase

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Kunci tampilan ClonApps saja. Tidak melindungi aplikasi di profil. */
    val dashboardLock: Boolean = false,
    val showSystemApps: Boolean = false,
)

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

object FailureCodec {
    fun encode(f: Failure): JsonValue.Obj = jObj(
        "stage" to jStr(f.stage.name),
        "cause" to jStr(f.cause.name),
        "code" to jStr(f.code),
        "detail" to jStr(f.detail),
        "recovery" to jStr(f.recovery.name),
        "retryable" to jBool(f.retryable),
        "at" to jNum(f.atMillis),
    )

    fun decode(o: JsonValue.Obj?): Failure? = o?.let {
        Failure(
            stage = enumOr(it.str("stage"), FailureStage.INSTALL),
            cause = enumOr(it.str("cause"), FailureCause.UNKNOWN),
            code = it.str("code") ?: "UNKNOWN",
            detail = it.str("detail"),
            recovery = enumOr(it.str("recovery"), RecoveryAction.NONE),
            retryable = it.bool("retryable") ?: false,
            atMillis = it.long("at") ?: 0L,
        )
    }
}

object QueueCodec {
    const val SCHEMA = 1

    fun encode(q: InstallQueue): String = Json.write(
        jObj(
            "schema" to jNum(SCHEMA),
            "jobs" to jArr(q.jobs.map { encodeJob(it) }),
        ),
    )

    fun encodeJob(j: CloneJob): JsonValue.Obj = jObj(
        "id" to jStr(j.id),
        "kind" to jStr(j.kind.name),
        "package" to jStr(j.packageName),
        "label" to jStr(j.label),
        "profileSerial" to jNum(j.profileSerial),
        "phase" to jStr(j.phase.name),
        "attempts" to jNum(j.attempts),
        "maxAttempts" to jNum(j.maxAttempts),
        "requestId" to jStr(j.requestId),
        "sessionId" to jNum(j.sessionId),
        "expectedVersionCode" to jNum(j.expectedVersionCode),
        "createdAt" to jNum(j.createdAt),
        "updatedAt" to jNum(j.updatedAt),
        "failure" to j.failure?.let { FailureCodec.encode(it) },
        "note" to jStr(j.note),
    )

    /** Data rusak tidak boleh membuat aplikasi crash: job yang tidak valid dilewati. */
    fun decode(text: String): InstallQueue {
        val root = try { Json.parse(text) as? JsonValue.Obj } catch (e: JsonParseException) { null } ?: return InstallQueue()
        val jobs = root.arr("jobs").mapNotNull { v ->
            val o = v as? JsonValue.Obj ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val pkg = o.str("package")?.takeIf { PackageNames.isValid(it) } ?: return@mapNotNull null
            CloneJob(
                id = id,
                kind = enumOrNull<JobKind>(o.str("kind")) ?: return@mapNotNull null,
                packageName = pkg,
                label = o.str("label") ?: pkg,
                profileSerial = o.long("profileSerial") ?: return@mapNotNull null,
                phase = enumOrNull<JobPhase>(o.str("phase")) ?: return@mapNotNull null,
                attempts = o.int("attempts") ?: 0,
                maxAttempts = o.int("maxAttempts") ?: CloneJob.DEFAULT_MAX_ATTEMPTS,
                requestId = o.str("requestId"),
                sessionId = o.int("sessionId"),
                expectedVersionCode = o.long("expectedVersionCode"),
                createdAt = o.long("createdAt") ?: 0L,
                updatedAt = o.long("updatedAt") ?: 0L,
                failure = FailureCodec.decode(o.obj("failure")),
                note = o.str("note"),
            )
        }
        return InstallQueue(jobs)
    }
}

object RecordCodec {
    fun encode(records: Collection<CloneRecord>): String = Json.write(
        jObj("schema" to jNum(1), "records" to jArr(records.map { encodeOne(it) })),
    )

    private fun encodeOne(r: CloneRecord) = jObj(
        "package" to jStr(r.packageName),
        "profileSerial" to jNum(r.profileSerial),
        "originalLabel" to jStr(r.originalLabel),
        "customLabel" to jStr(r.customLabel),
        "favorite" to jBool(r.favorite),
        "installState" to jStr(r.installState.name),
        "installMethod" to jStr(r.installMethod?.name),
        "installedVersionCode" to jNum(r.installedVersionCode),
        "lastInstallAt" to jNum(r.lastInstallAt),
        "launchState" to jStr(r.launchState.name),
        "lastLaunchAt" to jNum(r.lastLaunchAt),
        "lastFailure" to r.lastFailure?.let { FailureCodec.encode(it) },
        "updatedAt" to jNum(r.updatedAt),
    )

    fun decode(text: String): List<CloneRecord> {
        val root = try { Json.parse(text) as? JsonValue.Obj } catch (e: JsonParseException) { null } ?: return emptyList()
        return root.arr("records").mapNotNull { v ->
            val o = v as? JsonValue.Obj ?: return@mapNotNull null
            val pkg = o.str("package")?.takeIf { PackageNames.isValid(it) } ?: return@mapNotNull null
            CloneRecord(
                packageName = pkg,
                profileSerial = o.long("profileSerial") ?: return@mapNotNull null,
                originalLabel = o.str("originalLabel") ?: pkg,
                customLabel = o.str("customLabel"),
                favorite = o.bool("favorite") ?: false,
                installState = enumOr(o.str("installState"), InstallState.NONE),
                installMethod = enumOrNull<InstallMethod>(o.str("installMethod")),
                installedVersionCode = o.long("installedVersionCode"),
                lastInstallAt = o.long("lastInstallAt"),
                launchState = enumOr(o.str("launchState"), LaunchState.NONE),
                lastLaunchAt = o.long("lastLaunchAt"),
                lastFailure = FailureCodec.decode(o.obj("lastFailure")),
                updatedAt = o.long("updatedAt") ?: 0L,
            )
        }
    }
}

object PackageNames {
    private val re = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    fun isValid(name: String): Boolean = name.length in 3..255 && re.matches(name)
}

/** Pengaturan yang dapat diekspor. Sengaja TIDAK memuat kunci pemasangan, antrean, atau data aplikasi lain. */
data class SettingsExport(
    val settings: AppSettings,
    val clones: List<CloneLabelEntry>,
)

data class CloneLabelEntry(val packageName: String, val customLabel: String?, val favorite: Boolean)

sealed class ImportResult {
    data class Ok(val export: SettingsExport, val skipped: Int) : ImportResult()
    data class Invalid(val reason: String) : ImportResult()
}

object SettingsCodec {
    const val FORMAT = "clonapps-settings"
    const val SCHEMA = 1
    const val MAX_LABEL = 50
    const val MAX_ENTRIES = 2000

    fun export(e: SettingsExport, exportedAt: Long, appVersion: String): String = Json.write(
        jObj(
            "format" to jStr(FORMAT),
            "schema" to jNum(SCHEMA),
            "exportedAt" to jNum(exportedAt),
            "appVersion" to jStr(appVersion),
            "note" to jStr("Hanya pengaturan ClonApps. Tidak berisi akun, token, atau data aplikasi clone."),
            "settings" to jObj(
                "themeMode" to jStr(e.settings.themeMode.name),
                "dashboardLock" to jBool(e.settings.dashboardLock),
                "showSystemApps" to jBool(e.settings.showSystemApps),
            ),
            "clones" to jArr(
                e.clones.map {
                    jObj("package" to jStr(it.packageName), "label" to jStr(it.customLabel), "favorite" to jBool(it.favorite))
                },
            ),
        ),
        indent = true,
    )

    fun import(text: String): ImportResult {
        val root = try {
            Json.parse(text) as? JsonValue.Obj
        } catch (e: JsonParseException) {
            return ImportResult.Invalid("JSON tidak valid: ${e.message}")
        } ?: return ImportResult.Invalid("Akar berkas bukan objek")
        if (root.str("format") != FORMAT) return ImportResult.Invalid("Bukan berkas pengaturan ClonApps")
        val schema = root.int("schema") ?: return ImportResult.Invalid("Versi skema tidak ada")
        if (schema > SCHEMA) return ImportResult.Invalid("Skema $schema lebih baru dari yang didukung ($SCHEMA)")
        val s = root.obj("settings")
        val settings = AppSettings(
            themeMode = enumOr(s?.str("themeMode"), ThemeMode.SYSTEM),
            dashboardLock = s?.bool("dashboardLock") ?: false,
            showSystemApps = s?.bool("showSystemApps") ?: false,
        )
        var skipped = 0
        val entries = root.arr("clones").take(MAX_ENTRIES).mapNotNull { v ->
            val o = v as? JsonValue.Obj
            val pkg = o?.str("package")
            if (o == null || pkg == null || !PackageNames.isValid(pkg)) { skipped++; return@mapNotNull null }
            val label = o.str("label")?.trim()?.take(MAX_LABEL)?.filter { it >= ' ' }?.takeIf { it.isNotEmpty() }
            CloneLabelEntry(pkg, label, o.bool("favorite") ?: false)
        }
        return ImportResult.Ok(SettingsExport(settings, entries.distinctBy { it.packageName }), skipped)
    }
}
