plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

@Suppress("DEPRECATION")
android {
    namespace = "com.github.bentleypark.tessera.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.bentleypark.tessera.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":tessera-core"))
    implementation(project(":tessera-coil"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.compose.activity)

    // Logging
    implementation(libs.timber)

    // AndroidX
    implementation(libs.androidx.core)
}
