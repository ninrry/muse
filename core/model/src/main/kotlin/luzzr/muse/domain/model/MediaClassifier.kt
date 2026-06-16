package luzzr.muse.domain.model

object MediaClassifier {
    fun isAudiobook(song: Song): Boolean {
        val extension = song.filePath.substringAfterLast('.', "").lowercase()
        return song.codec.equals("OGG", ignoreCase = true) ||
            song.codec.equals("M4B/AAC", ignoreCase = true) ||
            extension in AUDIOBOOK_EXTENSIONS
    }

    private val AUDIOBOOK_EXTENSIONS = setOf("ogg", "oga", "m4b")
}
