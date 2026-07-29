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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import luzzr.muse.ui.MuseScaffold
import luzzr.muse.ui.SystemBarsEffect
import luzzr.muse.ui.components.ProvideReduceMotion
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
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = checkAudioPermission(context)
                hasNotificationPermission = checkNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAudioPermission = checkAudioPermission(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasNotificationPermission = checkNotificationPermission(context)
    }

    fun requestMissingAudioPermission() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasAudioPermission) needed.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            if (!hasAudioPermission) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            hasRequestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        requestMissingAudioPermission()
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            viewModel.loadLibrary()
        }
    }

    // A media session is exempt from the Android 13 notification permission,
    // but HyperOS can still hide the foreground media card when the app's
    // notification permission was never requested. Ask at the first actual
    // playback start, once per Activity session, instead of interrupting the
    // initial library setup.
    LaunchedEffect(isPlaying, hasNotificationPermission) {
        if (
            isPlaying &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission &&
            !hasRequestedNotificationPermission
        ) {
            requestNotificationPermission()
        }
    }

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    MuseTheme(darkTheme = isDarkTheme, dynamicColor = false) {
        ProvideReduceMotion {
            SystemBarsEffect(isDarkTheme = isDarkTheme)
            MuseScaffold(
                viewModel = viewModel,
                hasAudioPermission = hasAudioPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermission = { requestMissingAudioPermission() },
                onRequestNotificationPermission = { requestNotificationPermission() }
            )
        }
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
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
