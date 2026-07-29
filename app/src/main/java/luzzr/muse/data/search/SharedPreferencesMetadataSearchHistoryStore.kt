package luzzr.muse.data.search

import android.content.SharedPreferences
import androidx.core.content.edit
import luzzr.muse.domain.metadata.MetadataSearchHistoryStore
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SharedPreferencesMetadataSearchHistoryStore @Inject constructor(
    @Named("search_prefs") private val searchPrefs: SharedPreferences
) : MetadataSearchHistoryStore {

    override fun readHistory(): String = searchPrefs.getString("search_history", "") ?: ""

    override fun saveHistory(history: String) {
        searchPrefs.edit { putString("search_history", history) }
    }
}
