package luzzr.muse.ui.state

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidShizukuPermissionController @Inject constructor(
    @ApplicationContext private val context: Context
) : ShizukuPermissionController {

    override fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.getVersion() >= MIN_SHIZUKU_VERSION
        } catch (e: Exception) {
            MuseLog.w(TAG, "isAvailable: Shizuku binder check failed", e)
            false
        }
    }

    override fun isGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            MuseLog.w(TAG, "isGranted: Shizuku permission check failed", e)
            false
        }
    }

    override fun requestGrant() {
        try {
            if (isAvailable() && !isGranted()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            MuseLog.w(TAG, "requestGrant: Shizuku requestPermission failed, falling back to app", e)
            openShizukuApp()
        }
    }

    private fun openShizukuApp() {
        val intents = listOf(
            context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        )
        for (intent in intents.filterNotNull()) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try next.
            } catch (_: SecurityException) {
                // Try next.
            }
        }
    }

    private companion object {
        const val TAG = "AndroidShizukuPermissionController"
        const val MIN_SHIZUKU_VERSION = 11
        const val REQUEST_CODE = 9001
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
