package com.clonapps.domain

import com.clonapps.core.backend.BackendAvailability
import com.clonapps.core.backend.BackendCapabilities
import com.clonapps.core.backend.BackendId
import com.clonapps.core.backend.Capacity
import com.clonapps.core.diag.Redactor
import com.clonapps.core.model.CloneRecord
import com.clonapps.data.LogEntry
import com.clonapps.platform.DeviceReport
import com.clonapps.platform.ProfileState

/** Laporan diagnostik teks. Selalu melewati [Redactor]; tidak memuat kunci, token, atau data aplikasi. */
object DiagnosticReport {

    fun backends(profile: ProfileState, device: DeviceReport): List<BackendCapabilities> = listOf(
        Capacity.workProfile(available = device.featureManagedUsers, canPauseDirectly = false),
        BackendCapabilities(
            BackendId.SYSTEM_CLONE_PROFILE,
            if (profile.others.isNotEmpty()) BackendAvailability.DETECTED_NOT_CONTROLLABLE else BackendAvailability.NOT_AVAILABLE,
            0, 0, keepsOriginalSignature = true, requiresUserConfirmationPerInstall = true, canPauseProfileDirectly = false,
        ),
        BackendCapabilities(
            BackendId.VIRTUALIZATION_ENGINE, BackendAvailability.NOT_IMPLEMENTED,
            0, 0, keepsOriginalSignature = false, requiresUserConfirmationPerInstall = false, canPauseProfileDirectly = false,
        ),
    )

    fun build(
        appVersion: String,
        device: DeviceReport,
        profile: ProfileState,
        info: ProfileInfoReport?,
        records: Collection<CloneRecord>,
        log: List<LogEntry>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("ClonApps diagnostic report (redacted)")
        sb.appendLine("app=$appVersion")
        sb.appendLine("## device")
        with(device) {
            sb.appendLine("manufacturer=$manufacturer brand=$brand model=$model device=$device")
            sb.appendLine("android=$androidRelease sdk=$sdkInt patch=$securityPatch")
            sb.appendLine("rom=$rom evidence=${romEvidence.joinToString("; ")}")
            sb.appendLine("display=$buildDisplay incremental=$buildIncremental")
            sb.appendLine("abis=${supportedAbis.joinToString()} 64bit=$is64Bit")
            sb.appendLine("ramBytes=$totalRamBytes lowRam=$lowRamDevice dataFree=$dataFreeBytes dataTotal=$dataTotalBytes sw=${smallestWidthDp}dp")
            sb.appendLine("feature.managed_users=$featureManagedUsers feature.device_admin=$featureDeviceAdmin")
            sb.appendLine("provisioningAllowed=$provisioningAllowed provisioningUi=$provisioningUiPresent")
        }
        sb.appendLine("## profile")
        sb.appendLine("ownership=${profile.ownership} unverified=${profile.unverified} usable=${profile.usable}")
        profile.profile?.let { sb.appendLine("profile=${it.label} hasClonApps=${it.hasClonApps} quiet=${it.quietMode} unlocked=${it.unlocked}") }
        profile.others.forEach { sb.appendLine("other=${it.label} hasClonApps=${it.hasClonApps} quiet=${it.quietMode}") }
        sb.appendLine("## backends")
        backends(profile, device).forEach {
            sb.appendLine("${it.id} ${it.availability} maxProfiles=${it.maxProfiles} perApp=${it.maxInstancesPerAppPerProfile} keepsSignature=${it.keepsOriginalSignature} confirmEachInstall=${it.requiresUserConfirmationPerInstall}")
        }
        sb.appendLine("## profile info")
        if (info == null) {
            sb.appendLine("not fetched")
        } else {
            sb.appendLine("canRequestPackageInstalls=${info.canRequestPackageInstalls} unknownSourcesRestricted=${info.unknownSourcesRestricted}")
            sb.appendLine("restrictions=${info.restrictions.joinToString()}")
            info.integration.forEach { (k, v) -> sb.appendLine("integration.$k=$v") }
            sb.appendLine("packagesInProfile=${info.packages.size}")
        }
        sb.appendLine("## clones")
        records.forEach { r ->
            sb.appendLine(
                "${r.packageName} install=${r.installState} method=${r.installMethod} v=${r.installedVersionCode} launch=${r.launchState}" +
                    (r.lastFailure?.let { " failure=${it.stage}/${it.cause}/${it.code}" } ?: ""),
            )
        }
        sb.appendLine("## recent activity")
        log.take(80).forEach { sb.appendLine("${it.at} ${it.level} ${it.code} ${it.packageName ?: "-"} ${it.detail ?: ""}") }
        return Redactor.redact(sb.toString())
    }

    /** Matriks pengujian (CSV). Kolom fitur hanya berisi apa yang benar-benar dicatat. */
    fun testMatrixCsv(device: DeviceReport, records: Collection<CloneRecord>): String {
        val header = "app,version,device,android,rom,backend,install_result,install_method,launch_result,features_checked,last_failure"
        val rows = records.map { r ->
            listOf(
                r.packageName,
                r.installedVersionCode?.toString() ?: "",
                "${device.manufacturer} ${device.model}",
                "${device.androidRelease} (API ${device.sdkInt})",
                device.rom,
                "WORK_PROFILE",
                r.installState.name,
                r.installMethod?.name ?: "",
                r.launchState.name,
                when (r.launchState.name) {
                    "USER_CONFIRMED_RUNNING" -> "launch(user-confirmed)"
                    "REQUESTED" -> "launch(request only)"
                    else -> ""
                },
                r.lastFailure?.let { "${it.stage}/${it.cause}" } ?: "",
            ).joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        }
        return Redactor.redact((listOf(header) + rows).joinToString("\n"))
    }
}
