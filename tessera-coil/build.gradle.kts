plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
            baseName = "TesseraCoil"
            isStatic = true
            export(project(":tessera-core"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":tessera-core"))
            implementation(libs.coil.core)
            implementation(libs.coil.ktor)
            implementation(libs.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            compileOnly(libs.timber)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.naemomlab.tessera.coil"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
