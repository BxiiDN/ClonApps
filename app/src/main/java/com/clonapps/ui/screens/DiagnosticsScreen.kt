package com.clonapps.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.clonapps.R
import com.clonapps.core.model.ProfileOwnership
import com.clonapps.domain.CloneCoordinator
import com.clonapps.domain.DiagnosticReport
import com.clonapps.platform.DeviceProbe
import com.clonapps.platform.DeviceReport
import com.clonapps.ui.components.CenteredState
import com.clonapps.ui.components.InfoRow
import com.clonapps.ui.components.SectionCard
import com.clonapps.ui.components.enumText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    c: CloneCoordinator,
    onShareText: (String, String) -> Unit,
    onSaveText: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profile by c.profile.collectAsState()
    val info by c.profileInfo.collectAsState()
    val records by c.records.collectAsState()
    val log by c.log.collectAsState()
    val device by produceState<DeviceReport?>(null, profile) { value = withContext(Dispatchers.IO) { DeviceProbe.probe(context) } }
    val d = device ?: return CenteredState(stringResource(R.string.loading), loading = true)
    val appVersion = runCatching { com.clonapps.platform.Pm.packageInfo(context.packageManager, context.packageName, 0).versionName }.getOrNull() ?: "?"
    fun size(b: Long) = Formatter.formatShortFileSize(context, b)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.diag_device)) {
            InfoRow(stringResource(R.string.diag_model), "${d.manufacturer} ${d.model} (${d.device})")
            InfoRow(stringResource(R.string.diag_android), "${d.androidRelease} · API ${d.sdkInt} · ${d.securityPatch}")
            InfoRow(stringResource(R.string.diag_rom), d.rom)
            InfoRow(stringResource(R.string.diag_rom_evidence), d.romEvidence.joinToString("; ").ifEmpty { "—" })
            InfoRow(stringResource(R.string.diag_build), d.buildDisplay)
            InfoRow(stringResource(R.string.diag_abi), d.supportedAbis.joinToString())
            InfoRow(stringResource(R.string.diag_ram), size(d.totalRamBytes) + if (d.lowRamDevice) " (low-RAM)" else "")
            InfoRow(stringResource(R.string.diag_storage), stringResource(R.string.diag_storage_value, size(d.dataFreeBytes), size(d.dataTotalBytes)))
            InfoRow(stringResource(R.string.diag_screen), "sw${d.smallestWidthDp}dp")
            Text(stringResource(R.string.diag_rom_note), style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(stringResource(R.string.diag_work_profile)) {
            InfoRow("android.software.managed_users", yesNo(d.featureManagedUsers))
            InfoRow("android.software.device_admin", yesNo(d.featureDeviceAdmin))
            InfoRow("isProvisioningAllowed", yesNo(d.provisioningAllowed))
            InfoRow(stringResource(R.string.diag_provisioning_ui), yesNo(d.provisioningUiPresent))
            InfoRow(stringResource(R.string.diag_ownership), enumText("ownership", profile.ownership))
            profile.profile?.let { InfoRow(stringResource(R.string.diag_profile_handle), "${it.label} · serial ${it.serial}") }
            profile.others.forEach { InfoRow(stringResource(R.string.diag_other_profile), it.label + if (it.hasClonApps) " (ClonApps)" else "") }
            c.provisioningBlocker()?.let { f -> Text(enumText("cause", f.cause), color = MaterialTheme.colorScheme.error) }
        }
        SectionCard(stringResource(R.string.diag_backends)) {
            DiagnosticReport.backends(profile, d).forEach { b ->
                Text(enumText("backend", b.id) + ": " + enumText("availability", b.availability), style = MaterialTheme.typography.bodyMedium)
                Text(enumText("backendnote", b.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionCard(stringResource(R.string.diag_profile_checks)) {
            val i = info
            if (i == null) {
                Text(stringResource(R.string.diag_profile_checks_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                InfoRow("canRequestPackageInstalls", i.canRequestPackageInstalls?.let { yesNo(it) })
                InfoRow("DISALLOW_INSTALL_UNKNOWN_SOURCES", i.unknownSourcesRestricted?.let { yesNo(it) })
                i.integration.forEach { (k, v) -> InfoRow(enumText(context, "integration", k), v.replace("true", stringResource(R.string.yes)).replace("false", stringResource(R.string.no))) }
                InfoRow(stringResource(R.string.diag_restrictions), i.restrictions.joinToString().ifEmpty { "—" })
                InfoRow(stringResource(R.string.diag_packages_in_profile), i.packages.size.toString())
                Text(stringResource(R.string.diag_integration_note), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { c.fetchProfileInfo() }, enabled = profile.ownership == ProfileOwnership.OURS_PAIRED) { Text(stringResource(R.string.action_check_profile)) }
        }
        SectionCard(stringResource(R.string.diag_report)) {
            Text(stringResource(R.string.diag_report_note), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onShareText(DiagnosticReport.build(appVersion, d, profile, info, records.values, log), "clonapps-diagnostic.txt") }) {
                    Text(stringResource(R.string.action_share_report))
                }
                OutlinedButton(onClick = { onSaveText(DiagnosticReport.build(appVersion, d, profile, info, records.values, log), "clonapps-diagnostic.txt") }) {
                    Text(stringResource(R.string.action_save_report))
                }
                OutlinedButton(onClick = { onSaveText(DiagnosticReport.testMatrixCsv(d, records.values), "clonapps-test-matrix.csv") }) {
                    Text(stringResource(R.string.action_save_matrix))
                }
            }
        }
        SectionCard(stringResource(R.string.diag_adb_title)) {
            Text(stringResource(R.string.diag_adb_intro), style = MaterialTheme.typography.bodySmall)
            val user = profile.profile?.label?.filter { it.isDigit() }?.ifEmpty { null } ?: "<USER_ID>"
            SelectionContainer {
                Text(adbGuide(user), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun yesNo(b: Boolean) = stringResource(if (b) R.string.yes else R.string.no)

private fun adbGuide(user: String) = """
# Daftar pengguna/profil
adb shell pm list users
# Paket di profil kerja
adb shell pm list packages --user $user | grep <paket>
# Status paket per pengguna
adb shell dumpsys package <paket> | grep -A4 "User $user"
# Jalankan aplikasi di profil kerja
adb shell monkey --user $user -p <paket> -c android.intent.category.LAUNCHER 1
# Log crash
adb logcat -b crash -d
# Log aplikasi target (PID di profil kerja)
adb shell ps -A | grep <paket>
adb logcat --pid=<PID>
# Log ClonApps & PackageInstaller
adb logcat -s ClonAppsAdmin ClonAppsSetup ClonAppsProfileCmd ClonAppsProfileOps ClonAppsInstall PackageInstaller
""".trimIndent()
