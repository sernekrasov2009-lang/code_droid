plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ru.slicoo.code_droid.core.workspace"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
