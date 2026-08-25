package luzzr.muse.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileSupportTest {

    @Test
    fun `isSupportedAudioPath recognizes all supported audio formats`() {
        val supportedPaths = listOf(
            "/storage/emulated/0/Music/song.mp3",
            "/storage/emulated/0/Music/track.flac",
            "/storage/emulated/0/Music/audio.ogg",
            "/storage/emulated/0/Music/sample.oga",
            "/storage/emulated/0/Music/voice.opus",
            "/storage/emulated/0/Music/tune.m4a",
            "/storage/emulated/0/Music/book.m4b",
            "/storage/emulated/0/Music/lossless.alac",
            "/storage/emulated/0/Music/sound.wav",
            "/storage/emulated/0/Music/stream.aac",
            "/storage/emulated/0/Music/hires.ape",
            "/storage/emulated/0/Music/master.aiff",
            "/storage/emulated/0/Music/master.aif",
            "/storage/emulated/0/Music/sacd.dsf",
            "/storage/emulated/0/Music/sacd.dff",
            "/storage/emulated/0/Music/legacy.wma"
        )

        for (path in supportedPaths) {
            assertTrue("Path should be supported: $path", AudioFileSupport.isSupportedAudioPath(path))
        }
    }

    @Test
    fun `isSupportedAudioPath rejects videos and unsupported files`() {
        val unsupportedPaths = listOf(
            "/storage/emulated/0/Movies/video.mp4",
            "/storage/emulated/0/Movies/clip.mkv",
            "/storage/emulated/0/Movies/movie.avi",
            "/storage/emulated/0/Documents/doc.pdf",
            "/storage/emulated/0/Pictures/photo.jpg",
            ""
        )

        for (path in unsupportedPaths) {
            assertFalse("Path should be rejected: $path", AudioFileSupport.isSupportedAudioPath(path))
        }
    }

    @Test
    fun `detectCodec returns expected codec strings`() {
        assertEquals("FLAC", AudioFileSupport.detectCodec("/path/song.flac"))
        assertEquals("Opus", AudioFileSupport.detectCodec("/path/song.opus"))
        assertEquals("AAC", AudioFileSupport.detectCodec("/path/song.aac"))
        assertEquals("M4A/AAC", AudioFileSupport.detectCodec("/path/song.m4a"))
        assertEquals("M4B/AAC", AudioFileSupport.detectCodec("/path/song.m4b"))
        assertEquals("ALAC", AudioFileSupport.detectCodec("/path/song.alac"))
        assertEquals("WAV", AudioFileSupport.detectCodec("/path/song.wav"))
        assertEquals("AIFF", AudioFileSupport.detectCodec("/path/song.aiff"))
        assertEquals("AIFF", AudioFileSupport.detectCodec("/path/song.aif"))
        assertEquals("APE", AudioFileSupport.detectCodec("/path/song.ape"))
        assertEquals("DSD", AudioFileSupport.detectCodec("/path/song.dsf"))
        assertEquals("DSD", AudioFileSupport.detectCodec("/path/song.dff"))
        assertEquals("WMA", AudioFileSupport.detectCodec("/path/song.wma"))
        assertEquals("OGG", AudioFileSupport.detectCodec("/path/song.ogg"))
        assertEquals("MP3", AudioFileSupport.detectCodec("/path/song.mp3"))
    }
}
