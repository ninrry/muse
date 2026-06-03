package luzzr.muse.data.mapper

import android.net.Uri
import luzzr.muse.data.database.AlbumEntity
import luzzr.muse.data.model.Album

fun AlbumEntity.toAlbum() = Album(
    id = albumId,
    title = title,
    artist = artist,
    artworkUri = artworkUri?.let { Uri.parse(it) },
    songCount = songCount,
    year = year
)
