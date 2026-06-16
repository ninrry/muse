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

    @Volatile
    private var permissionGranted = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        MuseLog.d(TAG, "Shizuku binder received")
        refreshPermissionState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        MuseLog.d(TAG, "Shizuku binder dead")
        permissionGranted = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        MuseLog.d(TAG, "Shizuku permission result: code=$requestCode result=$result")
        if (requestCode == REQUEST_CODE) {
            permissionGranted = result == PackageManager.PERMISSION_GRANTED
        }
    }

    init {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            refreshPermissionState()
        } catch (e: Exception) {
            MuseLog.w(TAG, "init: failed to register Shizuku listeners", e)
        }
    }

    override fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.getVersion() >= MIN_SHIZUKU_VERSION
        } catch (e: Exception) {
            MuseLog.w(TAG, "isAvailable: Shizuku binder check failed", e)
            false
        }
    }

    override fun isGranted(): Boolean {
        if (permissionGranted) return true
        refreshPermissionState()
        return permissionGranted
    }

    override fun requestGrant() {
        try {
            if (isAvailable()) {
                if (!isGranted()) {
                    Shizuku.requestPermission(REQUEST_CODE)
                }
            }
        } catch (e: Exception) {
            MuseLog.w(TAG, "requestGrant: Shizuku requestPermission failed, opening app", e)
        }
        openShizukuApp()
    }

    private fun refreshPermissionState() {
        try {
            permissionGranted = isAvailable() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            MuseLog.w(TAG, "refreshPermissionState failed", e)
        }
    }

    private fun openShizukuApp() {
        val intents = listOf(
            context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        )
        for (intent in intents.filterNotNull()) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "AndroidShizukuPermissionController"
        const val MIN_SHIZUKU_VERSION = 11
        const val REQUEST_CODE = 9001
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
