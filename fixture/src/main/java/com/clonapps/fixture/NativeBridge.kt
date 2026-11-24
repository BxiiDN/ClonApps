package com.clonapps.fixture

object NativeBridge {
    val loaded: Result<Unit> = runCatching { System.loadLibrary("fixture_native") }

    @JvmStatic
    external fun hello(): String

    fun describe(): String = loaded.fold({ runCatching { hello() }.getOrElse { "JNI error: ${it.message}" } }, { "loadLibrary gagal: ${it.message}" })
}
