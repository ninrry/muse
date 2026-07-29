package luzzr.muse.data.mapper

import luzzr.muse.data.database.AlbumEntity
import luzzr.muse.domain.model.Album

fun AlbumEntity.toAlbum() = Album(
    id = albumId,
    title = title,
    artist = artist,
    artworkUri = artworkUri,
    songCount = songCount,
    year = year
)
