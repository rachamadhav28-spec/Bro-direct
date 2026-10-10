plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    packaging {
        jniLibs { pickFirsts += setOf("**/libc++_shared.so") }
        resources { excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES") }
    }
    namespace = "com.bro.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bro.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())
    }

    signingConfigs {
        // One fixed key for every build, so a new APK installs over the old app and keeps your settings.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
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

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // on-device language model (runs fully offline once a model file is installed)
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    // LiteRT-LM runs Gemma 4 (.litertlm files)
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
}
