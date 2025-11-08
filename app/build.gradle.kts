plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    id("kotlin-parcelize")
}

android {
    namespace = "cafe.oeee"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "cafe.oeee"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Image loading
    implementation(libs.coil)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Security
    implementation(libs.androidx.security.crypto)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}