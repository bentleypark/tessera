plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                }
            }
        }
    }

    // iOS targets — compile-only for now, no iosMain implementations yet (Phase 3)
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Desktop target (Phase 4)
    // jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.ui)
            implementation(libs.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }

        androidMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.runtime)
            implementation(libs.coroutines.android)
            implementation(libs.androidx.core)
            implementation(libs.compose.ui.tooling.preview)
            compileOnly(libs.glide)
            compileOnly(libs.coil)
            compileOnly(libs.timber)
        }
    }
}

android {
    namespace = "com.naemomlab.tessera"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
