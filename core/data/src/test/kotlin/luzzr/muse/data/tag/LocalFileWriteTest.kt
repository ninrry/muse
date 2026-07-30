package luzzr.muse.data.tag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalFileWriteTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testMp4AtomWriterWritesMetadataAndArtworkToSyntheticContainer() {
        val testFile = temporaryFolder.newFile("song.m4b")
        testFile.writeBytes(makeSyntheticMp4())

        val metadataSuccess = Mp4MetadataAtomWriter.writeTextMetadata(
            file = testFile,
            title = "七里香",
            artist = "周杰伦",
            album = "Initial J",
            year = 2005,
            genre = "Pop"
        )
        assertTrue("Writing text metadata should succeed", metadataSuccess)

        val readMeta = Mp4MetadataAtomWriter.readMetadata(testFile)
        assertNotNull(readMeta)
        assertEquals("七里香", readMeta?.title)
        assertEquals("周杰伦", readMeta?.artist)
        assertEquals("Initial J", readMeta?.album)
        assertEquals(2005, readMeta?.year)
        assertEquals("Pop", readMeta?.genre)

        val fakeCoverBytes = ByteArray(256) { 0xFF.toByte() }.apply {
            this[0] = 0x89.toByte()
            this[1] = 'P'.code.toByte()
            this[2] = 'N'.code.toByte()
            this[3] = 'G'.code.toByte()
        }
        val artworkSuccess = Mp4MetadataAtomWriter.writeArtwork(
            sourceFile = testFile,
            targetFile = testFile,
            artworkBytes = fakeCoverBytes,
            mimeType = "image/png"
        )
        assertTrue("Writing cover artwork should succeed", artworkSuccess)

        val readCover = Mp4MetadataAtomWriter.readArtwork(testFile)
        assertNotNull("Artwork should be read back", readCover)
        assertArrayEquals(fakeCoverBytes, readCover)

        val readMime = Mp4MetadataAtomWriter.readArtworkMime(testFile)
        assertEquals("image/png", readMime)
    }

    private fun makeSyntheticMp4(): ByteArray {
        val mvhdPayload = ByteArray(100)
        writeUInt32BE(mvhdPayload, 12, 1_000)
        writeUInt32BE(mvhdPayload, 16, 180_000)
        return box("ftyp", "M4B \u0000\u0000\u0000\u0000M4B ".toByteArray(Charsets.ISO_8859_1)) +
            box("moov", box("mvhd", mvhdPayload)) +
            box("mdat", ByteArray(16))
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val result = ByteArray(8 + payload.size)
        writeUInt32BE(result, 0, result.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        System.arraycopy(typeBytes, 0, result, 4, 4)
        System.arraycopy(payload, 0, result, 8, payload.size)
        return result
    }

    private fun writeUInt32BE(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }

    @Test
    fun testOggOpusMetadataParser() {
        val testFile = temporaryFolder.newFile("test_audiobook.ogg")

        // 1. Create a dummy valid Ogg Opus file structure
        val page0 = OggOpusMetadataParser.OggPage(
            headerType = 2, // BOS
            granulePos = 0L,
            serialNumber = 54321,
            sequenceNumber = 0,
            segments = byteArrayOf(8),
            payload = "OpusHead".toByteArray(Charsets.US_ASCII)
        )

        val initialTags = OggOpusMetadataParser.OpusTags(
            vendor = "test-encoder",
            comments = LinkedHashMap()
        )
        val initialTagsPayload = initialTags.toByteArray()
        val segments = ByteArray(initialTagsPayload.size / 255 + 1).apply {
            for (i in 0 until size - 1) this[i] = 255.toByte()
            this[size - 1] = (initialTagsPayload.size % 255).toByte()
        }

        val page1 = OggOpusMetadataParser.OggPage(
            headerType = 0,
            granulePos = 0L,
            serialNumber = 54321,
            sequenceNumber = 1,
            segments = segments,
            payload = initialTagsPayload
        )

        // Write Page 0 and Page 1
        testFile.outputStream().use { out ->
            out.write(page0.toByteArray())
            out.write(page1.toByteArray())
            // Add some mock audio data
            out.write("MOCK_AUDIO_DATA_PAGE_2_3_4_ETC".toByteArray(Charsets.US_ASCII))
        }

        // Verify we can detect it as Ogg Opus
        assertTrue("Should detect dummy file as Ogg Opus", OggOpusMetadataParser.isOggOpusFile(testFile))

        // 2. Read initial metadata (should be empty/null)
        val meta0 = OggOpusMetadataParser.readMetadata(testFile)
        assertNotNull(meta0)
        assertTrue(meta0?.title.isNullOrBlank())

        // 3. Write metadata via OggOpusMetadataParser
        val writeSuccess = OggOpusMetadataParser.writeMetadata(
            sourceFile = testFile,
            targetFile = testFile,
            title = "有声书章节1",
            artist = "伯恩斯",
            album = "情绪疗法",
            year = 2026,
            genre = "Psychology"
        )
        assertTrue("Writing metadata to Ogg Opus should succeed", writeSuccess)

        // 4. Read metadata back and verify
        val meta1 = OggOpusMetadataParser.readMetadata(testFile)
        assertNotNull(meta1)
        assertEquals("有声书章节1", meta1?.title)
        assertEquals("伯恩斯", meta1?.artist)
        assertEquals("情绪疗法", meta1?.album)
        assertEquals(2026, meta1?.year)
        assertEquals("Psychology", meta1?.genre)
    }
}
