package com.clonapps.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clonapps.R
import com.clonapps.core.model.CloneStatus
import com.clonapps.core.validate.CheckOutcome
import com.clonapps.domain.CloneCoordinator
import com.clonapps.platform.AppEntry
import com.clonapps.ui.components.AppIcon
import com.clonapps.ui.components.AppTitle
import com.clonapps.ui.components.CenteredState
import com.clonapps.ui.components.StatusChip
import com.clonapps.ui.components.enumText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PickScreen(c: CloneCoordinator, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by c.settings.collectAsState()
    val presence by c.presence.collectAsState()
    val prechecks by c.prechecks.collectAsState()
    val queue by c.queue.collectAsState()
    val profile by c.profile.collectAsState()
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var includeSystem by rememberSaveable { mutableStateOf(settings.showSystemApps) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(includeSystem) {
        apps = null
        apps = withContext(Dispatchers.IO) {
            runCatching { c.discovery.listApps(includeSystem) }.onFailure { loadError = it.message }.getOrDefault(emptyList())
        }
    }

    val filtered = apps?.let { list ->
        val q = query.trim()
        list.filter { q.isEmpty() || it.label.contains(q, true) || it.packageName.contains(q, true) }
            .let { if (descending) it.reversed() else it }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.pick_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !includeSystem, onClick = { includeSystem = false }, label = { Text(stringResource(R.string.pick_filter_user)) })
                FilterChip(selected = includeSystem, onClick = { includeSystem = true }, label = { Text(stringResource(R.string.pick_filter_with_system)) })
                FilterChip(selected = descending, onClick = { descending = !descending }, label = { Text(stringResource(if (descending) R.string.pick_sort_za else R.string.pick_sort_az)) })
            }
            if (includeSystem) Text(stringResource(R.string.pick_system_note), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        when {
            filtered == null -> CenteredState(stringResource(R.string.loading_apps), loading = true, modifier = Modifier.weight(1f))
            loadError != null && apps.isNullOrEmpty() -> CenteredState(stringResource(R.string.error_title), loadError, modifier = Modifier.weight(1f))
            filtered.isEmpty() -> CenteredState(stringResource(R.string.pick_empty), modifier = Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    val inProfile = app.packageName in presence
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .toggleable(value = checked, role = Role.Checkbox, enabled = !inProfile) {
                                selected = if (it) selected + app.packageName else selected - app.packageName
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = !inProfile)
                        AppIcon(app.packageName)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AppTitle(app.label, app.packageName)
                            val size = app.apkBytes?.let { Formatter.formatShortFileSize(context, it) }
                            Text(
                                listOfNotNull(
                                    stringResource(R.string.pick_version, app.versionName ?: "?", app.versionCode),
                                    size?.let { stringResource(R.string.pick_apk_size, it) },
                                    if (app.splitCount > 0) stringResource(R.string.pick_splits, app.splitCount) else null,
                                    if (app.isSystem) stringResource(R.string.pick_system_badge) else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip(if (inProfile) CloneStatus.INSTALLED else c.statusOf(app.packageName))
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pick_selected_count, selected.size), modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.action_clear)) }
                Button(
                    onClick = { c.precheck(selected.toList()); showSummary = true },
                    enabled = selected.isNotEmpty(),
                ) { Text(stringResource(R.string.action_check_requirements)) }
            }
        }
    }

    if (showSummary) {
        val entries = apps.orEmpty().filter { it.packageName in selected }
        Dialog(onDismissRequest = { showSummary = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth(0.95f).padding(vertical = 24.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.summary_note), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 520.dp)) {
                        items(entries, key = { it.packageName }) { app ->
                            val pre = prechecks[app.packageName]
                            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppTitle(app.label, app.packageName, Modifier.weight(1f))
                                    if (pre == null) Text(stringResource(R.string.summary_checking)) else StatusChip(pre.overall)
                                }
                                pre?.plannedMethod?.let { Text(stringResource(R.string.summary_method, enumText("method", it)), style = MaterialTheme.typography.bodySmall) }
                                pre?.items?.filter { it.outcome != CheckOutcome.PASS }?.forEach { item ->
                                    Text(
                                        "• " + enumText("check", item.id) + ": " + enumText("outcome", item.outcome) +
                                            (item.cause?.let { " — " + enumText("cause", it) } ?: "") +
                                            (item.detail?.let { " ($it)" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    val ready = entries.filter { prechecks[it.packageName]?.overall == CloneStatus.READY && queue.activeFor(it.packageName, profile.profile?.serial ?: -1) == null }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showSummary = false }) { Text(stringResource(R.string.action_back)) }
                        OutlinedButton(onClick = { c.precheck(selected.toList()) }) { Text(stringResource(R.string.action_recheck)) }
                        Button(
                            onClick = {
                                c.enqueueInstall(ready.map { it.packageName })
                                showSummary = false
                                selected = emptySet()
                                onDone()
                            },
                            enabled = ready.isNotEmpty(),
                        ) { Text(stringResource(R.string.action_start_clone, ready.size)) }
                    }
                }
            }
        }
    }
}
