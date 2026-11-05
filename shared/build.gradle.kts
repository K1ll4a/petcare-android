import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "PetCareShared"
            isStatic = true
            binaryOption("bundleId", "com.petcare.shared")
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(libs.multiplatform.lifecycle.runtime)
            api(libs.multiplatform.lifecycle.viewmodel)
            api(libs.datetime)
            api(libs.kotlinx.serialization.json)
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            api("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.1.3")
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.petcare.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
