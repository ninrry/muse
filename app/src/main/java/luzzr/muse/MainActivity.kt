package luzzr.muse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import luzzr.muse.ui.MuseScaffold
import luzzr.muse.ui.SystemBarsEffect
import luzzr.muse.ui.theme.MuseTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseMain()
        }
    }
}

@Composable
private fun MuseMain() {
    val viewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current
    var hasAudioPermission by remember { mutableStateOf(checkAudioPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAudioPermission = checkAudioPermission(context)
        hasNotificationPermission = checkNotificationPermission(context)
    }

    fun requestMissingPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasAudioPermission) needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (!hasNotificationPermission) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (!hasAudioPermission) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        requestMissingPermissions()
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            viewModel.loadLibrary()
        }
    }

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    MuseTheme(darkTheme = isDarkTheme) {
        SystemBarsEffect(isDarkTheme = isDarkTheme)
        MuseScaffold(
            viewModel = viewModel,
            hasAudioPermission = hasAudioPermission,
            onRequestPermission = { requestMissingPermissions() }
        )
    }
}

private fun checkAudioPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkNotificationPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
