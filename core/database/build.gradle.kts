plugins {
    id("muse.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "luzzr.muse.database"

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
