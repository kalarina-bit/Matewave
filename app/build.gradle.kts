plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cc.skysparkle.matewave"

    compileSdk = 37

    defaultConfig {
        applicationId = "cc.skysparkle.matewave"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.0.51"
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
