package com.clonapps.fixture

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Data milik fixture. Penanda instance dibuat sekali per instalasi per profil: bila ClonApps benar,
 * instance di profil utama dan di profil kerja selalu berbeda, dan penghitung tidak saling memengaruhi.
 */
class FixtureStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val noteFile = File(context.filesDir, "note.txt")

    val instanceId: String
        get() = prefs.getString("instance_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("instance_id", it).commit() }

    var counter: Int
        get() = prefs.getInt("counter", 0)
        set(v) { prefs.edit().putInt("counter", v).commit() }

    var note: String
        get() = if (noteFile.exists()) noteFile.readText() else ""
        set(v) = noteFile.writeText(v)

    val lastBackgroundRun: Long get() = prefs.getLong("last_background_run", 0L)

    companion object { const val PREFS = "fixture" }
}
