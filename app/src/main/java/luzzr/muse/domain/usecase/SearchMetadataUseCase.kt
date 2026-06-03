package luzzr.muse.domain.usecase

import android.content.SharedPreferences
import luzzr.muse.data.network.MetadataFetcher
import luzzr.muse.domain.model.MetadataResult
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SearchMetadataUseCase @Inject constructor(
    private val metadataFetcher: MetadataFetcher,
    @Named("search_prefs") private val searchPrefs: SharedPreferences
) {
    private val cache = LinkedHashMap<String, List<MetadataResult>>(MAX_CACHE_SIZE, 0.75f, true)

    suspend operator fun invoke(title: String, artist: String): List<MetadataResult> {
        val key = "$title|$artist".lowercase()
        cache[key]?.let { return it }

        val results = metadataFetcher.search(title, artist)

        if (results.isNotEmpty()) {
            cache[key] = results
            if (cache.size > MAX_CACHE_SIZE) {
                val eldest = cache.keys.first()
                cache.remove(eldest)
            }
            saveSearchHistory(title, artist)
        }
        return results
    }

    private fun saveSearchHistory(title: String, artist: String) {
        val history = searchPrefs.getString("search_history", "") ?: ""
        val entry = "$title|$artist"
        val entries = history.split("\n").filter { it.isNotBlank() }.toMutableList()
        entries.remove(entry)
        entries.add(0, entry)
        val trimmed = entries.take(MAX_HISTORY_SIZE).joinToString("\n")
        searchPrefs.edit().putString("search_history", trimmed).apply()
    }

    companion object {
        private const val MAX_CACHE_SIZE = 50
        private const val MAX_HISTORY_SIZE = 20
    }
}
