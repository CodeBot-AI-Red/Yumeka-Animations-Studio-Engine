plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        // Motores de IA nativos sao compilados apenas para ARM64 (android/native-ai).
        ndk { abiFilters += listOf("arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        // Necessario para mmap dos modelos e para alinhamento de 16 KB das .so.
        jniLibs { useLegacyPackaging = false }
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

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Android core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Lottie - animacoes vetoriais
    implementation("com.airbnb.android:lottie:6.3.0")

    // Coil - sprites
    implementation("io.coil-kt:coil:2.5.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testes
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

// Compila stable-diffusion.cpp e llama.cpp (codigo-fonte oficial, versoes em android/native-ai/versions.env)
// para arm64-v8a antes do build. Use -PskipNativeAi=true apenas para builds sem IA local.
val buildNativeAi = tasks.register<Exec>("buildNativeAi") {
    group = "build"
    description = "Compila libyumeka_sd.so e libyumeka_vlm.so a partir do codigo-fonte oficial"
    workingDir = rootProject.projectDir
    commandLine("bash", "android/native-ai/scripts/gradle-native.sh")
    onlyIf { !project.hasProperty("skipNativeAi") }
}
tasks.named("preBuild") { dependsOn(buildNativeAi) }
