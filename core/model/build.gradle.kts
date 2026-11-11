// Pure Kotlin domain models. No Android dependencies allowed here.
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
