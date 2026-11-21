import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 incluye Kotlin integrado: no se aplica org.jetbrains.kotlin.android
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "dev.gnacho.netgrip"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.gnacho.netgrip"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
