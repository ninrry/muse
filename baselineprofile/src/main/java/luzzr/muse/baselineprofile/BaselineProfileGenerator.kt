package luzzr.muse.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import luzzr.muse.benchmark.MuseJourneys
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true
    ) {
        MuseJourneys.grantRuntimePermissions(device, PACKAGE_NAME)
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun criticalUserJourneys() = baselineProfileRule.collect(packageName = PACKAGE_NAME) {
        MuseJourneys.grantRuntimePermissions(device, PACKAGE_NAME)
        startActivityAndWait()
        MuseJourneys.navigatePrimaryTabs(device)
        MuseJourneys.navigateToSettings(device)
        MuseJourneys.scrollSettings(device)
    }

    private companion object {
        const val PACKAGE_NAME = "luzzr.muse"
    }
}
