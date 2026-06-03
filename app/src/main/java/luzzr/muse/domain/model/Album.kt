package luzzr.muse.domain.model

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri? = null,
    val songCount: Int = 0,
    val year: Int? = null
)
