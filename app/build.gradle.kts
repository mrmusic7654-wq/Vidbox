import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
val engineLock = Properties().apply {
    rootProject.file("engine.lock").inputStream().use { load(it) }
}
android {
    namespace = "com.vidbox"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vidbox.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "MEDIA_ENGINE_VERSION", "\"${engineLock.getProperty("version")}\"")
        testInstrumentationRunner = "com.vidbox.VidboxTestRunner"
        // 64-bit only: minSdk 29 leaves very few 32-bit-only devices, and every dropped ABI removes a
        // full copy of the FFmpeg/Python/QuickJS runtime payload.
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
    }
    signingConfigs {
        val storePath = providers.environmentVariable("VIDBOX_KEYSTORE").orNull
        if (storePath != null) {
            create("production") {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("VIDBOX_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("VIDBOX_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("VIDBOX_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("production")
        }
    }
    // Per-ABI release APKs (~58 MB each instead of one 108 MB universal): enable with
    //   ./gradlew -Pvidbox.apkSplits=true assembleRelease
    // The flag is opt-in because AGP cannot bundle an AAB while multiple APK outputs exist
    // (b/402800800: resource shrinking + splits make the pre-bundle artifacts ambiguous), and
    // because connected tests and the emulator smoke install expect a single universal debug APK.
    splits {
        abi {
            isEnable = providers.gradleProperty("vidbox.apkSplits").isPresent
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        jniLibs {
            // Python/FFmpeg/QuickJS executables must exist in nativeLibraryDir on Android 10+.
            useLegacyPackaging = true
            // The *.zip.so entries are ZIP archives that only survive packaging untouched; real ELF
            // libraries are stripped by the native-strip step instead of shipping debug symbols.
            keepDebugSymbols += "**/*.zip.so"
            // ffprobe is never executed by Vidbox (ffmpeg doubles as the CLI); drop its ~15 MB/ABI copy.
            excludes += setOf("**/libffprobe.so")
        }
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/AL2.0", "META-INF/LGPL2.1")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.documentfile)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.hilt.android)
    implementation(libs.hilt.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.work)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.coil)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.rules)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.hilt.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.okhttp.mock)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.test.manifest)
}
