plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.sentry)
}

android {
    namespace = "cafe.oeee"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cafe.oeee"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The site's own Web application OAuth client id, which Credential Manager is
        // given as its server client id so the ID token it hands back is made for the
        // site (GoogleSignIn.kt). Set oeeeGoogleServerClientId in
        // ~/.gradle/gradle.properties; a build without it does not offer signing in with
        // Google, and the site does not show the button (theme_head.jinja in
        // oeee-cafe/web).
        val googleServerClientId = providers.gradleProperty("oeeeGoogleServerClientId").getOrElse("")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
    }

    // Release builds are signed with the upload key when ~/.gradle/gradle.properties says
    // where it is (oeeeUploadStoreFile, oeeeUploadStorePassword, oeeeUploadKeyAlias,
    // oeeeUploadKeyPassword); otherwise they come out unsigned.
    val uploadStoreFile = providers.gradleProperty("oeeeUploadStoreFile").orNull
    val uploadSigning = uploadStoreFile?.let {
        signingConfigs.create("upload") {
            storeFile = file(it)
            storePassword = providers.gradleProperty("oeeeUploadStorePassword").get()
            keyAlias = providers.gradleProperty("oeeeUploadKeyAlias").get()
            keyPassword = providers.gradleProperty("oeeeUploadKeyPassword").get()
        }
    }

    buildTypes {
        release {
            signingConfig = uploadSigning
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Firebase brings in Fragment 1.1, too old for the activity result APIs.
    implementation(libs.androidx.fragment)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Web views
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)
    // Other sites, over the app (Custom Tabs)
    implementation(libs.androidx.browser)

    // The bridge's messages
    implementation(libs.moshi)
    // A drawing fetched for its menu
    implementation(libs.okhttp)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Sign in with Google
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

sentry {
    org.set("limeburst")
    projectName.set("oeee-cafe-android")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)
}
