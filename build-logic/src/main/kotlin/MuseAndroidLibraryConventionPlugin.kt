import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class MuseAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("muse.quality")

        extensions.configure<LibraryExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 28
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            testOptions {
                unitTests.isReturnDefaultValues = true
                unitTests.isIncludeAndroidResources = true
            }

            lint {
                abortOnError = true
                checkReleaseBuilds = true
                warningsAsErrors = true
                lintConfig = rootProject.file("app/lint.xml")
            }
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }

        // AGP still creates JVM test tasks for Android-only modules. Gradle 9.4
        // fails those tasks when no src/test suite exists, even though an
        // instrumentation suite may legitimately live under src/androidTest.
        if (!file("src/test").exists()) {
            tasks.withType<Test>().configureEach {
                failOnNoDiscoveredTests.set(false)
            }
        }
    }
}
