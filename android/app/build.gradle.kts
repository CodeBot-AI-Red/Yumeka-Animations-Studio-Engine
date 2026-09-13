plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.yumeka.anime.engine"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yumeka.anime.engine"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
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
        viewBinding = true
    }
}

dependencies {
    // Android core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx.coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx.coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Rendering 2D - Animação anime
    implementation("com.github.airbnb:lottie-android:6.3.0")

    // Carregamento de imagens / sprites
    implementation("io.coil-kt:coil:2.5.0")

    // Rede - streaming de animações remotas
    implementation("com.squareup.okhttp:okhttp:4.12.0")

    // Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
}
