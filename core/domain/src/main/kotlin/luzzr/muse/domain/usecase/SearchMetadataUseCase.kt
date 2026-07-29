package luzzr.muse.domain.usecase

import luzzr.muse.domain.metadata.MetadataSearchClient
import luzzr.muse.domain.metadata.MetadataSearchHistoryStore
import luzzr.muse.domain.model.MetadataResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchMetadataUseCase @Inject constructor(
    private val metadataSearchClient: MetadataSearchClient,
    private val searchHistoryStore: MetadataSearchHistoryStore
) {
    private val cache = LinkedHashMap<String, List<MetadataResult>>(MAX_CACHE_SIZE, 0.75f, true)

    suspend operator fun invoke(title: String, artist: String?, album: String? = null): List<MetadataResult> {
        return search(title, artist, album, exact = false)
    }

    suspend fun exact(title: String, artist: String?): List<MetadataResult> {
        return search(title, artist, album = null, exact = true)
    }

    private suspend fun search(title: String, artist: String?, album: String?, exact: Boolean): List<MetadataResult> {
        val normalizedArtist = artist.orEmpty()
        val normalizedAlbum = album.orEmpty()
        val key = "${if (exact) "exact" else "auto"}|$title|$normalizedArtist|$normalizedAlbum".lowercase()
        cache[key]?.let { return it }

        val results = if (exact) {
            metadataSearchClient.searchExact(title, artist)
        } else {
            metadataSearchClient.search(title, artist, album)
        }

        if (results.isNotEmpty()) {
            cache[key] = results
            if (cache.size > MAX_CACHE_SIZE) {
                val eldest = cache.keys.first()
                cache.remove(eldest)
            }
            saveSearchHistory(title, normalizedArtist)
        }
        return results
    }

    private fun saveSearchHistory(title: String, artist: String) {
        val history = searchHistoryStore.readHistory()
        val entry = "$title|$artist"
        val entries = history.split("\n").filter { it.isNotBlank() }.toMutableList()
        entries.remove(entry)
        entries.add(0, entry)
        val trimmed = entries.take(MAX_HISTORY_SIZE).joinToString("\n")
        searchHistoryStore.saveHistory(trimmed)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 50
        private const val MAX_HISTORY_SIZE = 20
    }
}
