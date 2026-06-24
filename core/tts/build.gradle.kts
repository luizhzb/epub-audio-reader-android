plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.epubaudioreader.core.tts"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        // Modelos ONNX e dados do TTS precisam estar descomprimidos no APK
        // para o AssetManager conseguir le-los a partir do codigo nativo.
        noCompress += listOf(".onnx", ".bin", ".json")
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(libs.bundles.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.coroutines)
    // Sherpa-ONNX: AAR oficial do Maven Central (inclui .so JNI para Android)
    implementation(libs.sherpa.onnx)
}
