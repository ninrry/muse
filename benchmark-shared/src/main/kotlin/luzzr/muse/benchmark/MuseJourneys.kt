package luzzr.muse.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

object MuseJourneys {
    private const val UI_TIMEOUT = 5_000L

    fun grantRuntimePermissions(device: UiDevice, packageName: String) {
        listOf(
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.POST_NOTIFICATIONS"
        ).forEach { permission ->
            device.executeShellCommand("pm grant $packageName $permission")
        }
    }

    fun navigatePrimaryTabs(device: UiDevice) {
        clickText(device, "曲库")
        clickText(device, "有声书")
        clickText(device, "设置")
        clickText(device, "首页")
    }

    fun navigateToSettings(device: UiDevice) {
        clickText(device, "设置")
        device.wait(Until.hasObject(By.text("外观")), UI_TIMEOUT)
    }

    fun scrollSettings(device: UiDevice) {
        val centerX = device.displayWidth / 2
        val startY = device.displayHeight * 3 / 4
        val endY = device.displayHeight / 4
        device.swipe(centerX, startY, centerX, endY, 24)
        device.waitForIdle()
    }

    private fun clickText(device: UiDevice, text: String) {
        val item = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT)
            ?: error("Unable to find navigation item: $text")
        item.clickNearestClickableParent()
        device.waitForIdle()
    }

    private fun UiObject2.clickNearestClickableParent() {
        var target: UiObject2? = this
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        (target ?: this).click()
    }
}
