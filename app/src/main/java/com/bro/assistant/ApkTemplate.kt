package com.bro.assistant

/**
 * A tiny Android project that wraps an HTML app in a WebView. Pushed to GitHub together with a
 * build workflow, it turns any single-file HTML app into a real installable APK.
 */
object ApkTemplate {

    fun packageFor(repoName: String): String {
        val clean = repoName.substringAfter('/').lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "app" }
        return "com.bro.apps.a$clean"
    }

    /** Path -> content. The workflow file comes last so one build runs with everything in place. */
    fun files(repoName: String, html: String): LinkedHashMap<String, String> {
        val name = repoName.substringAfter('/')
        val pkg = packageFor(repoName)
        fun fill(t: String) = t.replace("__NAME__", name.replace("&", "and").replace("<", "").replace("\"", ""))
            .replace("__PKG__", pkg)
        val map = LinkedHashMap<String, String>()
        map["settings.gradle.kts"] = fill(SETTINGS)
        map["build.gradle.kts"] = ROOT
        map["gradle.properties"] = PROPS
        map[".gitignore"] = "build/\n.gradle/\nlocal.properties\n"
        map["app/build.gradle.kts"] = fill(APP)
        map["app/src/main/AndroidManifest.xml"] = fill(MANIFEST)
        map["app/src/main/java/" + pkg.replace('.', '/') + "/MainActivity.kt"] = fill(ACTIVITY)
        map["app/src/main/assets/index.html"] = html
        map[".github/workflows/build.yml"] = fill(WORKFLOW)
        return map
    }

    private val SETTINGS = """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WebApp"
include(":app")
""".trimStart()

    private val ROOT = """
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
""".trimStart()

    private val PROPS = """
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
""".trimStart()

    private val APP = """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "__PKG__"
    compileSdk = 35

    defaultConfig {
        applicationId = "__PKG__"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    implementation("androidx.core:core-ktx:1.15.0")
}
""".trimStart()

    private val MANIFEST = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:label="__NAME__"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimStart()

    private val ACTIVITY = """
package __PKG__

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.fitsSystemWindows = true
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
""".trimStart()

    private val WORKFLOW = """
name: Build APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.9

      - name: Build debug APK
        run: gradle :app:assembleDebug --stacktrace

      - uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: app/build/outputs/apk/debug/*.apk
""".trimStart()
}
