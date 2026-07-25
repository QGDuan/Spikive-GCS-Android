import java.io.File
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val webProjectDirectory = rootProject.layout.projectDirectory.dir("webProject/Spikive-Lichtblick")
val webBuildDirectory = webProjectDirectory.dir("web/.webpack")
val debugWebAssetsDirectory = layout.buildDirectory.dir("generated/spikiveWebAssets/debug").get().asFile
val releaseWebAssetsDirectory = layout.buildDirectory.dir("generated/spikiveWebAssets/release").get().asFile
val reuseExistingWebAssets = providers.gradleProperty("spikiveReuseWebAssets")
    .map(String::toBoolean)
    .orElse(false)
val defaultCorepackExecutable = run {
    val executableName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "corepack.cmd"
    } else {
        "corepack"
    }
    val nvmBinCandidate = System.getenv("NVM_BIN")?.let { File(it, executableName) }
    val nvmVersionsDirectory = File(System.getProperty("user.home"), ".nvm/versions/node")
    val installedNvmCandidate = nvmVersionsDirectory.listFiles()
        ?.asSequence()
        ?.map { File(it, "bin/$executableName") }
        ?.filter(File::isFile)
        ?.maxByOrNull(File::lastModified)
    sequenceOf(
        nvmBinCandidate,
        installedNvmCandidate,
        File("/opt/homebrew/bin/$executableName"),
        File("/usr/local/bin/$executableName"),
    ).filterNotNull().firstOrNull(File::isFile)?.absolutePath ?: executableName
}
val corepackExecutable = providers.gradleProperty("spikiveCorepackExecutable")
    .orElse(defaultCorepackExecutable)

val installSpikiveWebDependencies by tasks.registering(Exec::class) {
    group = "spikive"
    description = "Installs the locked Lichtblick dependencies without desktop browser binaries."
    workingDir(webProjectDirectory.asFile)
    commandLine(corepackExecutable.get(), "yarn", "install", "--immutable")
    environment("YARN_IGNORE_PATH", "1")
    environment("YARN_ENABLE_PROGRESS_BARS", "false")
    environment("ELECTRON_SKIP_BINARY_DOWNLOAD", "1")
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
    inputs.file(webProjectDirectory.file("package.json"))
    inputs.file(webProjectDirectory.file("yarn.lock"))
    inputs.file(webProjectDirectory.file(".yarnrc.yml"))
    inputs.dir(webProjectDirectory.dir("patches"))
    inputs.files(
        webProjectDirectory.asFileTree.matching {
            include("desktop/package.json")
            include("web/package.json")
            include("benchmark/package.json")
            include("packages/*/package.json")
            include("packages/@types/*/package.json")
        },
    )
}

val buildSpikiveWeb by tasks.registering(Exec::class) {
    group = "spikive"
    description = "Builds the Spikive-Lichtblick production web bundle."
    dependsOn(installSpikiveWebDependencies)
    workingDir(webProjectDirectory.asFile)
    commandLine(corepackExecutable.get(), "yarn", "web:build:prod")
    environment("YARN_IGNORE_PATH", "1")
    inputs.file(webProjectDirectory.file("package.json"))
    inputs.file(webProjectDirectory.file("yarn.lock"))
    inputs.file(webProjectDirectory.file("web/webpack.config.ts"))
    inputs.files(
        webProjectDirectory.asFileTree.matching {
            include("babel.config.json")
            include("tsconfig.json")
            include("web/src/**")
            include("packages/*/src/**")
            include("packages/@types/*/src/**")
        },
    )
    outputs.dir(webBuildDirectory)
}

val syncDebugSpikiveWebAssets by tasks.registering(Sync::class) {
    group = "spikive"
    description = "Synchronizes the web bundle into debug APK assets."
    if (!reuseExistingWebAssets.get()) {
        dependsOn(buildSpikiveWeb)
    }
    inputs.file(webBuildDirectory.file("index.html"))
    from(webBuildDirectory)
    into(File(debugWebAssetsDirectory, "web"))
}

val syncReleaseSpikiveWebAssets by tasks.registering(Sync::class) {
    group = "spikive"
    description = "Builds and synchronizes the web bundle into release APK assets."
    dependsOn(buildSpikiveWeb)
    inputs.file(webBuildDirectory.file("index.html"))
    from(webBuildDirectory)
    into(File(releaseWebAssetsDirectory, "web"))
}

android {
    namespace = "com.example.spikivegcs"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.spikivegcs"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("debug").assets.srcDir(debugWebAssetsDirectory)
        getByName("release").assets.srcDir(releaseWebAssetsDirectory)
    }
}

tasks.matching { it.name == "mergeDebugAssets" }.configureEach {
    dependsOn(syncDebugSpikiveWebAssets)
}

tasks.matching { it.name == "mergeReleaseAssets" }.configureEach {
    dependsOn(syncReleaseSpikiveWebAssets)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
