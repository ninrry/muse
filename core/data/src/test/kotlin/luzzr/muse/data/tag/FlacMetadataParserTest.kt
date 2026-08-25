package luzzr.muse.data.tag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64

class FlacMetadataParserTest {

    private fun createMinimalFlacFile(
        tempDir: File,
        fileName: String = "test.flac",
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null,
        pictureBytes: ByteArray? = null
    ): File {
        val file = File(tempDir, fileName)
        val out = ByteArrayOutputStream()

        // 1. 'fLaC' Magic
        out.write("fLaC".toByteArray(Charsets.US_ASCII))

        // 2. Block 0: STREAMINFO (34 bytes)
        val streamInfoData = ByteArray(34)
        val hasMoreBlocks = (title != null || artist != null || album != null || pictureBytes != null)
        val streamInfoHeader = (if (!hasMoreBlocks) 0x80 else 0x00) or 0
        out.write(streamInfoHeader)
        out.write(0)
        out.write(0)
        out.write(34)
        out.write(streamInfoData)

        // 3. Block 4: VORBIS_COMMENT if tags are present
        if (title != null || artist != null || album != null || year != null || genre != null) {
            val comments = LinkedHashMap<String, String>()
            if (title != null) comments["TITLE"] = title
            if (artist != null) comments["ARTIST"] = artist
            if (album != null) comments["ALBUM"] = album
            if (year != null) comments["DATE"] = year.toString()
            if (genre != null) comments["GENRE"] = genre

            val vorbisBytes = FlacMetadataParser.VorbisComments("MuseTest", comments).toByteArray()
            val isLast = (pictureBytes == null)
            val vorbisHeader = (if (isLast) 0x80 else 0x00) or 4
            out.write(vorbisHeader)
            out.write((vorbisBytes.size ushr 16) and 0xFF)
            out.write((vorbisBytes.size ushr 8) and 0xFF)
            out.write(vorbisBytes.size and 0xFF)
            out.write(vorbisBytes)
        }

        // 4. Block 6: PICTURE if picture is present
        if (pictureBytes != null) {
            val picPayload = ByteArrayOutputStream()
            // picture_type = 3 (Front cover)
            writeInt32BE(picPayload, 3)
            // mime = "image/jpeg"
            val mime = "image/jpeg".toByteArray(Charsets.US_ASCII)
            writeInt32BE(picPayload, mime.size)
            picPayload.write(mime)
            // desc = ""
            writeInt32BE(picPayload, 0)
            // width = 500, height = 500, depth = 24, colors = 0
            writeInt32BE(picPayload, 500)
            writeInt32BE(picPayload, 500)
            writeInt32BE(picPayload, 24)
            writeInt32BE(picPayload, 0)
            // data length + data
            writeInt32BE(picPayload, pictureBytes.size)
            picPayload.write(pictureBytes)

            val picBlockData = picPayload.toByteArray()
            val isLast = true
            val picHeader = (if (isLast) 0x80 else 0x00) or 6
            out.write(picHeader)
            out.write((picBlockData.size ushr 16) and 0xFF)
            out.write((picBlockData.size ushr 8) and 0xFF)
            out.write(picBlockData.size and 0xFF)
            out.write(picBlockData)
        }

        // 5. Audio frame data
        out.write(byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x12, 0x34))

