package com.clonapps.domain

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.content.ContextCompat
import com.clonapps.R
import com.clonapps.core.json.Json
import com.clonapps.core.json.JsonValue
import com.clonapps.core.json.jObj
import com.clonapps.core.json.jStr
import com.clonapps.core.model.CloneRecord
import com.clonapps.core.model.CloneStatus
import com.clonapps.core.model.Failure
import com.clonapps.core.model.FailureCause
import com.clonapps.core.model.FailureStage
import com.clonapps.core.model.InstallMethod
import com.clonapps.core.model.InstallState
import com.clonapps.core.model.LaunchState
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.core.model.RecoveryAction
import com.clonapps.core.model.StatusResolver
import com.clonapps.core.queue.CloneJob
import com.clonapps.core.queue.EnqueueRequest
import com.clonapps.core.queue.InstallQueue
import com.clonapps.core.queue.JobKind
import com.clonapps.core.queue.JobPhase
import com.clonapps.core.queue.Observation
import com.clonapps.core.queue.RetryDecision
import com.clonapps.core.security.CommandAuth
import com.clonapps.core.store.AppSettings
import com.clonapps.core.store.CloneLabelEntry
import com.clonapps.core.store.ImportResult
import com.clonapps.core.store.SettingsCodec
import com.clonapps.core.store.SettingsExport
import com.clonapps.core.validate.InstallCapability
import com.clonapps.core.validate.Precheck
import com.clonapps.core.validate.PrecheckInput
import com.clonapps.core.validate.PrecheckResult
import com.clonapps.core.validate.ProfileView
import com.clonapps.data.LogCode
import com.clonapps.data.LogEntry
import com.clonapps.data.LogLevel
import com.clonapps.data.PairingStore
import com.clonapps.data.Stores
import com.clonapps.platform.AppDiscovery
import com.clonapps.platform.DeviceProbe
import com.clonapps.platform.LaunchOutcome
import com.clonapps.platform.PauseResult
import com.clonapps.platform.Presence
import com.clonapps.platform.ProfileEnvironment
import com.clonapps.platform.ProfileLauncher
import com.clonapps.platform.ProfileState
import com.clonapps.platform.ShortcutResult
import com.clonapps.platform.Shortcuts
import com.clonapps.profile.ProfileCommandActivity
import com.clonapps.profile.ProfileProtocol
import com.clonapps.profile.ProfileProtocol.Command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Pesan singkat untuk snackbar. */
data class UiMessage(val res: Int, val args: List<Any> = emptyList())

/** Hasil INFO dari profil kerja. */
data class ProfileInfoReport(
    val fetchedAt: Long,
    val canRequestPackageInstalls: Boolean?,
    val unknownSourcesRestricted: Boolean?,
    val restrictions: List<String>,
    val integration: Map<String, String>,
    val packages: Map<String, Long>,
)

/**
 * Pusat logika ClonApps di profil utama: antrean persisten, kanal perintah lintas profil,
 * pencocokan ulang setelah proses terputus, dan semua aksi pengelolaan clone.
 */
