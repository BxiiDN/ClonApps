package com.clonapps

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.clonapps.core.store.ImportResult
import com.clonapps.core.store.ThemeMode
import com.clonapps.domain.CommandHost
import com.clonapps.ui.ClonAppsRoot
import com.clonapps.ui.RootActions
import com.clonapps.ui.screens.ProfileSideScreen
import com.clonapps.ui.theme.ClonAppsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity(), CommandHost {
    private val app get() = application as ClonAppsApp

    private val commandLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        app.coordinator.channel.onResult(r.resultCode, r.data)
    }
    private val provisioningLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        app.coordinator.onProvisioningResult(r.resultCode)
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeText(uri, pendingText ?: return@registerForActivityResult)
    }
    private val saveTextLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) writeText(uri, pendingText ?: return@registerForActivityResult)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().take(2 * 1024 * 1024).toByteArray().toString(Charsets.UTF_8) } }.getOrNull()
            }
            val msg = when (val r = text?.let { app.coordinator.importSettings(it) }) {
                is ImportResult.Ok -> getString(R.string.msg_import_ok, r.export.clones.size, r.skipped)
                is ImportResult.Invalid -> getString(R.string.msg_import_invalid, r.reason)
                null -> getString(R.string.msg_import_unreadable)
            }
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
    private val credentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) unlocked.value = true
    }

    private var pendingText: String? = null
    private val unlocked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        unlocked.value = savedInstanceState?.getBoolean(KEY_UNLOCKED) ?: false
        if (app.isProfileSide) {
            setContent { ClonAppsTheme(ThemeMode.SYSTEM) { ProfileSideScreen() } }
            return
        }
        val c = app.coordinator
        val actions = object : RootActions {
            override val deviceSecure: Boolean get() = getSystemService(KeyguardManager::class.java).isDeviceSecure
            override fun createProfile() = c.startProvisioning(this@MainActivity)
            override fun exportSettings() {
                pendingText = c.exportSettings(versionName())
                exportLauncher.launch("clonapps-settings.json")
            }
            override fun importSettings() = importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            override fun shareText(text: String, fileName: String) = share(text, fileName)
            override fun saveText(text: String, fileName: String) {
                pendingText = text
                saveTextLauncher.launch(fileName)
            }
            override fun openSystemSettings() {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
        setContent {
            val settings by c.settings.collectAsState()
            ClonAppsTheme(settings.themeMode) {
                if (settings.dashboardLock && !unlocked.value) {
                    LockScreen(onUnlock = ::authenticate)
                } else {
                    ClonAppsRoot(c, actions)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (app.isProfileSide) return
        // Kunci tampilan kembali bila ClonApps lama di latar belakang.
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) unlocked.value = false
        app.coordinator.channel.host.value = this
        app.coordinator.refreshProfile()
        app.coordinator.onHostAttached()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        if (!app.isProfileSide && app.coordinator.channel.host.value === this) app.coordinator.channel.host.value = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
    }

    override fun launchCommand(intent: Intent) = commandLauncher.launch(intent)
    override fun launchProvisioning(intent: Intent) = provisioningLauncher.launch(intent)

    private fun versionName(): String = runCatching { com.clonapps.platform.Pm.packageInfo(packageManager, packageName, 0).versionName }.getOrNull() ?: "?"

    private fun writeText(uri: android.net.Uri, text: String) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
            }
            Toast.makeText(this@MainActivity, if (ok) R.string.msg_saved else R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
            pendingText = null
        }
    }

    private fun share(text: String, fileName: String) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                val f = File(dir, fileName)
                f.writeText(text)
                FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, fileName)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, getString(R.string.action_share_report)))
        }
    }

    /** Kunci tampilan ClonApps memakai kredensial perangkat/biometrik sistem. */
    private fun authenticate() {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) { unlocked.value = true; return }
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.lock_title))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { unlocked.value = true }
            })
        } else if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            val prompt = BiometricPrompt.Builder(this).setTitle(getString(R.string.lock_title)).setDeviceCredentialAllowed(true).build()
            prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { unlocked.value = true }
            })
        } else {
            @Suppress("DEPRECATION")
            val intent = km.createConfirmDeviceCredentialIntent(getString(R.string.lock_title), null)
            if (intent != null) credentialLauncher.launch(intent) else unlocked.value = true
        }
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"
        private const val RELOCK_AFTER_MS = 60_000L
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.settings_lock_scope), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onUnlock) { Text(stringResource(R.string.action_unlock)) }
        }
    }
}