        file.writeBytes(out.toByteArray())
        return file
    }

    private fun writeInt32BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    @Test
    fun `isFlacFile returns true for valid FLAC and false for other files`() {
        val tempDir = Files.createTempDirectory("flac_test").toFile()
        try {
            val validFlac = createMinimalFlacFile(tempDir, "valid.flac")
            assertTrue(FlacMetadataParser.isFlacFile(validFlac))

            val fakeFile = File(tempDir, "fake.flac").apply { writeBytes("NOT A FLAC".toByteArray()) }
            assertFalse(FlacMetadataParser.isFlacFile(fakeFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `readMetadata correctly parses FLAC Vorbis Comment tags`() {
        val tempDir = Files.createTempDirectory("flac_test").toFile()
        try {
            val flacFile = createMinimalFlacFile(
                tempDir = tempDir,
                title = "晴天",
                artist = "周杰伦",
                album = "叶惠美",
                year = 2003,
                genre = "流行"
            )

            val meta = FlacMetadataParser.readMetadata(flacFile)
            assertNotNull(meta)
            assertEquals("晴天", meta?.title)
            assertEquals("周杰伦", meta?.artist)
            assertEquals("叶惠美", meta?.album)
            assertEquals(2003, meta?.year)
            assertEquals("流行", meta?.genre)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `readArtwork and writeArtwork correctly handle embedded FLAC cover art`() {
        val tempDir = Files.createTempDirectory("flac_test").toFile()
        try {
            val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
            val flacFile = createMinimalFlacFile(
                tempDir = tempDir,
                title = "青花瓷",
                artist = "周杰伦",
                pictureBytes = fakeJpeg
            )

            val readArt = FlacMetadataParser.readArtwork(flacFile)
            assertNotNull(readArt)
            assertArrayEquals(fakeJpeg, readArt)
            assertEquals("image/jpeg", FlacMetadataParser.readArtworkMime(flacFile))

            // Replace artwork with new bytes
            val fakePng = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 5, 6, 7, 8)
            val writeSuccess = FlacMetadataParser.writeArtwork(flacFile, flacFile, fakePng, "image/png")
            assertTrue(writeSuccess)

            val updatedArt = FlacMetadataParser.readArtwork(flacFile)
            assertNotNull(updatedArt)
            assertArrayEquals(fakePng, updatedArt)

            // Delete artwork
            val deleteSuccess = FlacMetadataParser.writeArtwork(flacFile, flacFile, null, null)
            assertTrue(deleteSuccess)
            assertNull(FlacMetadataParser.readArtwork(flacFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `readArtwork supports Vorbis Comment base64 COVERART fallback`() {
        val tempDir = Files.createTempDirectory("flac_test").toFile()
        try {
            val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 9, 9)
            val base64Art = Base64.getEncoder().encodeToString(fakeJpeg)

            val flacFile = File(tempDir, "base64_cover.flac")
            val out = ByteArrayOutputStream()
            out.write("fLaC".toByteArray(Charsets.US_ASCII))

            // STREAMINFO
            out.write(0) // isLast = false
            out.write(0); out.write(0); out.write(34)
            out.write(ByteArray(34))

            // VORBIS_COMMENT with COVERART
            val comments = LinkedHashMap<String, String>()
            comments["TITLE"] = "七里香"
            comments["ARTIST"] = "周杰伦"
            comments["COVERART"] = base64Art

            val vorbisBytes = FlacMetadataParser.VorbisComments("MuseTest", comments).toByteArray()
            out.write(0x80 or 4) // isLast = true
            out.write((vorbisBytes.size ushr 16) and 0xFF)
            out.write((vorbisBytes.size ushr 8) and 0xFF)
            out.write(vorbisBytes.size and 0xFF)
            out.write(vorbisBytes)
            out.write(byteArrayOf(0xFF.toByte(), 0xF8.toByte()))

            flacFile.writeBytes(out.toByteArray())

            val extracted = FlacMetadataParser.readArtwork(flacFile)
            assertNotNull(extracted)
            assertArrayEquals(fakeJpeg, extracted)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `writeMetadata and writeLyrics persist tags atomically`() {
        val tempDir = Files.createTempDirectory("flac_test").toFile()
        try {
            val flacFile = createMinimalFlacFile(tempDir, "initial.flac")
            val writeSuccess = FlacMetadataParser.writeMetadata(
                inputFile = flacFile,
                outputFile = flacFile,
                title = "稻香",
                artist = "周杰伦",
                album = "魔杰座",
                year = 2008,
                genre = "流行",
                lyrics = "[00:00.00]对这个世界如果你有太多的抱怨"
            )
            assertTrue(writeSuccess)

            val meta = FlacMetadataParser.readMetadata(flacFile)
            assertNotNull(meta)
            assertEquals("稻香", meta?.title)
            assertEquals("周杰伦", meta?.artist)
            assertEquals("魔杰座", meta?.album)
            assertEquals(2008, meta?.year)
            assertEquals("流行", meta?.genre)

            val lyrics = FlacMetadataParser.readLyrics(flacFile)
            assertEquals("[00:00.00]对这个世界如果你有太多的抱怨", lyrics)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
