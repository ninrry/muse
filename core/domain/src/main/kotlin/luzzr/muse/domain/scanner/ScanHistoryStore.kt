package luzzr.muse.domain.scanner

interface ScanHistoryStore {
    fun markScanCompleted(timestampMillis: Long)
}
