import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.net.URI
import java.io.File
import java.security.MessageDigest
import java.util.Properties
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
val engineLock = Properties().apply {
    rootProject.file("engine.lock").inputStream().use { load(it) }
}
val engineVersion = engineLock.getProperty("version")
val engineResourceDirectory = layout.buildDirectory.dir("generated/bundledEngine/res")
val prepareBundledEngine by tasks.registering {
    inputs.file(rootProject.file("engine.lock"))
    outputs.file(engineResourceDirectory.map { it.file("raw/ytdlp") })
    outputs.cacheIf { true }
    doLast {
        val output = engineResourceDirectory.get().file("raw/ytdlp").asFile
        val expectedHash = engineLock.getProperty("sha256")
        fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        if (!output.isFile || digest(output) != expectedHash) {
            output.parentFile.mkdirs()
            val partial = File(output.path + ".download")
            try {
                val connection = URI(engineLock.getProperty("url")).toURL().openConnection().apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Vidbox-build/1.0")
                }
                connection.getInputStream().use { input -> partial.outputStream().use { input.copyTo(it, 65536) } }
                check(digest(partial) == expectedHash) { "Pinned yt-dlp checksum mismatch; refusing to package the engine" }
                Files.move(partial.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { partial.delete() }
        }
    }
}

val nativeLock = Properties().apply {
    rootProject.file("native-deps.lock").inputStream().use { load(it) }
}
val upstreamFfmpeg by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val nativeResourceDirectory = layout.buildDirectory.dir("generated/nativeMedia/jniLibs")
val prepareNativeMedia by tasks.registering(Exec::class) {
    inputs.files(upstreamFfmpeg)
    inputs.files(rootProject.file("native-deps.lock"), rootProject.file("scripts/prepare-native-media.py"), file("src/main/cpp/CMakeLists.txt"))
    outputs.dir(nativeResourceDirectory)
    outputs.cacheIf { true }
    workingDir(rootProject.projectDir)
    doFirst {
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: local.getProperty("sdk.dir")
            ?: error("Set ANDROID_HOME or sdk.dir in local.properties")
        commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
            rootProject.file("scripts/prepare-native-media.py").absolutePath,
            "--root", rootProject.projectDir.absolutePath, "--aar", upstreamFfmpeg.singleFile.absolutePath,
            "--work", layout.buildDirectory.dir("nativeMediaWork").get().asFile.absolutePath,
            "--output", nativeResourceDirectory.get().asFile.absolutePath, "--sdk", sdk)
    }
}

android {
    namespace = "com.vidbox.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "MEDIA_RUNTIME_VERSION", "\"${libs.versions.ytdlp.get()}\"")
        buildConfigField("String", "MEDIA_ENGINE_VERSION", "\"$engineVersion\"")
        buildConfigField("String", "NATIVE_PACKAGE_VERSION", "\"${nativeLock.getProperty("webp.version")}:${nativeLock.getProperty("package.revision")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" } }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    // Higher-priority app/library resources override the AAR's older bundled raw/ytdlp zipapp.
    sourceSets["main"].res.srcDir(engineResourceDirectory)
    sourceSets["main"].jniLibs.srcDir(nativeResourceDirectory)
}
tasks.named("preBuild") { dependsOn(prepareBundledEngine, prepareNativeMedia) }
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":domain"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.documentfile)
    implementation(libs.okhttp)
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation(libs.ytdlp)
    add(upstreamFfmpeg.name, libs.ffmpeg)
    constraints {
        implementation("commons-io:commons-io:2.19.0")
        implementation("org.apache.commons:commons-compress:1.27.1")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
        implementation("com.fasterxml.jackson.core:jackson-core:2.19.2")
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.2")
    }
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.android.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mock)
    testImplementation(libs.okhttp.tls)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.okhttp.mock)
    androidTestImplementation(libs.okhttp.tls)
}
