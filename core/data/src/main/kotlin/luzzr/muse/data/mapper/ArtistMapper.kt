package luzzr.muse.data.mapper

import luzzr.muse.data.database.ArtistEntity
import luzzr.muse.domain.model.Artist

fun ArtistEntity.toArtist() = Artist(
    name = artistName,
    songCount = songCount,
    albumCount = albumCount
)
