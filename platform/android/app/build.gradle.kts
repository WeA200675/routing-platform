plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.routingplatform.app"

    compileSdk = 36
    buildToolsVersion = "36.0.0"

    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "org.routingplatform.app"

        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path =
                file("src/main/cpp/CMakeLists.txt")

            version =
                "3.22.1"
        }
    }

    packaging {
        resources {
            excludes +=
                "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    val composeBom =
        platform(
            "androidx.compose:compose-bom:2026.06.00"
        )

    implementation(composeBom)

    implementation(
        "androidx.activity:activity-compose:1.13.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.10.0"
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )

    implementation(
        "org.maplibre.gl:android-sdk-opengl:13.4.1"
    )

    testImplementation(
        "junit:junit:4.13.2"
    )
}
