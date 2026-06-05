plugins {
    id("muse.android.library")
}

android {
    namespace = "luzzr.muse.network"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
}
