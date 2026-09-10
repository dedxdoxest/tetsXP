plugins {
    kotlin("multiplatform") version "1.9.20"
    id("com.android.library")
}

kotlin {
    androidTarget()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.bouncycastle:bcprov-jdk18on:1.77")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
            }
        }
    }
}

android {
    namespace = "com.example.hp.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 31
    }
}
