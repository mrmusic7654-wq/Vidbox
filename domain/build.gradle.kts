plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.inject)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
tasks.test { testLogging { events("passed", "skipped", "failed") } }
