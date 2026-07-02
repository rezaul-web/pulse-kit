plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Directory for the generated PulseKit build-provenance asset (Commit History panel).
val pulseProvenanceDir = layout.buildDirectory.dir("generated/pulse/assets")
val repoRoot = rootDir

android {
    namespace = "io.pulsekit.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.pulsekit.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    // Serve the generated provenance JSON as an app asset.
    sourceSets.getByName("main").assets.srcDir(pulseProvenanceDir)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":pulse-android"))
    implementation(project(":pulse-network"))
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}

/**
 * Capture git/build provenance at build time and write it to an asset that
 * PulseKit reads at runtime for the Commit History panel (ARCHITECTURE.md §6.5).
 * Uses ProcessBuilder (not Gradle exec) so there's no configuration-cache coupling;
 * degrades to an empty, valid record if git is unavailable.
 */
val generatePulseProvenance by tasks.registering {
    val outFile = pulseProvenanceDir.map { it.file("pulsekit_provenance.json") }
    outputs.file(outFile)
    // Git state isn't a declared Gradle input, so without this the task would be
    // cached UP-TO-DATE and the provenance would go stale. Always regenerate.
    outputs.upToDateWhen { false }
    val root = repoRoot
    doLast {
        fun git(vararg args: String): String = try {
            val proc = ProcessBuilder(listOf("git") + args).directory(root).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (proc.exitValue() == 0) out else ""
        } catch (e: Exception) {
            ""
        }
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

        val branch = git("rev-parse", "--abbrev-ref", "HEAD")
        val head = git("rev-parse", "--short", "HEAD")
        val dirty = git("status", "--porcelain").isNotEmpty()
        val commitTimeMs = (git("log", "-1", "--format=%ct").toLongOrNull() ?: 0L) * 1000
        val commits = git("log", "-20", "--format=%h%x1f%an%x1f%ct%x1f%s")
            .split("\n").filter { it.isNotBlank() }.joinToString(",") { line ->
                val p = line.split("\u001F")
                val t = (p.getOrElse(2) { "0" }.toLongOrNull() ?: 0L) * 1000
                """{"sha":"${esc(p.getOrElse(0) { "" })}","author":"${esc(p.getOrElse(1) { "" })}","timeMs":$t,"subject":"${esc(p.getOrElse(3) { "" })}"}"""
            }
        val json = """{"branch":"${esc(branch)}","commit":"${esc(head)}","commitTimeMs":$commitTimeMs,""" +
            """"dirty":$dirty,"buildTimeMs":${System.currentTimeMillis()},"history":[$commits]}"""

        outFile.get().asFile.apply { parentFile.mkdirs(); writeText(json) }
    }
}

// Ensure the asset is generated before it's merged into the APK.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(generatePulseProvenance) }
