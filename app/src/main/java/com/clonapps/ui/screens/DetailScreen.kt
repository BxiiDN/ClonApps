package com.clonapps.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clonapps.R
import com.clonapps.core.model.InstallState
import com.clonapps.core.queue.JobPhase
import com.clonapps.domain.CloneCoordinator
import com.clonapps.ui.components.AppIcon
import com.clonapps.ui.components.CenteredState
import com.clonapps.ui.components.ConfirmDialog
import com.clonapps.ui.components.FailureDetails
import com.clonapps.ui.components.InfoRow
import com.clonapps.ui.components.SectionCard
import com.clonapps.ui.components.StatusChip
import com.clonapps.ui.components.TextInputDialog
import com.clonapps.ui.components.enumText
import java.util.Date

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(c: CloneCoordinator, pkg: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val records by c.records.collectAsState()
    val presence by c.presence.collectAsState()
    val profile by c.profile.collectAsState()
    val queue by c.queue.collectAsState()
    val info by c.profileInfo.collectAsState()
    val record = records[pkg] ?: return CenteredState(stringResource(R.string.detail_not_found))
    var editLabel by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf(false) }
    val present = pkg in presence
    val original = remember(pkg, presence, records) { c.discovery.entry(pkg) }
    val job = queue.jobs.filter { it.packageName == pkg }.maxByOrNull { it.updatedAt }
    fun time(t: Long?) = t?.let { DateFormat.getMediumDateFormat(context).format(Date(it)) + " " + DateFormat.getTimeFormat(context).format(Date(it)) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(pkg, profile.profile?.handle, size = 64.dp, launcher = c.launcher)
            Column(Modifier.weight(1f)) {
                Text(record.displayLabel, style = MaterialTheme.typography.headlineSmall)
                Text(pkg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusChip(c.statusOf(pkg))
            }
        }

        SectionCard(stringResource(R.string.detail_actions)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.launch(pkg) }, enabled = present && profile.usable) { Text(stringResource(R.string.action_open_clone)) }
                OutlinedButton(onClick = { c.pinShortcut(pkg) }, enabled = present) { Text(stringResource(R.string.action_shortcut)) }
                OutlinedButton(onClick = { editLabel = true }) { Text(stringResource(R.string.action_edit_label)) }
                OutlinedButton(onClick = { c.toggleFavorite(pkg) }) {
                    Text(stringResource(if (record.favorite) R.string.action_unfavorite else R.string.action_favorite))
                }
                if (original != null && present && (record.installedVersionCode ?: 0) < original.versionCode) {
                    OutlinedButton(onClick = { c.enqueueInstall(listOf(pkg)) }) { Text(stringResource(R.string.action_update_from_main)) }
                }
                if (!present && record.installState != InstallState.INSTALLED && original != null && job?.phase?.blocksDuplicate != true) {
                    OutlinedButton(onClick = { c.enqueueInstall(listOf(pkg)) }) { Text(stringResource(R.string.action_clone_again)) }
                }
                if (job != null && job.phase == JobPhase.FAILED && job.failure?.retryable == true && job.attempts < job.maxAttempts) {
                    OutlinedButton(onClick = { c.retry(job.id) }) { Text(stringResource(R.string.action_retry)) }
                }
                OutlinedButton(onClick = { confirmRemove = true }, enabled = present) { Text(stringResource(R.string.action_remove_clone)) }
            }
        }

        SectionCard(stringResource(R.string.detail_verify_title)) {
            Text(stringResource(R.string.detail_verify_body), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.confirmRunning(pkg) }, enabled = present) { Text(stringResource(R.string.action_confirm_running)) }
                OutlinedButton(onClick = { report = true }, enabled = present) { Text(stringResource(R.string.action_report_problem)) }
            }
        }

        SectionCard(stringResource(R.string.detail_status_title)) {
            InfoRow(stringResource(R.string.detail_profile), profile.profile?.label)
            InfoRow(stringResource(R.string.detail_install_state), enumText("install", record.installState))
            InfoRow(stringResource(R.string.detail_install_method), record.installMethod?.let { enumText("method", it) })
            InfoRow(stringResource(R.string.detail_installed_version), record.installedVersionCode?.toString() ?: info?.packages?.get(pkg)?.toString())
            InfoRow(stringResource(R.string.detail_original_version), original?.let { "${it.versionName} (${it.versionCode})" } ?: stringResource(R.string.detail_original_missing))
            InfoRow(stringResource(R.string.detail_last_install), time(record.lastInstallAt))
            InfoRow(stringResource(R.string.detail_launch_state), enumText("launch", record.launchState))
            InfoRow(stringResource(R.string.detail_last_launch), time(record.lastLaunchAt))
            InfoRow(stringResource(R.string.detail_present_in_profile), stringResource(if (present) R.string.yes else R.string.no))
            Text(stringResource(R.string.detail_identity_note), style = MaterialTheme.typography.bodySmall)
        }

        job?.let { j ->
            SectionCard(stringResource(R.string.detail_last_job)) {
                InfoRow(stringResource(R.string.job_kind), enumText("jobkind", j.kind))
                InfoRow(stringResource(R.string.job_phase), enumText("phase", j.phase))
                InfoRow(stringResource(R.string.job_attempts), "${j.attempts}/${j.maxAttempts}")
                j.note?.let { InfoRow(stringResource(R.string.job_note), it) }
            }
        }
        (record.lastFailure ?: job?.failure)?.let { f ->
            SectionCard(stringResource(R.string.detail_failure)) { FailureDetails(f) }
        }

        SectionCard(stringResource(R.string.detail_integration_title)) {
            Text(stringResource(R.string.detail_integration_body), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (editLabel) {
        TextInputDialog(
            title = stringResource(R.string.action_edit_label),
            initial = record.customLabel ?: "",
            hint = record.originalLabel,
            onSave = { c.setLabel(pkg, it); editLabel = false },
            onDismiss = { editLabel = false },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.remove_clone_title, record.displayLabel),
            body = stringResource(R.string.remove_clone_body),
            confirmLabel = stringResource(R.string.action_remove_clone),
            onConfirm = { confirmRemove = false; c.removeClone(pkg) },
            onDismiss = { confirmRemove = false },
        )
    }
    if (report) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { report = false },
            title = { Text(stringResource(R.string.action_report_problem)) },
            text = {
                Column {
                    CloneCoordinator.ProblemKind.values().forEach { k ->
                        TextButton(onClick = { c.reportProblem(pkg, k); report = false }) { Text(enumText("problem", k)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { report = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
