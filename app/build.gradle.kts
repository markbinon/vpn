plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Данные прокси берутся из переменных окружения (GitHub Secrets) или из -PNAME=value.
fun prop(name: String, def: String = ""): String =
    System.getenv(name) ?: (project.findProperty(name) as String?) ?: def

fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

val ksFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.myvpn.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myvpn.app"
        minSdk = 24
        // 33, чтобы не требовать тип foreground-сервиса (Android 14). Для установки APK вручную это не мешает.
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "PROXY_HOST", "\"${esc(prop("PROXY_HOST"))}\"")
        buildConfigField("int", "PROXY_PORT", "${prop("PROXY_PORT", "0").toIntOrNull() ?: 0}")
        buildConfigField("String", "PROXY_USER", "\"${esc(prop("PROXY_USER"))}\"")
        buildConfigField("String", "PROXY_PASS", "\"${esc(prop("PROXY_PASS"))}\"")

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildFeatures { buildConfig = true }

    externalNativeBuild {
        ndkBuild { path = file("src/main/jni/Android.mk") }
    }

    signingConfigs {
        if (ksFile != null) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
