buildscript {
    // AGP's built-in Kotlin compiles with the Kotlin Gradle plugin on this classpath.
    dependencies { classpath(libs.kotlin.gradle) }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
}
