package luzzr.muse.domain.metadata

interface MetadataSearchHistoryStore {
    fun readHistory(): String
    fun saveHistory(history: String)
}
