// Top-level build file for OPPO Watch SE AI Chat
// AGP 8.2 + Kotlin 1.9.22 | Wear OS / Android

plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

// Shared SDK versions — subprojects reference via rootProject.extra["compileSdk"] etc.
extra.apply {
    set("compileSdk", 34)
    set("minSdk", 26)
    set("targetSdk", 34)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
