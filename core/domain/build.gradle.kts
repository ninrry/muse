plugins {
    id("muse.kotlin.library")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
}
