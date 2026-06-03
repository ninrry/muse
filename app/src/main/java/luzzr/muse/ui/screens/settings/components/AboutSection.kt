package luzzr.muse.ui.screens.settings.components

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import luzzr.muse.BuildConfig

@Composable
@Suppress("UNUSED_PARAMETER")
fun AboutSection(modifier: Modifier = Modifier) {
    SettingItem(
        icon = Icons.Default.Info,
        title = "Muse",
        subtitle = "版本 ${BuildConfig.VERSION_NAME}",
        onClick = {}
    )

    SettingItem(
        icon = Icons.Default.PhoneAndroid,
        title = "设备",
        subtitle = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
        onClick = {}
    )
}
