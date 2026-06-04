plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "luzzr.muse"
    compileSdk = 35

    defaultConfig {
        applicationId = "luzzr.muse"
        minSdk = 28
        targetSdk = 35
        versionCode = 10
        versionName = "1.1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // Load from keystore.properties (gitignored, keeps secrets out of repo)
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val lines = keystorePropsFile.readLines()
                val props = mutableMapOf<String, String>()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#") || !trimmed.contains("=")) continue
                    val idx = trimmed.indexOf("=")
                    if (idx > 0) {
                        props[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
                    }
                }
                storeFile = props["keystorePath"]?.let { file(it) }
                storePassword = props["keystorePwd"]
                keyAlias = props["keyAliasName"]
                keyPassword = props["keyPwd"]
            } else {
                // Fallback: read from gradle.properties (deprecated)
                val keystorePath: String? by project
                val keystorePwd: String? by project
                val keyAliasName: String? by project
                val keyPwd: String? by project
                storeFile = keystorePath?.let { file(it) }
                storePassword = keystorePwd
                keyAlias = keyAliasName
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    // Allow references to generated Hilt classes during ktlint/detekt
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    disabledRules.set(
        setOf(
            "no-wildcard-imports",
            "argument-list-wrapping",
            "discouraged-comment-location",
            "wrapping",
            "filename"
        )
    )
}

detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    autoCorrect = false
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // Media compat (for NotificationCompat.MediaStyle)
    implementation(libs.media.compat)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // TagLib
    implementation(libs.taglib)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    // Logging
    implementation(libs.timber)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
