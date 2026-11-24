// Aplikasi fixture untuk menguji ClonApps: data per profil, native library, split APK (via bundletool),
// notifikasi, pemilih file, kamera, tautan, berbagi, dan job latar belakang. Tanpa dependensi pihak ketiga.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.clonapps.fixture"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "com.clonapps.fixture"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0-fixture"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
    }
}
