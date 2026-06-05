package luzzr.muse.data.scanner

import android.content.SharedPreferences
import androidx.core.content.edit
import luzzr.muse.domain.scanner.ScanHistoryStore
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SharedPreferencesScanHistoryStore @Inject constructor(
    @Named("scan_prefs") private val scanPrefs: SharedPreferences
) : ScanHistoryStore {

    override fun markScanCompleted(timestampMillis: Long) {
        scanPrefs.edit { putLong(LAST_SCAN_TIME_KEY, timestampMillis) }
    }

    private companion object {
        const val LAST_SCAN_TIME_KEY = "last_scan_time"
    }
}
