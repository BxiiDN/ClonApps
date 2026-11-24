package com.clonapps.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clonapps.R
import com.clonapps.core.queue.JobPhase
import com.clonapps.core.queue.RetryDecision
import com.clonapps.data.LogLevel
import com.clonapps.domain.CloneCoordinator
import com.clonapps.ui.components.CenteredState
import com.clonapps.ui.components.SectionCard
import com.clonapps.ui.components.enumText
import java.util.Date

@Composable
fun ActivityScreen(c: CloneCoordinator, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val queue by c.queue.collectAsState()
    val log by c.log.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    fun time(t: Long) = DateFormat.getTimeFormat(context).format(Date(t)) + " · " + DateFormat.getMediumDateFormat(context).format(Date(t))

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.activity_tab_queue)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.activity_tab_log)) })
        }
        if (tab == 0) {
            val jobs = queue.jobs.sortedByDescending { it.updatedAt }
            if (jobs.isEmpty()) {
                CenteredState(stringResource(R.string.activity_queue_empty))
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(jobs, key = { it.id }) { j ->
                        SectionCard(j.label) {
                            Text(j.packageName, style = MaterialTheme.typography.bodySmall)
                            Text(
                                enumText("jobkind", j.kind) + " · " + enumText("phase", j.phase) + " · " +
                                    stringResource(R.string.job_attempts_short, j.attempts, j.maxAttempts),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(enumText("phasehint", j.phase), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            j.failure?.let { f ->
                                Text(enumText("stage", f.stage) + " — " + enumText("cause", f.cause) + " (" + f.code + ")", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.failure_recovery) + ": " + enumText("recovery", f.recovery), style = MaterialTheme.typography.bodySmall)
                            }
                            j.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(time(j.updatedAt), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!j.phase.isTerminal && j.phase != JobPhase.FAILED) {
                                    OutlinedButton(onClick = { c.cancel(j.id) }) { Text(stringResource(R.string.action_cancel)) }
                                }
                                // Tombol coba lagi hanya bila keputusan antrean mengizinkan (batas percobaan & jenis kegagalan).
                                if (queue.retryDecision(j.id) == RetryDecision.Allowed) {
                                    OutlinedButton(onClick = { c.retry(j.id) }) { Text(stringResource(R.string.action_retry)) }
                                }
                                if (j.phase == JobPhase.FAILED) {
                                    TextButton(onClick = { c.cancel(j.id) }) { Text(stringResource(R.string.action_dismiss)) }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (log.isEmpty()) {
                CenteredState(stringResource(R.string.activity_log_empty))
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.activity_log_local_only), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { c.clearLog() }) { Text(stringResource(R.string.action_clear)) }
                        }
                    }
                    items(log) { e ->
                        Column {
                            Text(
                                enumText("log", e.code) + (e.packageName?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (e.level) {
                                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(time(e.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
