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
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        jniLibs {
            // Python/FFmpeg/QuickJS executables must exist in nativeLibraryDir on Android 10+.
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
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
    implementation(libs.webkit)
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
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
    androidTestImplementation(libs.serialization.json)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.test.manifest)
}
