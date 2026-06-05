// App module — OPPO Watch SE AI Chat
// AGP 8.2.0 | Kotlin 1.9.22 | Compose Compiler 1.5.8

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")          // Room annotation processor
}

android {
    namespace = "com.oppowatch.aichat"

    // SDK versions from root project extras
    compileSdk = (rootProject.extra["compileSdk"] as Int)   // 34

    defaultConfig {
        applicationId = "com.oppowatch.aichat"
        minSdk = (rootProject.extra["minSdk"] as Int)        // 26
        targetSdk = (rootProject.extra["targetSdk"] as Int)  // 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export — keep under app module
        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // ── Compose ──────────────────────────────────────────────
    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compose Compiler 1.5.8 → Kotlin 1.9.22
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // ── Java / Kotlin targets ───────────────────────────────
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // ── Packaging exclusions ────────────────────────────────
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ── Dependencies ────────────────────────────────────────────

dependencies {
    // Compose BOM — single version control for all Compose artifacts
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Activity & Lifecycle (Compose integration)
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // ── Room ─────────────────────────────────────────────────
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // ── OkHttp ───────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── Retrofit + Gson ──────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // ── Kotlin Coroutines ────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── Core KTX ─────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.12.0")

    // ── Testing ──────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
