plugins {
    id("muse.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "luzzr.muse.designsystem"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)

    implementation(libs.androidx.core.ktx)
}
