plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":pulse-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "io.pulsekit.plugin"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
