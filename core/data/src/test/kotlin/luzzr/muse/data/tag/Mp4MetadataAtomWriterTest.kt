package luzzr.muse.data.tag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class Mp4MetadataAtomWriterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `writeTextMetadata adds ilst metadata and adjusts stco when moov grows before mdat`() {
        val original = makeMp4WithMoovBeforeMdat()
        val originalStco = readStcoOffset(original)
        val file = temporaryFolder.newFile("song.m4a")
        file.writeBytes(original)

        val wrote = Mp4MetadataAtomWriter.writeTextMetadata(
            file = file,
            title = "New Title",
            artist = "New Artist",
            album = "New Album",
            year = 2026,
            genre = "Pop"
        )

        val updated = file.readBytes()
        val delta = updated.size - original.size
        assertTrue(wrote)
        assertTrue(updated.indexOf(byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte())) > 0)
        assertTrue(updated.indexOf("New Title".toByteArray()) > 0)
        assertEquals(originalStco + delta, readStcoOffset(updated))
    }

    @Test
    fun `writeTextMetadata can stream from source file to separate target file`() {
        val original = makeMp4WithMoovBeforeMdat()
        val source = temporaryFolder.newFile("source.m4a")
        val target = temporaryFolder.newFile("target.m4a")
        source.writeBytes(original)

        val wrote = Mp4MetadataAtomWriter.writeTextMetadata(
            sourceFile = source,
            targetFile = target,
            title = "Target Title",
            artist = "Target Artist"
        )

        assertTrue(wrote)
        assertArrayEquals(original, source.readBytes())
        assertTrue(target.readBytes().indexOf("Target Title".toByteArray()) > 0)
    }

    @Test
    fun `writeArtwork writes covr atom with correct image type`() {
        val original = makeMp4WithMoovBeforeMdat()
        val file = temporaryFolder.newFile("song_art.m4a")
        file.writeBytes(original)

        val imageBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        val wrote = Mp4MetadataAtomWriter.writeArtwork(file, file, imageBytes, "image/png")

        val updated = file.readBytes()
        assertTrue(wrote)
        assertTrue(updated.indexOf(byteArrayOf('c'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte(), 'r'.code.toByte())) > 0)
        assertTrue(updated.indexOf(imageBytes) > 0)
    }

    @Test
    fun `writeArtwork with null bytes deletes covr atom`() {
        val original = makeMp4WithMoovBeforeMdat()
        val file = temporaryFolder.newFile("song_art_delete.m4a")
        file.writeBytes(original)

        val imageBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        Mp4MetadataAtomWriter.writeArtwork(file, file, imageBytes, "image/png")
        assertTrue(file.readBytes().indexOf("covr".toByteArray(Charsets.US_ASCII)) > 0)

        val deleted = Mp4MetadataAtomWriter.writeArtwork(file, file, null, null)
        assertTrue(deleted)
        assertEquals(-1, file.readBytes().indexOf("covr".toByteArray(Charsets.US_ASCII)))
    }

    private fun makeMp4WithMoovBeforeMdat(): ByteArray {
        val ftyp = box("ftyp", byteArrayOf('M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()))
        val placeholderStco = makeStco(0)
        val moovWithPlaceholder = makeMoov(placeholderStco)
        val mdatPayload = ByteArray(32) { 0x11 }
        val mdat = box("mdat", mdatPayload)
        val mdatPayloadOffset = ftyp.size + moovWithPlaceholder.size + 8
        val moov = makeMoov(makeStco(mdatPayloadOffset))
        return concat(ftyp, moov, mdat)
    }

    private fun makeMoov(stco: ByteArray): ByteArray {
        val stbl = box("stbl", stco)
        val minf = box("minf", stbl)
        val mdia = box("mdia", minf)
        val trak = box("trak", mdia)
        return box("moov", trak)
    }

    private fun makeStco(offset: Int): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            writeUInt32(0)
            writeUInt32(1)
            writeUInt32(offset)
        }.toByteArray()
        return box("stco", payload)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            writeUInt32(8 + payload.size)
            write(type.toByteArray(Charsets.US_ASCII))
            write(payload)
        }.toByteArray()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        return ByteArrayOutputStream(arrays.sumOf { it.size }).apply {
            arrays.forEach { write(it) }
        }.toByteArray()
    }

    private fun readStcoOffset(data: ByteArray): Int {
        val stco = data.indexOf("stco".toByteArray(Charsets.US_ASCII))
        assertTrue(stco > 4)
        return readUInt32(data, stco + 12).toInt()
    }

    private fun ByteArrayOutputStream.writeUInt32(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        for (index in 0..size - needle.size) {
            var matches = true
            for (needleIndex in needle.indices) {
                if (this[index + needleIndex] != needle[needleIndex]) {
                    matches = false
                    break
                }
            }
            if (matches) return index
        }
        return -1
    }
}
