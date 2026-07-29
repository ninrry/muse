plugins {
    `kotlin-dsl`
}

group = "luzzr.muse.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
}

gradlePlugin {
    plugins {
        register("museQuality") {
            id = "muse.quality"
            implementationClass = "MuseQualityConventionPlugin"
        }
        register("museKotlinLibrary") {
            id = "muse.kotlin.library"
            implementationClass = "MuseKotlinLibraryConventionPlugin"
        }
        register("museAndroidLibrary") {
            id = "muse.android.library"
            implementationClass = "MuseAndroidLibraryConventionPlugin"
        }
    }
}
