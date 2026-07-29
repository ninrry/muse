plugins {
    id("muse.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "luzzr.muse.ui"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:designsystem"))
    api(project(":core:model"))
    api(project(":core:domain"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.compose.material.icons.extended)

    testImplementation(libs.junit)
}
