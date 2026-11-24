plugins { `kotlin-dsl` }

repositories { mavenCentral() }

// The app's versionCode rule, shared with app/build.gradle.kts.
sourceSets.main { kotlin.srcDir("../app/src/main/kotlin/app/koma/version") }
