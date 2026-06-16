package luzzr.muse.data.audio

object AudioFileSupport {
    val supportedAudioExtensions = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "alac", "wav")
    val mp4AudioContainerExtensions = setOf("m4a", "m4b", "alac")

    private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "3g2", "wmv", "flv")
    private val audioMimeMarkers = setOf(
        "audio/",
        "application/ogg",
        "application/x-ogg"
    )

    fun extension(path: String): String {
        return path.substringAfterLast('.', "").lowercase()
    }

    fun isKnownVideo(path: String, mime: String? = null): Boolean {
        val normalizedMime = mime.orEmpty().lowercase()
        return normalizedMime.startsWith("video/") || extension(path) in videoExtensions
    }

    fun isSupportedAudio(path: String, mime: String? = null): Boolean {
        if (isKnownVideo(path, mime)) return false
        val ext = extension(path)
        val normalizedMime = mime.orEmpty().lowercase()
        return ext in supportedAudioExtensions || audioMimeMarkers.any { normalizedMime.startsWith(it) }
    }

    fun isSupportedAudioPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (isKnownVideo(path)) return false
        return extension(path) in supportedAudioExtensions
    }

    fun detectCodec(path: String, mime: String? = null): String {
        val ext = extension(path)
        val normalizedMime = mime.orEmpty().lowercase()
        return when {
            normalizedMime.contains("flac") || ext == "flac" -> "FLAC"
            normalizedMime.contains("opus") || ext == "opus" -> "Opus"
            ext == "m4b" -> "M4B/AAC"
            normalizedMime.contains("m4a") || normalizedMime == "audio/mp4" || ext == "m4a" -> "M4A/AAC"
            normalizedMime.contains("alac") || ext == "alac" -> "ALAC"
            normalizedMime.contains("wav") || ext == "wav" -> "WAV"
            normalizedMime.contains("ogg") || ext == "ogg" || ext == "oga" -> "OGG"
            normalizedMime.contains("mpeg") || normalizedMime.contains("mp3") || ext == "mp3" -> "MP3"
            ext.isBlank() -> "UNKNOWN"
            else -> ext.uppercase()
        }
    }
}
