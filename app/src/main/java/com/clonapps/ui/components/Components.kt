package com.clonapps.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clonapps.R
import com.clonapps.core.model.CloneStatus
import com.clonapps.core.model.Failure
import com.clonapps.platform.ProfileLauncher
import com.clonapps.platform.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Teks lokal untuk nilai enum: mencari resource string bernama `<prefix>_<nama_enum_huruf_kecil>`.
 * Unit test memastikan setiap nilai enum punya teks di bahasa Indonesia dan Inggris.
 */
@SuppressLint("DiscouragedApi")
fun enumText(context: Context, prefix: String, name: String): String {
    val id = context.resources.getIdentifier("${prefix}_${name.lowercase()}", "string", context.packageName)
    return if (id != 0) context.getString(id) else name
}

@Composable
fun enumText(prefix: String, value: Enum<*>): String = enumText(LocalContext.current, prefix, value.name)

@Composable
fun StatusChip(status: CloneStatus, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        CloneStatus.NOT_CHECKED -> cs.surfaceVariant to cs.onSurfaceVariant
        CloneStatus.READY -> cs.secondaryContainer to cs.onSecondaryContainer
        CloneStatus.NEEDS_USER_ACTION -> cs.tertiaryContainer to cs.onTertiaryContainer
        CloneStatus.IN_PROGRESS -> cs.primaryContainer to cs.onPrimaryContainer
        CloneStatus.INSTALLED -> cs.primaryContainer to cs.onPrimaryContainer
        CloneStatus.LAUNCH_REQUESTED -> cs.secondaryContainer to cs.onSecondaryContainer
        CloneStatus.VERIFIED_RUNNING -> Color(0xFF1B5E20) to Color.White
        CloneStatus.FAILED -> cs.errorContainer to cs.onErrorContainer
    }
    val label = enumText("status", status)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), modifier = modifier.semantics { contentDescription = label }) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), maxLines = 1)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}

/** Kegagalan lengkap: tahap, kode, penyebab diketahui, yang belum diketahui, dan tindakan pemulihan. */
@Composable
fun FailureDetails(f: Failure) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoRow(stringResource(R.string.failure_stage), enumText("stage", f.stage))
        InfoRow(stringResource(R.string.failure_code), f.code)
        InfoRow(stringResource(R.string.failure_cause), enumText("cause", f.cause))
        InfoRow(stringResource(R.string.failure_unknown), enumText("unknown", f.stage))
        InfoRow(stringResource(R.string.failure_recovery), enumText("recovery", f.recovery))
        f.detail?.let { InfoRow(stringResource(R.string.failure_detail), it) }
        InfoRow(stringResource(R.string.failure_retryable), stringResource(if (f.retryable) R.string.yes else R.string.no))
    }
}

@Composable
fun CenteredState(
    title: String,
    body: String? = null,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            Text(title, style = MaterialTheme.typography.titleMedium)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            action?.invoke()
        }
    }
}

object IconCache {
    private val cache = LruCache<String, Bitmap>(300)
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, b: Bitmap) { cache.put(key, b) }
}

/** Ikon aplikasi dimuat di thread IO. Bila [user] diberikan, memakai ikon berlencana profil kerja. */
@Composable
fun AppIcon(packageName: String, user: UserHandle? = null, size: Dp = 40.dp, launcher: ProfileLauncher? = null) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val key = "$packageName:${user?.toString() ?: "main"}:$px"
    val bmp by produceState(initialValue = IconCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    (if (user != null && launcher != null) launcher.badgedIcon(packageName, user, px) else null)
                        ?: context.packageManager.getApplicationIcon(packageName).toBitmap(px)
                }.getOrNull()?.also { IconCache.put(key, it) }
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    requireTyping: String? = null,
) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                if (requireTyping != null) {
                    Text(stringResource(R.string.type_to_confirm, requireTyping), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = requireTyping == null || typed.trim() == requireTyping) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, hint: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it.take(50) }, singleLine = true, label = { Text(hint) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.label_only_in_clonapps), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun AppTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

