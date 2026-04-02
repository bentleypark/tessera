import com.vanniktech.maven.publish.SonatypeHost
import java.util.Base64

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    id("signing")
}

group = "io.github.bentleypark"
version = libs.versions.tessera.get()

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
    namespace = "com.github.bentleypark.tessera.coil"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    coordinates("io.github.bentleypark", "tessera-coil", version.toString())

    pom {
        name.set("tessera-coil")
        description.set("Coil 3.x companion module for Tessera image viewer")
        inceptionYear.set("2025")
        url.set("https://github.com/bentleypark/tessera")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("bentleypark")
                name.set("BentleyPark")
                url.set("https://github.com/bentleypark/")
            }
        }
        scm {
            url.set("https://github.com/bentleypark/tessera")
            connection.set("scm:git:git://github.com/bentleypark/tessera.git")
            developerConnection.set("scm:git:ssh://git@github.com/bentleypark/tessera.git")
        }
    }
}

signing {
    val signingKey = project.findProperty("signing.key") as String?
    val signingPassword = project.findProperty("signing.password") as String?

    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(
            String(Base64.getDecoder().decode(signingKey)),
            signingPassword
        )
        sign(publishing.publications)
    }
}
