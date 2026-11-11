plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.slicoo.code_droid.core.agent"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:llm"))
    api(project(":core:workspace"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
