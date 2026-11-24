// AGP 9 memakai Kotlin bawaan (built-in Kotlin). Plugin kotlin-jvm dideklarasikan di sini agar
// modul :core dan AGP memakai versi Kotlin Gradle Plugin yang sama.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
