import com.vanniktech.maven.publish.SonatypeHost
import java.util.Base64

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.publish)
    id("signing")
}

group = "io.github.bentleypark"
version = libs.versions.tessera.get()

android {
    namespace = "com.github.bentleypark.tessera.glide"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    api(project(":tessera-core"))
    implementation(libs.glide)
    implementation(libs.coroutines.android)
    compileOnly(libs.timber)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    coordinates("io.github.bentleypark", "tessera-glide", version.toString())

    pom {
        name.set("tessera-glide")
        description.set("Glide companion module for Tessera image viewer")
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
