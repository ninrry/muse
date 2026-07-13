package luzzr.muse.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun primaryTabs() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            MuseJourneys.grantRuntimePermissions(device, PACKAGE_NAME)
            startActivityAndWait()
        }
    ) {
        MuseJourneys.navigatePrimaryTabs(device)
    }

    @Test
    fun settingsScroll() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = {
            MuseJourneys.grantRuntimePermissions(device, PACKAGE_NAME)
            startActivityAndWait()
            MuseJourneys.navigateToSettings(device)
        }
    ) {
        MuseJourneys.scrollSettings(device)
    }

    private companion object {
        const val PACKAGE_NAME = "luzzr.muse"
    }
}
