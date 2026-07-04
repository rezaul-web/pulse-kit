plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "PulseKit"
            isStatic = true
            // Expose the pulse-core / pulse-plugin API (Pulse, events, data models) to Swift.
            export(project(":pulse-core"))
            export(project(":pulse-plugin"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":pulse-core"))
            api(project(":pulse-plugin"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.napier)
        }
        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.process)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.pulsekit.runtime"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
