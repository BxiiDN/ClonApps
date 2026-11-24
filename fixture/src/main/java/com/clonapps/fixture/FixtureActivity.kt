package com.clonapps.fixture

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * Layar uji. Setiap tombol memanggil API sistem yang nyata dan menulis hasilnya ke layar serta logcat
 * (tag "ClonAppsFixture") agar dapat diperiksa lewat ADB di profil utama maupun profil kerja.
 */
class FixtureActivity : Activity() {
    private lateinit var store: FixtureStore
    private lateinit var status: TextView
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FixtureStore(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { setTextIsSelectable(true) }
        result = TextView(this).apply { setTextIsSelectable(true) }
        val note = EditText(this).apply { setText(store.note); hint = "Catatan (disimpan di data aplikasi)" }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(note)
            addView(button("Simpan catatan") { store.note = note.text.toString(); report("note saved") })
            addView(button("Tambah penghitung") { store.counter = store.counter + 1; report("counter=${store.counter}") })
            addView(button("Kirim notifikasi") { notifyTest() })
            addView(button("Pilih file") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_FILE) })
            addView(button("Kamera") { runCatching { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CAMERA) }.onFailure { report("camera: ${it.javaClass.simpleName}") } })
            addView(button("Buka tautan") { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com/work/managed-profiles"))) }.onFailure { report("link: ${it.javaClass.simpleName}") } })
            addView(button("Bagikan teks") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "ClonApps fixture ${store.instanceId}"), "Bagikan")) })
            addView(button("Jadwalkan job latar belakang") { report("job schedule=" + (FixtureJobService.schedule(this@FixtureActivity) == JobScheduler.RESULT_SUCCESS)) })
            addView(result)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val bg = store.lastBackgroundRun.takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "belum"
        val text = """
            ClonApps Fixture
            user=${Process.myUserHandle()}
            instance=${store.instanceId}
            counter=${store.counter}
            native=${NativeBridge.describe()}
            lastBackgroundRun=$bg
            abi=${Build.SUPPORTED_ABIS.joinToString()}
        """.trimIndent()
        status.text = text
        Log.i(TAG, text.replace('\n', ' '))
    }

    private fun notifyTest() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("fixture", "Fixture", NotificationManager.IMPORTANCE_DEFAULT))
        val n = android.app.Notification.Builder(this, "fixture")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ClonApps fixture")
            .setContentText("user=${Process.myUserHandle()} counter=${store.counter}")
            .build()
        nm.notify(1, n)
        report("notification posted, enabled=${nm.areNotificationsEnabled()}")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) notifyTest() else report("notification permission denied")
        }
    }

    @Deprecated("Activity klasik tanpa AndroidX; cukup untuk fixture.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_FILE -> {
                val uri = data?.data
                val name = uri?.let { u -> contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }
                report("file picker result=$resultCode name=${name ?: "-"}")
            }
            REQ_CAMERA -> {
                @Suppress("DEPRECATION")
                val bmp = data?.extras?.get("data") as? Bitmap
                report("camera result=$resultCode thumbnail=${bmp?.let { "${it.width}x${it.height}" } ?: "-"}")
            }
        }
    }

    private fun report(msg: String) {
        Log.i(TAG, msg)
        result.text = msg
        refresh()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply { text = label; setOnClickListener { onClick() } }

    companion object {
        const val TAG = "ClonAppsFixture"
        private const val REQ_FILE = 1
        private const val REQ_CAMERA = 2
        private const val REQ_NOTIF = 3
    }
}
