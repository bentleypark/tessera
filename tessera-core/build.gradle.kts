plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

group = "com.github.bentleypark.tessera"
version = "0.1.0"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                }
            }
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "TesseraCore"
            isStatic = true
        }
    }

    // Desktop target (Phase 4)
    // jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(libs.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.robolectric)
                implementation(libs.timber)
            }
        }

        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.androidx.core)
            implementation(libs.compose.ui.tooling.preview)
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}
