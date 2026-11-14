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

        buildConfigField(
            "String",
            "TRAINING_DISCOVERY_URL",
            "\"https://wireless-packages-paul-tooth.trycloudflare.com/connection.json\"",
        )

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
        // local.properties is machine-generated and may contain Windows paths.
        disable += "PropertyEscape"
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

    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation(
        "junit:junit:4.13.2"
    )

    androidTestImplementation(
        "androidx.test.ext:junit:1.3.0"
    )

    androidTestImplementation(
        "androidx.test:core:1.7.0"
    )

// The local Qwen runtime is compiled from a pinned llama.cpp revision and packaged only
// for arm64-v8a. Disable with -PwithLocalLlm=false for deterministic fallback-only builds.
val withLocalLlm = providers.gradleProperty("withLocalLlm").orNull?.toBoolean() != false
val generatedLlmJniLibs = layout.buildDirectory.dir("generated/jniLibs/localLlm")
sourceSets.getByName("main").jniLibs.srcDir(generatedLlmJniLibs)
val buildLocalLlmRuntime by tasks.registering(Exec::class) {
    val outputDir = generatedLlmJniLibs.get().asFile
    outputs.dir(outputDir)
    onlyIf { withLocalLlm }
    commandLine(
        "python3",
        rootProject.file("../../tools/g620/build_local_llm_android.py").absolutePath,
        "--work-dir", layout.buildDirectory.dir("local-llm").get().asFile.absolutePath,
        "--ndk", android.ndkDirectory.absolutePath,
        "--output", outputDir.absolutePath,
    )
}
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(buildLocalLlmRuntime)
    }
}

}
