plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.0+ ships the Compose compiler as a standalone Gradle plugin.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.implantdoom"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.implantdoom"
        // Minimum SDK 26 (Android 8.0): modern enough for adaptive icons, the
        // full NFC tech stack and Jetpack Compose, while still covering the vast
        // majority of devices.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    // Kotlin sources live under src/main/kotlin and src/test/kotlin.
    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose: the Bill of Materials keeps all Compose artifacts on compatible versions.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Local JVM unit tests (cartridge codec, CRC, nibble packing, validation).
    testImplementation(libs.junit)
}
