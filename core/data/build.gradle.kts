plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.slicoo.code_droid.core.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:llm"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(project(":core:workspace"))
}
