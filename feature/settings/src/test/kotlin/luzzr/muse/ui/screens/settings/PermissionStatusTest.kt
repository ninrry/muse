package luzzr.muse.ui.screens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStatusTest {

    @Test
    fun `all required permissions are visible as granted`() {
        val snapshot = PermissionSnapshot(
            audio = PermissionStatus.GRANTED,
            fullFileAccess = PermissionStatus.GRANTED,
            notifications = PermissionStatus.GRANTED
        )

        assertTrue(snapshot.allGranted)
    }

    @Test
    fun `not required platform permission does not block readiness`() {
        val snapshot = PermissionSnapshot(
            audio = PermissionStatus.GRANTED,
            fullFileAccess = PermissionStatus.NOT_REQUIRED,
            notifications = PermissionStatus.NOT_REQUIRED
        )

        assertTrue(snapshot.allGranted)
    }

    @Test
    fun `a missing permission keeps the aggregate incomplete`() {
        val snapshot = PermissionSnapshot(
            audio = PermissionStatus.GRANTED,
            fullFileAccess = PermissionStatus.MISSING,
            notifications = PermissionStatus.GRANTED
        )

        assertFalse(snapshot.allGranted)
    }
}
