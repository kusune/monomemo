plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = System.getenv("MONOMEMO_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val releaseStorePassword = System.getenv("MONOMEMO_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("MONOMEMO_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    ?: "monomemo-release"
val releaseKeyPassword = System.getenv("MONOMEMO_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: releaseStorePassword
val releaseSigningConfigured = releaseKeystorePath != null &&
    releaseStorePassword != null &&
    releaseKeyPassword != null

android {
    namespace = "io.github.kusune.monomemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.kusune.monomemo"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("monomemoRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = releaseKeyAlias
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("monomemoRelease")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
