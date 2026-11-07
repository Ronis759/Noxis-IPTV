plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val keystorePath: String = System.getenv("KEYSTORE_FILE").orEmpty()
val hasReleaseKey: Boolean = keystorePath.isNotEmpty() && file(keystorePath).exists() &&
    !System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty() &&
    !System.getenv("KEY_ALIAS").isNullOrEmpty() &&
    !System.getenv("KEY_PASSWORD").isNullOrEmpty()

android {
    namespace = "app.iptv.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.iptv.tv"
        minSdk = 21
        targetSdk = 35
        // В GitHub Actions versionCode растёт с каждым запуском, локально = 1
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.9.0"
        // Автообновление имеет смысл только у сборки, подписанной постоянным ключом (иначе Android не установит новую версию поверх)
        buildConfigField("boolean", "UPDATES_ENABLED", hasReleaseKey.toString())
    }
    buildFeatures { buildConfig = true }
    // Подпись релиза: ключ и пароли берутся из переменных окружения (в GitHub Actions — из Secrets).
    // Без них (локальная сборка) APK подписывается обычным отладочным ключом Android.
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}
dependencies {
    // Media3 1.9+ требует minSdk 23 (Android 6): на 1.8.x остаются поддерживаемыми приставки с Android 5 (minSdk 21)
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    testImplementation("junit:junit:4.13.2")
}
