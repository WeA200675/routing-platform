buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        // AGP 9 uses built-in Kotlin. Declaring a newer KGP classpath
        // upgrades the Kotlin compiler used by built-in Kotlin without
        // applying the incompatible kotlin-android plugin.
        classpath(
            "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21"
        )
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
