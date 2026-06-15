package luzzr.muse.ui.state

/**
 * Port for Shizuku / Sui authorization state and actions.
 *
 * Implementations live in the app module because they need the Shizuku SDK.
 */
interface ShizukuPermissionController {
    /** Whether Shizuku (or Sui) is installed and its service is running. */
    fun isAvailable(): Boolean

    /** Whether the user has already granted this app Shizuku access. */
    fun isGranted(): Boolean

    /** Open Shizuku (or the system permission dialog if supported) to let the user grant access. */
    fun requestGrant()
}
