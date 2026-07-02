// Root build file — declares plugins for subprojects without applying them here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// Publishable coordinates for every module (io.pulsekit:<module>:<version>).
// Enables consuming PulseKit from another project via a Gradle composite build
// or `publishToMavenLocal`. See docs/INTEGRATION.md.
subprojects {
    group = "io.pulsekit"
    version = "0.1.0"
}
