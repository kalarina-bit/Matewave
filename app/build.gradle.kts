plugins {
    id("com.android.application")

    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cc.skysparkle.matewave"

    compileSdk = 37

    defaultConfig {
        applicationId = "cc.skysparkle.matewave"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.48"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Keeps release builds reproducible (F-Droid).
            vcsInfo.include = false
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // The app's languages, plus the regional variants libraries ship for them.
        localeFilters += listOf(
            "en", "en-rAU", "en-rCA", "en-rGB", "en-rIN",
            "ru", "de", "ja", "lt",
            "zh", "zh-rCN", "zh-rHK", "zh-rTW"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // F-Droid rejects the Google-encrypted dependency metadata block in APKs.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }
}

androidComponents {
    // Release APKs leave out files only build and debug tools read: library version markers,
    // Kotlin reflection metadata and the coroutines debug agent.
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.resources.excludes.addAll(
            "/META-INF/*.version",
            "/META-INF/*.kotlin_module",
            "/kotlin/**",
            "/kotlin-tooling-metadata.json",
            "/DebugProbesKt.bin"
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("com.google.zxing:core:3.5.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
