plugins {
    kotlin("multiplatform") version "1.9.20"
}

kotlin {
    jvm()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.bouncycastle:bcprov-jdk18on:1.77")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
