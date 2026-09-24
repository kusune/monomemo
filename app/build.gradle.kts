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
        versionCode = 5
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

dependencies {
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
