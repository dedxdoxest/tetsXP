plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Криптография
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                // Для Argon2id, AES-GCM, XChaCha20-Poly1305 будем использовать expect/actual
                // В commonOnly - интерфейсы и модели
            }
        }
        val androidMain by getting {
            dependencies {
                // Реализация криптографии для Android
                implementation("com.github.oshai:kotlin-logging-jvm:5.1.0")
            }
        }
    }
}

android {
    namespace = "app.hp.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
