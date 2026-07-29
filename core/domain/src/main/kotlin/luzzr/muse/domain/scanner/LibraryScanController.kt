package luzzr.muse.domain.scanner

import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

interface LibraryScanController {
    val songs: StateFlow<List<Song>>
    val isScanning: StateFlow<Boolean>
    val scanProgress: StateFlow<Int>
    val scanStats: StateFlow<ScanStats?>

    suspend fun scanAll(): List<Song>

}
