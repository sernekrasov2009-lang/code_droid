// Pure Kotlin domain models. No Android dependencies allowed here.
plugins {
    // Built-in Kotlin of AGP 9 puts KGP on the classpath already,
    // so we apply it without a version to avoid a resolution conflict.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
