package luzzr.muse.domain.model

object MediaClassifier {
    fun isAudiobook(song: Song): Boolean {
        val extension = song.filePath.substringAfterLast('.', "").lowercase()
        return song.codec.equals("OGG", ignoreCase = true) || extension == "ogg"
    }
}