class CloneCoordinator(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val pairing = PairingStore(app)
    private val stores = Stores(app)
    val env = ProfileEnvironment(app, pairing, stores)
    val discovery = AppDiscovery(app)
    val launcher = ProfileLauncher(app)
    val channel = CommandChannel()
    private val stateLock = Mutex()
    private val runnerActive = AtomicBoolean(false)
    private val cancelRequested = mutableSetOf<String>()

    private val _profile = MutableStateFlow(env.read())
    val profile: StateFlow<ProfileState> = _profile.asStateFlow()
    private val _queue = MutableStateFlow(stores.loadQueue())
    val queue: StateFlow<InstallQueue> = _queue.asStateFlow()
    private val _records = MutableStateFlow(stores.loadRecords().associateBy { it.packageName })
    val records: StateFlow<Map<String, CloneRecord>> = _records.asStateFlow()
    private val _settings = MutableStateFlow(stores.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private val _log = MutableStateFlow(stores.loadLog())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()
    private val _info = MutableStateFlow<ProfileInfoReport?>(null)
    val profileInfo: StateFlow<ProfileInfoReport?> = _info.asStateFlow()
    private val _prechecks = MutableStateFlow<Map<String, PrecheckResult>>(emptyMap())
    val prechecks: StateFlow<Map<String, PrecheckResult>> = _prechecks.asStateFlow()
    private val _presence = MutableStateFlow<Set<String>>(emptySet())
    val presence: StateFlow<Set<String>> = _presence.asStateFlow()
    private val _provisioningFailure = MutableStateFlow<Failure?>(null)
    val provisioningFailure: StateFlow<Failure?> = _provisioningFailure.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val now: Long get() = System.currentTimeMillis()

    init {
        channel.orphanListener = { result ->
            log(LogLevel.WARN, LogCode.ORPHAN_RESULT, null, result?.code?.name)
            scope.launch { reconcile() }
        }
        registerProfileListeners()
        scope.launch { reconcile() }
    }

    // ---------------------------------------------------------------- profil

    fun refreshProfile() {
        _profile.value = env.read()
        refreshPresence()
    }

    private fun refreshPresence() {
        val handle = _profile.value.profile?.handle ?: run { _presence.value = emptySet(); return }
        val launchable = launcher.packagesInProfile(handle)
        val fromRecords = _records.value.keys.filter { launcher.presence(it, handle) is Presence.Present }
        _presence.value = launchable + fromRecords + (_info.value?.packages?.keys ?: emptySet())
    }

    fun provisioningBlocker(): Failure? {
        val p = _profile.value
        return when {
            !p.managedUsersFeature -> Failures.simple(FailureStage.PROVISIONING, FailureCause.MANAGED_USERS_FEATURE_MISSING, "FEATURE_MANAGED_USERS=false", now, retry = false)
            p.ownership == ProfileOwnership.FOREIGN -> Failures.simple(FailureStage.PROVISIONING, FailureCause.FOREIGN_PROFILE_EXISTS, "FOREIGN_PROFILE", now, retry = false)
            p.ownership == ProfileOwnership.OURS_PAIRED || p.ownership == ProfileOwnership.OURS_UNPAIRED -> null
            !p.provisioningAllowed -> Failures.simple(FailureStage.PROVISIONING, FailureCause.PROVISIONING_NOT_ALLOWED, "isProvisioningAllowed=false", now, retry = false)
            !p.provisioningUiPresent -> Failures.simple(FailureStage.PROVISIONING, FailureCause.PROVISIONING_UI_MISSING, "ACTION_PROVISION_MANAGED_PROFILE tidak terselesaikan", now, retry = false)
            else -> null
        }
    }

    /** Memulai layar provisioning Android. Persetujuan pengguna dilakukan sepenuhnya oleh sistem. */
    fun startProvisioning(host: CommandHost) {
        refreshProfile()
        provisioningBlocker()?.let { _provisioningFailure.value = it; log(LogLevel.ERROR, LogCode.PROVISION_FAILED, null, it.cause.name); return }
        val (secret, id) = pairing.newPendingSecret()
        log(LogLevel.INFO, LogCode.PROVISION_STARTED, null, null)
        try {
            host.launchProvisioning(ProfileEnvironment.provisioningIntent(app, ProfileEnvironment.adminExtras(secret, id)))
        } catch (e: RuntimeException) {
            val f = Failures.simple(FailureStage.PROVISIONING, FailureCause.PROVISIONING_UI_MISSING, e.javaClass.simpleName, now, e.message, retry = false)
            _provisioningFailure.value = f
            log(LogLevel.ERROR, LogCode.PROVISION_FAILED, null, f.code)
        }
    }

    fun onProvisioningResult(resultCode: Int) {
        refreshProfile()
        val p = _profile.value
        if (resultCode == Activity.RESULT_OK && p.profile?.hasClonApps == true) {
            pairing.bindSerial(p.profile.serial)
            stores.saveHello(p.profile.serial, profileOwner = true, paired = true)
            _provisioningFailure.value = null
            refreshProfile()
            log(LogLevel.INFO, LogCode.PROVISION_OK, null, p.profile.label)
            message(R.string.msg_profile_created)
        } else if (resultCode == Activity.RESULT_OK) {
            // Sistem melaporkan OK tetapi profil belum terlihat (beberapa ROM menyelesaikan secara asinkron).
            val f = Failures.simple(FailureStage.PROVISIONING, FailureCause.PROVISIONING_FAILED, "RESULT_OK_NO_PROFILE", now,
                "Android melaporkan berhasil, tetapi profil dengan ClonApps belum terlihat. Buka ulang ClonApps beberapa saat lagi.")
            _provisioningFailure.value = f
            log(LogLevel.WARN, LogCode.PROVISION_FAILED, null, f.code)
        } else {
            val f = com.clonapps.core.error.ErrorMapper.fromProvisioningResult(false, now)!!
            _provisioningFailure.value = f
            log(LogLevel.WARN, LogCode.PROVISION_FAILED, null, f.code)
        }
    }

    /** HELLO: memastikan profil kandidat memang dikelola ClonApps. */
    fun verifyProfile() = scope.launch {
        val p = _profile.value.profile ?: return@launch refreshProfile()
        busy {
            when (val o = channel.send(Command.HELLO, "-", "", pairing.auth())) {
                is CommandOutcome.Replied -> {
                    val owner = o.result.info?.bool("profileOwner") == true
                    val paired = o.result.info?.bool("paired") == true
                    val sameId = o.result.info?.str("pairingId") != null && o.result.info?.str("pairingId") == pairing.pairingId
                    stores.saveHello(p.serial, owner, paired)
                    if (owner && paired && sameId && pairing.hasSecret()) pairing.bindSerial(p.serial)
                    log(LogLevel.INFO, LogCode.PROFILE_VERIFIED, null, "owner=$owner paired=$paired sameKey=$sameId")
                }
                is CommandOutcome.NoHandler -> {
                    stores.saveHello(p.serial, profileOwner = false, paired = false)
                    log(LogLevel.WARN, LogCode.PROFILE_VERIFIED, null, "no handler: ${o.message}")
                }
                else -> message(R.string.msg_profile_no_answer)
            }
            refreshProfile()
        }
    }

    /** Pemasangan ulang kunci. Kunci baru baru disimpan setelah pengguna menyetujui di profil kerja. */
    fun repairPairing() = scope.launch {
        val p = _profile.value.profile ?: return@launch
        busy {
            val secret = CommandAuth.run { CommandAuth.newSecret().toHex() }
            val id = CommandAuth.randomHex(8)
            val payload = Json.write(jObj("secret" to jStr(secret), "pairingId" to jStr(id)))
            when (val o = channel.send(Command.PAIR, "-", payload, null)) {
                is CommandOutcome.Replied -> if (o.result.ok) {
                    pairing.save(secret, id)
                    pairing.bindSerial(p.serial)
                    stores.saveHello(p.serial, profileOwner = true, paired = true)
                    log(LogLevel.INFO, LogCode.PAIRED, null, null)
                    message(R.string.msg_paired)
                } else {
                    log(LogLevel.WARN, LogCode.PAIR_FAILED, null, o.result.code.name)
                    message(R.string.msg_pair_failed, listOf(o.result.code.name))
                }
                is CommandOutcome.NoHandler -> {
                    log(LogLevel.WARN, LogCode.PAIR_FAILED, null, "NO_HANDLER")
                    message(R.string.msg_pair_no_handler)
                }
                else -> message(R.string.msg_profile_no_answer)
            }
            refreshProfile()
        }
    }

    fun fetchProfileInfo() = scope.launch { busy { fetchInfoInternal() } }

    private suspend fun fetchInfoInternal(): Boolean {
        if (_profile.value.ownership != ProfileOwnership.OURS_PAIRED) return false
        val o = channel.send(Command.INFO, "-", "", pairing.auth() ?: return false)
        if (o !is CommandOutcome.Replied || !o.result.ok) {
            if (o is CommandOutcome.Replied) handleAuthProblems(o.result)
            return false
        }
        val i = o.result.info ?: return false
        val integration = i.obj("integration")?.fields?.mapValues { (_, v) ->
            when (v) { is JsonValue.Bool -> v.value.toString(); is JsonValue.Str -> v.value; else -> "?" }
        } ?: emptyMap()
        val pkgs = i.arr("packages").mapNotNull { v ->
            val obj = v as? JsonValue.Obj ?: return@mapNotNull null
            val n = obj.str("package") ?: return@mapNotNull null
            n to (obj.long("versionCode") ?: 0L)
        }.toMap()
        _info.value = ProfileInfoReport(
            fetchedAt = now,
            canRequestPackageInstalls = i.bool("canRequestPackageInstalls"),
            unknownSourcesRestricted = i.bool("unknownSourcesRestricted"),
            restrictions = i.arr("restrictions").mapNotNull { (it as? JsonValue.Str)?.value },
            integration = integration,
            packages = pkgs,
        )
        log(LogLevel.INFO, LogCode.INFO_REFRESHED, null, "packages=${pkgs.size}")
        refreshPresence()
        return true
    }

    private fun handleAuthProblems(r: ProfileProtocol.Result) {
        if (r.code == ProfileProtocol.ResultCode.AUTH_FAILED || r.code == ProfileProtocol.ResultCode.NOT_PAIRED) {
            _profile.value.profile?.let { stores.saveHello(it.serial, profileOwner = true, paired = false) }
            pairing.clear()
            refreshProfile()
        }
    }

    fun pauseProfile(pause: Boolean) {
        val h = _profile.value.profile?.handle ?: return
        when (val r = env.requestPause(h, pause)) {
            PauseResult.Requested -> { log(LogLevel.INFO, LogCode.PROFILE_PAUSE_REQUESTED, null, "pause=$pause"); message(R.string.msg_pause_requested) }
            is PauseResult.NotPermitted -> { log(LogLevel.WARN, LogCode.PROFILE_PAUSE_DENIED, null, r.message); message(R.string.msg_pause_not_permitted) }
            is PauseResult.Error -> { log(LogLevel.ERROR, LogCode.PROFILE_PAUSE_DENIED, null, r.message); message(R.string.msg_pause_not_permitted) }
        }
        Handler(Looper.getMainLooper()).postDelayed({ refreshProfile() }, 1500)
    }

    fun openClonAppsInProfile(): Boolean = _profile.value.profile?.handle?.let { env.openClonAppsInProfile(it) } ?: false

    fun requestProfileLock() = scope.launch {
        busy {
            val auth = pairing.auth() ?: return@busy message(R.string.msg_not_paired)
            val o = channel.send(Command.SET_PROFILE_LOCK, "-", "", auth)
            log(LogLevel.INFO, LogCode.PROFILE_LOCK_REQUESTED, null, (o as? CommandOutcome.Replied)?.result?.code?.name ?: o.javaClass.simpleName)
        }
    }

    /** Menghapus SELURUH profil ClonApps. UI wajib menampilkan konfirmasi terpisah lebih dulu. */
    fun removeWholeProfile() = scope.launch {
        busy {
            val auth = pairing.auth() ?: return@busy message(R.string.msg_not_paired)
            log(LogLevel.WARN, LogCode.PROFILE_REMOVE_REQUESTED, null, null)
            val o = channel.send(Command.WIPE_PROFILE, "-", ProfileCommandActivity.WIPE_CONFIRMATION, auth)
            if (o is CommandOutcome.Replied && o.result.ok) {
                pairing.clear()
                stores.clearHello()
                _records.value.values.forEach { r -> upsertRecord(r.copy(installState = InstallState.REMOVED, launchState = LaunchState.NONE, updatedAt = now)) }
                _info.value = null
            }
            Handler(Looper.getMainLooper()).postDelayed({ refreshProfile() }, 2500)
        }
    }

    // ---------------------------------------------------------------- pemeriksaan

    fun precheck(packages: List<String>) = scope.launch {
        busy {
            val info = _info.value
            if (_profile.value.usable && (info == null || now - info.fetchedAt > 60_000)) fetchInfoInternal()
            val results = withContext(Dispatchers.IO) {
                packages.mapNotNull { pkg -> discovery.snapshot(pkg)?.let { pkg to evaluate(it) } }
            }
            _prechecks.update { it + results }
        }
    }

    private fun evaluate(s: com.clonapps.core.validate.PackageSnapshot): PrecheckResult {
        val p = _profile.value
        val info = _info.value
        val present = s.packageName in _presence.value
        val profileVersion = info?.packages?.get(s.packageName)
            ?: if (present) (_records.value[s.packageName]?.installedVersionCode ?: 0L) else null
        return Precheck.evaluate(
            PrecheckInput(
                selfPackageName = app.packageName,
                snapshot = s,
                deviceAbis = android.os.Build.SUPPORTED_ABIS.toList(),
                profile = ProfileView(p.ownership, p.profile?.quietMode == true, p.profile?.unlocked != false),
                capability = InstallCapability(info?.unknownSourcesRestricted, info?.canRequestPackageInstalls),
                freeBytes = DeviceProbe.dataFreeBytes().takeIf { it >= 0 },
                profileVersionCode = profileVersion,
                presenceKnown = p.profile != null && !p.profile.quietMode,
                systemAppAvailableInProfile = null,
            ),
        )
    }

    fun statusOf(pkg: String): CloneStatus {
        val q = _queue.value
        val active = q.jobs.filter { it.packageName == pkg }.maxByOrNull { it.updatedAt }
            ?.takeIf { it.phase != JobPhase.SUCCEEDED && it.phase != JobPhase.CANCELLED && !(it.phase == JobPhase.FAILED && pkg in _presence.value) }
        val presentKnown = _profile.value.profile != null
        return StatusResolver.resolve(_records.value[pkg], active, if (presentKnown) pkg in _presence.value else null, _prechecks.value[pkg])
    }

    // ---------------------------------------------------------------- antrean

    fun enqueueInstall(packages: List<String>) = scope.launch {
        val serial = _profile.value.profile?.serial ?: return@launch message(R.string.msg_no_profile)
        val reqs = packages.mapNotNull { pkg ->
            val e = discovery.entry(pkg) ?: return@mapNotNull null
            EnqueueRequest(JobKind.INSTALL, pkg, e.label, serial, e.versionCode)
        }
        val result = mutateQueue { q -> q.enqueue(reqs, now) { UUID.randomUUID().toString() } }
        result.added.forEach { j ->
            log(LogLevel.INFO, LogCode.ENQUEUED, j.packageName, null)
            if (_records.value[j.packageName] == null) {
                upsertRecord(CloneRecord(j.packageName, serial, j.label, updatedAt = now))
            }
        }
        if (result.skippedDuplicates.isNotEmpty()) message(R.string.msg_skipped_duplicates, listOf(result.skippedDuplicates.size))
        kick()
    }

    fun removeClone(pkg: String) = scope.launch {
        val serial = _profile.value.profile?.serial ?: return@launch message(R.string.msg_no_profile)
        val label = _records.value[pkg]?.displayLabel ?: pkg
        val r = mutateQueue { q -> q.enqueue(listOf(EnqueueRequest(JobKind.UNINSTALL, pkg, label, serial)), now) { UUID.randomUUID().toString() } }
        if (r.added.isEmpty()) message(R.string.msg_skipped_duplicates, listOf(1))
        kick()
    }

    fun cancel(jobId: String) = scope.launch {
        val job = _queue.value.job(jobId) ?: return@launch
        if (job.phase.isRunning && job.requestId != null || job.phase == JobPhase.INSTALLING || job.phase == JobPhase.AWAITING_USER) {
            // Perintah sedang ditangani profil kerja; tidak dapat dihentikan dari sini tanpa merusak session.
            synchronized(cancelRequested) { cancelRequested += jobId }
            message(R.string.msg_cancel_on_system_screen)
            return@launch
        }
        mutateQueue { it.cancel(jobId, now) to Unit }
        log(LogLevel.INFO, LogCode.JOB_CANCELLED, job.packageName, null)
    }

    fun retry(jobId: String) = scope.launch {
        val decision = mutateQueue { it.retry(jobId, now) }
        when (decision) {
            RetryDecision.Allowed -> { log(LogLevel.INFO, LogCode.JOB_RETRY, _queue.value.job(jobId)?.packageName, null); kick() }
            is RetryDecision.Rejected -> message(R.string.msg_retry_rejected, listOf(decision.reason))
        }
    }

    fun resumeQueue() = kick()

    /** Dipanggil saat layar ClonApps tampil: job yang terputus dicocokkan ulang lalu dilanjutkan. */
    fun onHostAttached() {
        if (_queue.value.jobs.any { it.phase == JobPhase.INTERRUPTED }) scope.launch { reconcile() } else kick()
    }

    private fun kick() {
        if (!runnerActive.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val job = _queue.value.next() ?: break
                    refreshProfile()
                    if (!_profile.value.usable) break
                    when (job.kind) {
                        JobKind.INSTALL -> runInstall(job)
                        JobKind.UNINSTALL -> runUninstall(job)
                    }
                    // Berhenti bila job masih berjalan atau terputus (tidak ada layar host): lanjut saat host kembali.
                    if (_queue.value.running != null || _queue.value.job(job.id)?.phase == JobPhase.INTERRUPTED) break
                }
            } finally {
                runnerActive.set(false)
            }
        }
    }

    private suspend fun runInstall(job: CloneJob) {
        mutateQueue { it.transition(job.id, JobPhase.CHECKING, now) to Unit }
        log(LogLevel.INFO, LogCode.JOB_STARTED, job.packageName, "attempt=${job.attempts + 1}")
        val snap = withContext(Dispatchers.IO) { discovery.snapshot(job.packageName) }
            ?: return failJob(job, Failures.simple(FailureStage.PRECHECK, FailureCause.BASE_APK_MISSING, "ORIGINAL_NOT_INSTALLED", now, retry = false))
        if (_profile.value.usable && (_info.value == null || now - (_info.value?.fetchedAt ?: 0) > 60_000)) fetchInfoInternal()
        val pre = evaluate(snap)
        _prechecks.update { it + (job.packageName to pre) }
        when (pre.overall) {
            CloneStatus.INSTALLED -> {
                mutateQueue { it.transition(job.id, JobPhase.VERIFYING, now).transition(job.id, JobPhase.SUCCEEDED, now) to Unit }
                markInstalled(job.packageName, InstallMethod.ALREADY_PRESENT, _info.value?.packages?.get(job.packageName) ?: snap.versionCode)
                return
            }
            CloneStatus.READY -> Unit
            else -> return failJob(job, Failures.fromPrecheck(pre, now))
        }
        val payload = ProfileProtocol.InstallPayload(
            basePath = snap.base?.path ?: return failJob(job, Failures.simple(FailureStage.PACKAGE_INCOMPLETE, FailureCause.BASE_APK_MISSING, "NO_BASE", now)),
            splitPaths = snap.splitFiles.map { it.path },
            versionCode = snap.versionCode,
            isSystemApp = snap.isSystemApp,
            totalBytes = snap.totalApkBytes,
        )
        val auth = pairing.auth() ?: return failJob(job, Failures.simple(FailureStage.PROFILE, FailureCause.PROFILE_NOT_PAIRED, "NO_SECRET", now))
        mutateQueue {
            var q = it.transition(job.id, JobPhase.INSTALLING, now) { j -> j.copy(expectedVersionCode = snap.versionCode, requestId = "pending") }
            if (!snap.isSystemApp) q = q.transition(job.id, JobPhase.AWAITING_USER, now)
            q to Unit
        }
        val outcome = channel.send(Command.INSTALL, job.packageName, payload.encode(), auth)
        val cancel = synchronized(cancelRequested) { cancelRequested.remove(job.id) }
        when (outcome) {
            is CommandOutcome.Replied -> {
                val r = outcome.result
                if (r.ok) {
                    mutateQueue { it.transition(job.id, JobPhase.VERIFYING, now) { j -> j.copy(sessionId = r.sessionId) } to Unit }
                    val h = _profile.value.profile?.handle
                    val p = h?.let { launcher.presence(job.packageName, it) }
                    if (p is Presence.Present || (p is Presence.Unknown && r.versionCode != null)) {
                        mutateQueue { it.transition(job.id, JobPhase.SUCCEEDED, now) { j -> j.copy(note = r.steps.joinToString(" → ")) } to Unit }
                        markInstalled(job.packageName, r.method ?: InstallMethod.PACKAGE_INSTALLER_COPY, r.versionCode ?: snap.versionCode)
                        log(LogLevel.INFO, LogCode.JOB_SUCCEEDED, job.packageName, "${r.method} v=${r.versionCode}")
                    } else {
                        failJob(job, Failures.simple(FailureStage.VERIFY, FailureCause.NOT_PRESENT_AFTER_INSTALL, "LAUNCHER_APPS_ABSENT", now, r.steps.joinToString(" → ")))
                    }
                } else {
                    handleAuthProblems(r)
                    if (cancel) cancelJob(job) else failJob(job, Failures.fromResult(r, FailureStage.INSTALL, now))
                }
            }
            is CommandOutcome.NoHandler -> failJob(job, Failures.unreachable(FailureStage.COMMUNICATION, outcome.message, now))
            CommandOutcome.NoResult -> settleWithoutResult(job, cancel)
            CommandOutcome.NoHost -> mutateQueue { it.transition(job.id, JobPhase.INTERRUPTED, now) to Unit }
        }
    }

    private suspend fun runUninstall(job: CloneJob) {
        mutateQueue { it.transition(job.id, JobPhase.CHECKING, now) to Unit }
        log(LogLevel.INFO, LogCode.JOB_STARTED, job.packageName, "uninstall")
        val auth = pairing.auth() ?: return failJob(job, Failures.simple(FailureStage.PROFILE, FailureCause.PROFILE_NOT_PAIRED, "NO_SECRET", now))
        mutateQueue { it.transition(job.id, JobPhase.AWAITING_USER, now) { j -> j.copy(requestId = "pending") } to Unit }
        when (val o = channel.send(Command.UNINSTALL, job.packageName, "", auth)) {
            is CommandOutcome.Replied -> if (o.result.ok) {
                mutateQueue { it.transition(job.id, JobPhase.VERIFYING, now) to Unit }
                val h = _profile.value.profile?.handle
                val gone = h == null || launcher.presence(job.packageName, h) is Presence.Absent || o.result.method == InstallMethod.ENABLE_SYSTEM_APP
                if (gone) {
                    mutateQueue { it.transition(job.id, JobPhase.SUCCEEDED, now) to Unit }
                    val originalKept = discovery.entry(job.packageName) != null
                    _records.value[job.packageName]?.let { upsertRecord(it.copy(installState = InstallState.REMOVED, launchState = LaunchState.NONE, lastFailure = null, updatedAt = now)) }
                    log(LogLevel.INFO, LogCode.CLONE_REMOVED, job.packageName, "aplikasi asli tetap terpasang=$originalKept")
                    refreshPresence()
                } else {
                    failJob(job, Failures.simple(FailureStage.UNINSTALL, FailureCause.UNKNOWN, "STILL_PRESENT", now))
                }
            } else {
                handleAuthProblems(o.result)
                failJob(job, Failures.fromResult(o.result, FailureStage.UNINSTALL, now))
            }
            is CommandOutcome.NoHandler -> failJob(job, Failures.unreachable(FailureStage.UNINSTALL, o.message, now))
            CommandOutcome.NoResult -> settleWithoutResult(job, false)
            CommandOutcome.NoHost -> mutateQueue { it.transition(job.id, JobPhase.INTERRUPTED, now) to Unit }
        }
    }

    /** Tidak ada hasil: cocokkan langsung dengan keadaan nyata paket di profil. */
    private suspend fun settleWithoutResult(job: CloneJob, cancelled: Boolean) {
        val h = _profile.value.profile?.handle
        val p = h?.let { launcher.presence(job.packageName, it) }
        val wanted = when (job.kind) {
            JobKind.INSTALL -> p is Presence.Present && (_records.value[job.packageName]?.installedVersionCode == null)
            JobKind.UNINSTALL -> p is Presence.Absent
        }
        when {
            wanted -> {
                mutateQueue { it.transition(job.id, JobPhase.VERIFYING, now).transition(job.id, JobPhase.SUCCEEDED, now) { j -> j.copy(note = "dicocokkan tanpa balasan") } to Unit }
                if (job.kind == JobKind.INSTALL) markInstalled(job.packageName, InstallMethod.PACKAGE_INSTALLER_COPY, job.expectedVersionCode)
                log(LogLevel.INFO, LogCode.JOB_RECONCILED, job.packageName, "presence=$p")
            }
            cancelled -> cancelJob(job)
            else -> failJob(job, Failures.interrupted(if (job.kind == JobKind.INSTALL) FailureStage.INSTALL else FailureStage.UNINSTALL, now))
        }
    }

    private suspend fun failJob(job: CloneJob, f: Failure) {
        mutateQueue { q ->
            val current = q.job(job.id)
            val q2 = if (current != null && current.phase == JobPhase.INTERRUPTED) q.transition(job.id, JobPhase.FAILED, now) { it.copy(failure = f) } else q.fail(job.id, f, now)
            q2 to Unit
        }
        if (job.kind == JobKind.INSTALL) {
            val r = _records.value[job.packageName]
            if (r != null && r.installState != InstallState.INSTALLED) upsertRecord(r.copy(installState = InstallState.FAILED, lastFailure = f, updatedAt = now))
            else if (r != null) upsertRecord(r.copy(lastFailure = f, updatedAt = now))
        }
        log(LogLevel.ERROR, LogCode.JOB_FAILED, job.packageName, "${f.stage}/${f.cause} ${f.code}")
    }

    private suspend fun cancelJob(job: CloneJob) {
        mutateQueue { q ->
            val cur = q.job(job.id)
            (if (cur != null && cur.phase.isRunning) q.transition(job.id, JobPhase.FAILED, now) { it.copy(failure = Failures.simple(FailureStage.CANCELLED, FailureCause.USER_CANCELLED, "CANCELLED", now)) }.cancel(job.id, now) else q.cancel(job.id, now)) to Unit
        }
        log(LogLevel.INFO, LogCode.JOB_CANCELLED, job.packageName, null)
    }

    private fun markInstalled(pkg: String, method: InstallMethod, versionCode: Long?) {
        val serial = _profile.value.profile?.serial ?: return
        val existing = _records.value[pkg] ?: CloneRecord(pkg, serial, discovery.entry(pkg)?.label ?: pkg)
        upsertRecord(
            existing.copy(
                installState = InstallState.INSTALLED, installMethod = method, installedVersionCode = versionCode,
                lastInstallAt = now, lastFailure = null, launchState = LaunchState.NONE, updatedAt = now,
            ),
        )
        refreshPresence()
    }

    /** Pencocokan ulang setelah proses ClonApps dimulai lagi (process death / reboot). */
    private suspend fun reconcile() {
        refreshProfile()
        val handle = _profile.value.profile?.handle
        val includeRunning = !runnerActive.get()
        mutateQueue { q ->
            q.reconcile(now, includeRunning) { job ->
                if (handle == null) return@reconcile Observation.Unknown
                when (val p = launcher.presence(job.packageName, handle)) {
                    Presence.Absent -> Observation.Absent
                    is Presence.Unknown -> Observation.Unknown
                    Presence.Present -> {
                        val v = _info.value?.packages?.get(job.packageName)
                        val rec = _records.value[job.packageName]
                        when {
                            v != null -> Observation.Present(v)
                            // Pembaruan: versi lama sudah ada sebelumnya, jadi keberadaan saja bukan bukti.
                            rec?.installedVersionCode != null && job.kind == JobKind.INSTALL -> Observation.Unknown
                            else -> Observation.Present(job.expectedVersionCode ?: 0L)
                        }
                    }
                }
            }.prune() to Unit
        }
        // Catatan clone vs keadaan nyata profil
        if (handle != null && _profile.value.profile?.quietMode == false) {
            _records.value.values.forEach { r ->
                val p = launcher.presence(r.packageName, handle)
                if (r.installState == InstallState.INSTALLED && p is Presence.Absent) {
                    upsertRecord(r.copy(installState = InstallState.REMOVED, launchState = LaunchState.NONE, updatedAt = now))
                    log(LogLevel.WARN, LogCode.JOB_RECONCILED, r.packageName, "dihapus di luar ClonApps")
                } else if (r.installState != InstallState.INSTALLED && p is Presence.Present && _queue.value.activeFor(r.packageName, r.profileSerial) == null) {
                    upsertRecord(r.copy(installState = InstallState.INSTALLED, installMethod = r.installMethod ?: InstallMethod.ALREADY_PRESENT, updatedAt = now))
                }
            }
        }
        refreshPresence()
        if (_queue.value.next() != null) kick()
    }

    // ---------------------------------------------------------------- clone

    fun launch(pkg: String) = scope.launch {
        val h = _profile.value.profile?.handle ?: return@launch message(R.string.msg_no_profile)
        if (_profile.value.profile?.quietMode == true) message(R.string.msg_profile_paused_launch)
        when (val o = launcher.launch(pkg, h, now)) {
            is LaunchOutcome.Requested -> {
                updateRecord(pkg) { it.copy(launchState = LaunchState.REQUESTED, lastLaunchAt = now, updatedAt = now) }
                log(LogLevel.INFO, LogCode.LAUNCH_REQUESTED, pkg, "${o.component} @ ${o.user}")
            }
            is LaunchOutcome.Failed -> {
                // Alternatif dengan backend yang SAMA (profil kerja yang sama), lewat ClonApps di profil.
                val auth = pairing.auth()
                val alt = if (auth != null && o.failure.cause != FailureCause.PROFILE_LOCKED) channel.send(Command.LAUNCH, pkg, "", auth) else null
                if (alt is CommandOutcome.Replied && alt.result.ok) {
                    updateRecord(pkg) { it.copy(launchState = LaunchState.REQUESTED, lastLaunchAt = now, updatedAt = now) }
                    log(LogLevel.INFO, LogCode.LAUNCH_REQUESTED, pkg, "via ClonApps profil; LauncherApps gagal: ${o.failure.cause}")
                } else {
                    val f = if (alt is CommandOutcome.Replied) Failures.fromResult(alt.result, FailureStage.LAUNCH, now) else o.failure
                    updateRecord(pkg) { it.copy(launchState = LaunchState.FAILED, lastFailure = f, updatedAt = now) }
                    log(LogLevel.ERROR, LogCode.LAUNCH_FAILED, pkg, "${f.cause} ${f.code}")
                    message(R.string.msg_launch_failed)
                }
            }
        }
    }

    /**
     * Peluncuran sinkron untuk shortcut: dipanggil saat activity ClonApps masih tampil, sehingga
     * tidak terkena pembatasan peluncuran activity dari latar belakang.
     */
    fun launchSync(pkg: String): Boolean {
        val h = _profile.value.profile?.handle ?: return false
        return when (val o = launcher.launch(pkg, h, now)) {
            is LaunchOutcome.Requested -> {
                updateRecord(pkg) { it.copy(launchState = LaunchState.REQUESTED, lastLaunchAt = now, updatedAt = now) }
                log(LogLevel.INFO, LogCode.LAUNCH_REQUESTED, pkg, "shortcut: ${o.component} @ ${o.user}")
                true
            }
            is LaunchOutcome.Failed -> {
                updateRecord(pkg) { it.copy(launchState = LaunchState.FAILED, lastFailure = o.failure, updatedAt = now) }
                log(LogLevel.ERROR, LogCode.LAUNCH_FAILED, pkg, "shortcut: ${o.failure.cause}")
                false
            }
        }
    }

    fun confirmRunning(pkg: String) {
        updateRecord(pkg) { it.copy(launchState = LaunchState.USER_CONFIRMED_RUNNING, lastFailure = null, updatedAt = now) }
        log(LogLevel.INFO, LogCode.USER_CONFIRMED_RUNNING, pkg, null)
    }

    enum class ProblemKind { LOGIN, TARGET_RESTRICTION, LAUNCH }

    fun reportProblem(pkg: String, kind: ProblemKind) {
        val (stage, cause) = when (kind) {
            ProblemKind.LOGIN -> FailureStage.LOGIN to FailureCause.UNKNOWN
            ProblemKind.TARGET_RESTRICTION -> FailureStage.TARGET_RESTRICTION to FailureCause.TARGET_RESTRICTS_PROFILE
            ProblemKind.LAUNCH -> FailureStage.LAUNCH to FailureCause.UNKNOWN
        }
        val f = Failure(stage, cause, "USER_REPORTED", "Dilaporkan pengguna", RecoveryAction.COLLECT_ADB_LOG, true, now)
        updateRecord(pkg) { it.copy(launchState = LaunchState.FAILED, lastFailure = f, updatedAt = now) }
        log(LogLevel.WARN, LogCode.USER_REPORTED_PROBLEM, pkg, kind.name)
    }

    fun setLabel(pkg: String, label: String?) =
        updateRecord(pkg) { it.copy(customLabel = label?.trim()?.take(SettingsCodec.MAX_LABEL)?.takeIf { l -> l.isNotEmpty() }, updatedAt = now) }

    fun toggleFavorite(pkg: String) = updateRecord(pkg) { it.copy(favorite = !it.favorite, updatedAt = now) }

    fun pinShortcut(pkg: String) {
        val p = _profile.value.profile ?: return message(R.string.msg_no_profile)
        val rec = _records.value[pkg] ?: return
        val size = (app.resources.displayMetrics.density * 108).toInt()
        val icon = launcher.badgedIcon(pkg, p.handle, size)
        when (val r = Shortcuts.requestPin(app, pkg, p.serial, rec.displayLabel, icon)) {
            ShortcutResult.Requested -> { log(LogLevel.INFO, LogCode.SHORTCUT_REQUESTED, pkg, null); message(R.string.msg_shortcut_requested) }
            ShortcutResult.NotSupported -> message(R.string.msg_shortcut_unsupported)
            is ShortcutResult.Error -> message(R.string.msg_shortcut_error, listOf(r.message ?: "-"))
        }
    }

    fun handleForSerial(serial: Long): UserHandle? = env.handleForSerial(serial)

    // ---------------------------------------------------------------- pengaturan

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        val s = f(_settings.value)
        _settings.value = s
        stores.saveSettings(s)
    }

    fun exportSettings(appVersion: String): String {
        val entries = _records.value.values.map { CloneLabelEntry(it.packageName, it.customLabel, it.favorite) }
        log(LogLevel.INFO, LogCode.SETTINGS_EXPORTED, null, "entries=${entries.size}")
        return SettingsCodec.export(SettingsExport(_settings.value, entries), now, appVersion)
    }

    fun importSettings(text: String): ImportResult {
        val r = SettingsCodec.import(text)
        if (r is ImportResult.Ok) {
            updateSettings { r.export.settings }
            val serial = _profile.value.profile?.serial ?: pairing.profileSerial ?: -1L
            r.export.clones.forEach { e ->
                val existing = _records.value[e.packageName]
                val label = discovery.entry(e.packageName)?.label ?: e.packageName
                upsertRecord((existing ?: CloneRecord(e.packageName, serial, label)).copy(customLabel = e.customLabel, favorite = e.favorite, updatedAt = now))
            }
            log(LogLevel.INFO, LogCode.SETTINGS_IMPORTED, null, "entries=${r.export.clones.size} skipped=${r.skipped}")
        }
        return r
    }

    fun clearLog() {
        _log.value = emptyList()
        scope.launch(Dispatchers.IO) { stores.saveLog(emptyList()) }
    }

    // ---------------------------------------------------------------- utilitas

    private suspend fun <T> mutateQueue(f: (InstallQueue) -> Pair<InstallQueue, T>): T = stateLock.withLock {
        val (q, r) = f(_queue.value)
        _queue.value = q
        withContext(Dispatchers.IO) { stores.saveQueue(q) }
        r
    }

    private fun updateRecord(pkg: String, f: (CloneRecord) -> CloneRecord) {
        val r = _records.value[pkg] ?: return
        upsertRecord(f(r))
    }

    @Synchronized
    private fun upsertRecord(r: CloneRecord) {
        _records.update { it + (r.packageName to r) }
        val snapshot = _records.value.values.toList()
        scope.launch(Dispatchers.IO) { stores.saveRecords(snapshot) }
    }

    @Synchronized
    private fun log(level: LogLevel, code: LogCode, pkg: String?, detail: String?) {
        val e = LogEntry(now, level, code, pkg, detail?.let { com.clonapps.core.diag.Redactor.redact(it) })
        _log.update { (listOf(e) + it).take(MAX_LOG) }
        val snapshot = _log.value
        scope.launch(Dispatchers.IO) { stores.saveLog(snapshot) }
    }

    private fun message(res: Int, args: List<Any> = emptyList()) {
        _messages.tryEmit(UiMessage(res, args))
    }

    private suspend fun busy(block: suspend () -> Unit) {
        _busy.value = true
        try { block() } finally { _busy.value = false }
    }

    private fun registerProfileListeners() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshProfile()
                if (_profile.value.usable) kick()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        app.getSystemService(LauncherApps::class.java).registerCallback(
            object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshPresence()
                override fun onPackageAdded(packageName: String, user: UserHandle) = refreshPresence()
                override fun onPackageChanged(packageName: String, user: UserHandle) = refreshPresence()
                override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshPresence()
                override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshPresence()
            },
            Handler(Looper.getMainLooper()),
        )
    }

    companion object {
        const val MAX_LOG = 500
    }
}
