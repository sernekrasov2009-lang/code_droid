plugins {
    alias(libs.plugins.android.library)
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
}
