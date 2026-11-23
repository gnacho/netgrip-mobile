import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    // AGP 9 incluye Kotlin integrado: no se aplica org.jetbrains.kotlin.android
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val releaseSigningPropertiesFile = System.getenv("NETGRIP_SIGNING_PROPERTIES")
    ?.takeIf { it.isNotBlank() }
    ?.let { File(it) }
    ?: rootProject.file("keystore.properties")
val releaseSigningProperties = Properties()
if (releaseSigningPropertiesFile.exists()) {
    releaseSigningPropertiesFile.inputStream().use { releaseSigningProperties.load(it) }
}

android {
    namespace = "dev.gnacho.netgrip"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.gnacho.netgrip"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.1.6"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningPropertiesFile.exists()) {
                storeFile = File(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    dependencies {
        implementation(project(":shared"))
        implementation(libs.androidx.activity.compose)
    }
}
