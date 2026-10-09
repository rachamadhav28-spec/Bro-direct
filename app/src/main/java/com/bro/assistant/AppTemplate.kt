package com.bro.assistant

/** Known-good Gradle/Android scaffolding for apps that BRO generates. Only the Kotlin code varies. */
object AppTemplate {

    const val SOURCE_DIR = "app/src/main/java/com/bro/app/"
    const val WORKFLOW_PATH = ".github/workflows/build.yml"

    fun slug(text: String): String {
        val s = text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        return if (s.length >= 3) s else "bro-app"
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("'", "\\'").replace("\"", "\\\"")

    /** Every non-Kotlin file of the project, workflow last so only one build starts. */
    fun files(appName: String, slug: String): List<Pair<String, String>> {
        val id = slug.replace("-", "")
        return listOf(
            "settings.gradle.kts" to SETTINGS,
            "build.gradle.kts" to ROOT_BUILD,
            "gradle.properties" to PROPERTIES,
            ".gitignore" to GITIGNORE,
            "app/build.gradle.kts" to APP_BUILD.replace("APPLICATION_ID", "com.bro.app.$id"),
            "app/src/main/AndroidManifest.xml" to MANIFEST,
            "app/src/main/res/values/strings.xml" to
                "<resources>\n    <string name=\"app_name\">${xml(appName)}</string>\n</resources>\n",
            WORKFLOW_PATH to WORKFLOW
        )
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

        rootProject.name = "GeneratedApp"
        include(":app")
    """.trimIndent() + "\n"

    private val ROOT_BUILD = """
        plugins {
            id("com.android.application") version "8.7.3" apply false
            id("org.jetbrains.kotlin.android") version "2.0.21" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
        }
    """.trimIndent() + "\n"

    private val PROPERTIES = """
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        android.useAndroidX=true
        android.nonTransitiveRClass=true
        kotlin.code.style=official
    """.trimIndent() + "\n"

    private val GITIGNORE = """
        *.iml
        .gradle/
        /local.properties
        /.idea/
        .DS_Store
        /build/
        /app/build/
        /captures/
    """.trimIndent() + "\n"

    private val APP_BUILD = """
        plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
            id("org.jetbrains.kotlin.plugin.compose")
        }

        android {
            namespace = "com.bro.app"
            compileSdk = 35

            defaultConfig {
                applicationId = "APPLICATION_ID"
                minSdk = 26
                targetSdk = 35
                versionCode = 1
                versionName = "1.0"
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
                compose = true
            }
        }

        dependencies {
            implementation("androidx.core:core-ktx:1.15.0")
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

            implementation(platform("androidx.compose:compose-bom:2024.10.01"))
            implementation("androidx.compose.ui:ui")
            implementation("androidx.compose.ui:ui-graphics")
            implementation("androidx.compose.foundation:foundation")
            implementation("androidx.compose.animation:animation")
            implementation("androidx.compose.material3:material3")
        }
    """.trimIndent() + "\n"

    private val MANIFEST = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

            <application
                android:allowBackup="true"
                android:icon="@android:drawable/sym_def_app_icon"
                android:label="@string/app_name"
                android:supportsRtl="true"
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
    """.trimIndent() + "\n"

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
    """.trimIndent() + "\n"
}
