import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("local.properties")
val hasKeystoreProperties = if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
    listOf(
        "release.storeFile",
        "release.storePassword",
        "release.keyAlias",
        "release.keyPassword",
    ).all { keystoreProperties.getProperty(it)?.isNotBlank() == true }
} else {
    false
}

android {
    namespace = "com.ftvrcm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ftvrcm"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (hasKeystoreProperties) {
                storeFile = rootProject.file(keystoreProperties.getProperty("release.storeFile"))
                storePassword = keystoreProperties.getProperty("release.storePassword")
                keyAlias = keystoreProperties.getProperty("release.keyAlias")
                keyPassword = keystoreProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
