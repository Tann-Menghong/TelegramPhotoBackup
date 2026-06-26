plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.tgphotobackup"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile     = file("tgbackup.keystore")
            storePassword = "tgbackup123"
            keyAlias      = "tgbackup"
            keyPassword   = "tgbackup123"
        }
    }

    defaultConfig {
        applicationId = "com.example.tgphotobackup"
        minSdk = 26
        targetSdk = 34
        versionCode = 36
        versionName = "1.36"
    }

    buildTypes {
        debug {
            // Same package name as release so debug and release APKs can update each other
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    // WorkManager (background + scheduled backups)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Room (local index DB: which photo -> which Telegram message)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (settings: bot token, chat id)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // OkHttp (Telegram Bot API calls)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coil (photo thumbnails)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Glance (home screen widget)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
}
