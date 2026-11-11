plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.slicoo.code_droid.feature.agent"
    compileSdk = 37
    defaultConfig { minSdk = 26 }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:ui"))
}
