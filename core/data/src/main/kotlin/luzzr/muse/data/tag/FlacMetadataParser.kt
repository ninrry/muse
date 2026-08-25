package luzzr.muse.data.tag

import luzzr.muse.core.log.MuseLog
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Base64
import java.util.Locale

/**
 * Pure Kotlin FLAC (Free Lossless Audio Codec) parser and writer.
 * Directly parses and writes FLAC metadata blocks according to the Xiph.Org FLAC specification:
 * - Block 0: STREAMINFO
 * - Block 4: VORBIS_COMMENT (TITLE, ARTIST, ALBUM, DATE/YEAR, GENRE, TRACKNUMBER, ALBUMARTIST, LYRICS)
 * - Block 6: PICTURE (embedded cover artwork, MIME, dimensions, raw image bytes)
 * - Base64 METADATA_BLOCK_PICTURE and COVERART tags in Vorbis Comments
 *
 * Supports both File and InputStream (ContentResolver/Uri) to ensure 100% compatibility under Scoped Storage.
 */
internal object FlacMetadataParser {

    private const val BLOCK_STREAMINFO = 0
    private const val BLOCK_PADDING = 1
    private const val BLOCK_APPLICATION = 2
    private const val BLOCK_SEEKTABLE = 3
    private const val BLOCK_VORBIS_COMMENT = 4
    private const val BLOCK_CUESHEET = 5
    private const val BLOCK_PICTURE = 6

    data class FlacBlock(
        val isLast: Boolean,
        val blockType: Int,
        val length: Int,
        val data: ByteArray
    )

    data class FlacPicture(
        val pictureType: Int,
        val mimeType: String,
        val description: String,
        val width: Int,
        val height: Int,
        val colorDepth: Int,
        val numColors: Int,
        val data: ByteArray
    )

    data class VorbisComments(
        val vendor: String,
        val comments: LinkedHashMap<String, String>
    ) {
        fun get(key: String): String? {
            val upper = key.uppercase(Locale.ROOT)
            return comments[upper]
        }

        fun set(key: String, value: String?) {
            val upper = key.uppercase(Locale.ROOT)
            if (value.isNullOrBlank()) {
                comments.remove(upper)
            } else {
                comments[upper] = value
            }
        }

        fun toByteArray(): ByteArray {
            val out = ByteArrayOutputStream()
            val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
            writeInt32LE(out, vendorBytes.size)
            out.write(vendorBytes)

            writeInt32LE(out, comments.size)
            for ((k, v) in comments) {
                val commentBytes = "$k=$v".toByteArray(Charsets.UTF_8)
                writeInt32LE(out, commentBytes.size)
                out.write(commentBytes)
            }
            return out.toByteArray()
        }

        companion object {
            fun parse(data: ByteArray): VorbisComments? {
                if (data.size < 8) return null
                var offset = 0
                val vendorLen = readInt32LE(data, offset)
                offset += 4
                if (vendorLen < 0 || offset + vendorLen > data.size) return null
                val vendor = String(data, offset, vendorLen, Charsets.UTF_8)
                offset += vendorLen

                if (offset + 4 > data.size) return VorbisComments(vendor, LinkedHashMap())
                val commentCount = readInt32LE(data, offset)
                offset += 4
                if (commentCount < 0) return VorbisComments(vendor, LinkedHashMap())

                val map = LinkedHashMap<String, String>()
                for (i in 0 until commentCount) {
                    if (offset + 4 > data.size) break
                    val commentLen = readInt32LE(data, offset)
                    offset += 4
                    if (commentLen < 0 || offset + commentLen > data.size) break
                    val commentStr = String(data, offset, commentLen, Charsets.UTF_8)
                    offset += commentLen

                    val eqIdx = commentStr.indexOf('=')
                    if (eqIdx > 0) {
                        val key = commentStr.substring(0, eqIdx).trim().uppercase(Locale.ROOT)
                        val value = commentStr.substring(eqIdx + 1).trim()
                        if (!map.containsKey(key)) {
                            map[key] = value
                        }
                    }
                }
                return VorbisComments(vendor, map)
            }
        }
    }

    fun isFlacFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { input -> isFlacStream(input) }
        } catch (_: Exception) {
            false
        }
    }

    fun isFlacStream(input: InputStream): Boolean {
        return try {
            val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
            buffered.mark(1024 * 64)
            val header = ByteArray(10)
            val read = buffered.read(header)
            buffered.reset()
            if (read < 4) return false
            if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
            ) {
                return true
            }
            if (read >= 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reads metadata tags from an InputStream (works seamlessly with ContentResolver / Uri).
     */
    fun readMetadata(input: InputStream): TagEditor.FileMetadata? {
        return try {
            val blocks = readMetadataBlocksFromStream(input) ?: return null
            extractMetadataFromBlocks(blocks)
        } catch (e: Exception) {
            MuseLog.w("FlacMetadataParser", "Failed to read FLAC metadata from stream", e)
            null
        }
    }

    fun readMetadata(file: File): TagEditor.FileMetadata? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            FileInputStream(file).use { input -> readMetadata(input) }
        } catch (e: Exception) {
            MuseLog.w("FlacMetadataParser", "Failed to read FLAC metadata from ${file.name}", e)
            null
        }
    }

    fun readArtwork(input: InputStream): ByteArray? {
        return try {
            val blocks = readMetadataBlocksFromStream(input) ?: return null
            extractArtworkFromBlocks(blocks)
        } catch (e: Exception) {
            MuseLog.w("FlacMetadataParser", "Failed to read FLAC artwork from stream", e)
            null
        }
    }

    fun readArtwork(file: File): ByteArray? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            FileInputStream(file).use { input -> readArtwork(input) }
        } catch (e: Exception) {
            MuseLog.w("FlacMetadataParser", "Failed to read FLAC artwork from ${file.name}", e)
            null
        }
    }

    fun readArtworkMime(input: InputStream): String? {
        val art = readArtwork(input) ?: return null
        return detectMimeType(art)
    }

    fun readArtworkMime(file: File): String? {
        val art = readArtwork(file) ?: return null
        return detectMimeType(art)
    }

    fun readLyrics(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            FileInputStream(file).use { input ->
                val blocks = readMetadataBlocksFromStream(input) ?: return null
                val vorbisBlock = blocks.firstOrNull { it.blockType == BLOCK_VORBIS_COMMENT } ?: return null
                val comments = VorbisComments.parse(vorbisBlock.data) ?: return null
                comments.get("LYRICS") ?: comments.get("UNSYNCEDLYRICS")
            }
        } catch (e: Exception) {
            MuseLog.w("FlacMetadataParser", "Failed to read FLAC lyrics from ${file.name}", e)
            null
        }
    }

    fun writeMetadata(
        inputFile: File,
        outputFile: File,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null,
        albumArtist: String? = null,
        trackNumber: Int? = null,
        lyrics: String? = null
    ): Boolean {
        if (!inputFile.exists() || !inputFile.canRead()) return false
        return try {
            val structure = readFullFlacStructure(inputFile) ?: return false
            val flacOffset = structure.flacOffset
            val blocks = structure.blocks
            val audioFrameOffset = structure.audioFrameOffset

            val updatedBlocks = blocks.toMutableList()
            val vorbisIdx = updatedBlocks.indexOfFirst { it.blockType == BLOCK_VORBIS_COMMENT }

            val currentComments = if (vorbisIdx >= 0) {
                VorbisComments.parse(updatedBlocks[vorbisIdx].data) ?: VorbisComments("Muse", LinkedHashMap())
            } else {
                VorbisComments("Muse", LinkedHashMap())
            }

            title?.let { currentComments.set("TITLE", it) }
            artist?.let { currentComments.set("ARTIST", it) }
            album?.let { currentComments.set("ALBUM", it) }
            year?.let { currentComments.set("DATE", it.toString()) }
            genre?.let { currentComments.set("GENRE", it) }
            albumArtist?.let { currentComments.set("ALBUMARTIST", it) }
            trackNumber?.let { currentComments.set("TRACKNUMBER", it.toString()) }
            lyrics?.let {
                if (it.isBlank()) {
                    currentComments.set("LYRICS", null)
                    currentComments.set("UNSYNCEDLYRICS", null)
                } else {
                    currentComments.set("LYRICS", it)
                }
            }

            val newVorbisBytes = currentComments.toByteArray()
            val newVorbisBlock = FlacBlock(
                isLast = false,
                blockType = BLOCK_VORBIS_COMMENT,
                length = newVorbisBytes.size,
                data = newVorbisBytes
            )

            if (vorbisIdx >= 0) {
                updatedBlocks[vorbisIdx] = newVorbisBlock
            } else {
                val insertPos = if (updatedBlocks.isNotEmpty() && updatedBlocks[0].blockType == BLOCK_STREAMINFO) 1 else 0
                updatedBlocks.add(insertPos, newVorbisBlock)
            }

            writeFlacFile(inputFile, outputFile, flacOffset, updatedBlocks, audioFrameOffset)
        } catch (e: Exception) {
            MuseLog.e("FlacMetadataParser", "Failed to write metadata to ${inputFile.name}", e)
            false
        }
    }

    fun writeArtwork(
        inputFile: File,
        outputFile: File,
        artworkBytes: ByteArray?,
        mimeType: String? = "image/jpeg"
    ): Boolean {
        if (!inputFile.exists() || !inputFile.canRead()) return false
        return try {
            val structure = readFullFlacStructure(inputFile) ?: return false
            val flacOffset = structure.flacOffset
            val blocks = structure.blocks
            val audioFrameOffset = structure.audioFrameOffset

            val updatedBlocks = blocks.toMutableList()
            updatedBlocks.removeAll { it.blockType == BLOCK_PICTURE }

            val vorbisIdx = updatedBlocks.indexOfFirst { it.blockType == BLOCK_VORBIS_COMMENT }
            if (vorbisIdx >= 0) {
                val comments = VorbisComments.parse(updatedBlocks[vorbisIdx].data)
                if (comments != null) {
                    var changed = false
                    if (comments.get("METADATA_BLOCK_PICTURE") != null) {
                        comments.set("METADATA_BLOCK_PICTURE", null)
                        changed = true
                    }
                    if (comments.get("COVERART") != null) {
                        comments.set("COVERART", null)
                        changed = true
                    }
                    if (comments.get("COVERARTMIME") != null) {
                        comments.set("COVERARTMIME", null)
                        changed = true
                    }
                    if (changed) {
                        val newBytes = comments.toByteArray()
                        updatedBlocks[vorbisIdx] = FlacBlock(
                            isLast = false,
                            blockType = BLOCK_VORBIS_COMMENT,
                            length = newBytes.size,
                            data = newBytes
                        )
                    }
                }
            }

            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                val actualMime = mimeType?.takeIf { it.isNotBlank() } ?: detectMimeType(artworkBytes)
                val dims = detectImageDimensions(artworkBytes)
                val picBlockData = buildPictureBlockData(
                    pictureType = 3, // Front cover
                    mimeType = actualMime,
                    description = "",
                    width = dims.first,
                    height = dims.second,
                    colorDepth = 24,
                    numColors = 0,
                    imageBytes = artworkBytes
                )

                val newPicBlock = FlacBlock(
                    isLast = false,
                    blockType = BLOCK_PICTURE,
                    length = picBlockData.size,
                    data = picBlockData
                )

                val insertIdx = updatedBlocks.indexOfLast { it.blockType == BLOCK_VORBIS_COMMENT }
                    .takeIf { it >= 0 }?.plus(1)
                    ?: (if (updatedBlocks.isNotEmpty() && updatedBlocks[0].blockType == BLOCK_STREAMINFO) 1 else 0)

                updatedBlocks.add(insertIdx.coerceAtMost(updatedBlocks.size), newPicBlock)
            }

            writeFlacFile(inputFile, outputFile, flacOffset, updatedBlocks, audioFrameOffset)
        } catch (e: Exception) {
            MuseLog.e("FlacMetadataParser", "Failed to write artwork to ${inputFile.name}", e)
            false
        }
    }

    fun writeLyrics(inputFile: File, outputFile: File, lyrics: String): Boolean {
        return writeMetadata(inputFile, outputFile, lyrics = lyrics)
    }

    // -------------------------------------------------------------
    // Internal extraction helpers
    // -------------------------------------------------------------

    private fun extractMetadataFromBlocks(blocks: List<FlacBlock>): TagEditor.FileMetadata? {
        val vorbisBlock = blocks.firstOrNull { it.blockType == BLOCK_VORBIS_COMMENT } ?: return null
        val comments = VorbisComments.parse(vorbisBlock.data) ?: return null

        val title = comments.get("TITLE")?.ifBlank { null }
        val artist = comments.get("ARTIST")?.ifBlank { null }
            ?: comments.get("PERFORMER")?.ifBlank { null }
            ?: comments.get("ARTISTS")?.ifBlank { null }
        val album = comments.get("ALBUM")?.ifBlank { null }
        val year = (comments.get("DATE") ?: comments.get("YEAR") ?: comments.get("ORIGINALDATE"))
            ?.trim()?.take(4)?.toIntOrNull()
        val genre = comments.get("GENRE")?.ifBlank { null }
        val albumArtist = comments.get("ALBUMARTIST")?.ifBlank { null }
            ?: comments.get("ALBUM ARTIST")?.ifBlank { null }
            ?: comments.get("ALBUM_ARTIST")?.ifBlank { null }
        val trackNumber = (comments.get("TRACKNUMBER") ?: comments.get("TRACK"))
            ?.trim()?.substringBefore('/')?.toIntOrNull()

        return TagEditor.FileMetadata(
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre,
            albumArtist = albumArtist,
            trackNumber = trackNumber
        )
    }

    private fun extractArtworkFromBlocks(blocks: List<FlacBlock>): ByteArray? {
        // 1. Check native Block 6 (PICTURE)
        val picBlocks = blocks.filter { it.blockType == BLOCK_PICTURE }
        val frontCover = picBlocks.mapNotNull { parsePictureBlock(it.data) }
            .firstOrNull { it.pictureType == 3 }
            ?: picBlocks.mapNotNull { parsePictureBlock(it.data) }.firstOrNull()

        if (frontCover != null && frontCover.data.isNotEmpty()) {
            return frontCover.data
        }

        // 2. Check Vorbis Comment tags for base64 picture blocks or coverart
        val vorbisBlock = blocks.firstOrNull { it.blockType == BLOCK_VORBIS_COMMENT }
        if (vorbisBlock != null) {
            val comments = VorbisComments.parse(vorbisBlock.data)
            if (comments != null) {
                val mbpBase64 = comments.get("METADATA_BLOCK_PICTURE")
                if (!mbpBase64.isNullOrBlank()) {
                    val decoded = decodeBase64Safe(mbpBase64)
                    if (decoded != null) {
                        val pic = parsePictureBlock(decoded)
                        if (pic != null && pic.data.isNotEmpty()) {
                            return pic.data
                        }
                    }
                }

                val coverartBase64 = comments.get("COVERART")
                if (!coverartBase64.isNullOrBlank()) {
                    val decoded = decodeBase64Safe(coverartBase64)
                    if (decoded != null && decoded.isNotEmpty()) {
                        return decoded
                    }
                }
            }
        }

        return null
    }

    private fun readMetadataBlocksFromStream(input: InputStream): List<FlacBlock>? {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)

        // 1. Check/skip header until 'fLaC' magic
        val header = ByteArray(10)
        var read = readBytesFully(buffered, header, 0, 4)
        if (read < 4) return null

        if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            // Skip ID3v2 header
            readBytesFully(buffered, header, 4, 6)
            val id3Len = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            skipBytes(buffered, id3Len.toLong())

            val magicCheck = ByteArray(4)
            if (readBytesFully(buffered, magicCheck, 0, 4) != 4 ||
                String(magicCheck, Charsets.US_ASCII) != "fLaC"
            ) {
                return null
            }
        } else if (header[0] != 'f'.code.toByte() || header[1] != 'L'.code.toByte() ||
            header[2] != 'a'.code.toByte() || header[3] != 'C'.code.toByte()
        ) {
            return null
        }

        // 2. Read metadata blocks sequentially
        val blocks = mutableListOf<FlacBlock>()
        var isLast = false

        while (!isLast) {
            val headerBytes = ByteArray(4)
            if (readBytesFully(buffered, headerBytes, 0, 4) < 4) break
            val b0 = headerBytes[0].toInt() and 0xFF
            isLast = (b0 and 0x80) != 0
            val blockType = b0 and 0x7F

            val length = ((headerBytes[1].toInt() and 0xFF) shl 16) or
                ((headerBytes[2].toInt() and 0xFF) shl 8) or
                (headerBytes[3].toInt() and 0xFF)

            if (length < 0 || length > 30 * 1024 * 1024) break // Safety cap: 30MB per block

            val data = ByteArray(length)
            if (readBytesFully(buffered, data, 0, length) < length) break

            blocks.add(FlacBlock(isLast, blockType, length, data))
        }

        return blocks
    }

    private data class FlacStructure(
        val flacOffset: Long,
        val blocks: List<FlacBlock>,
        val audioFrameOffset: Long
    )

    private fun readFullFlacStructure(file: File): FlacStructure? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val flacOffset = findFlacHeaderOffset(raf)
                if (flacOffset < 0) return null
                raf.seek(flacOffset + 4) // skip 'fLaC'

                val blocks = mutableListOf<FlacBlock>()
                var isLast = false

                while (!isLast && raf.filePointer < raf.length()) {
                    val headerByte = raf.read()
                    if (headerByte == -1) break
                    isLast = (headerByte and 0x80) != 0
                    val blockType = headerByte and 0x7F

                    val lenB0 = raf.read()
                    val lenB1 = raf.read()
                    val lenB2 = raf.read()
                    if (lenB0 == -1 || lenB1 == -1 || lenB2 == -1) break
                    val length = (lenB0 shl 16) or (lenB1 shl 8) or lenB2

                    if (length < 0 || raf.filePointer + length > raf.length()) {
                        break
                    }

                    val data = ByteArray(length)
                    raf.readFully(data)
                    blocks.add(FlacBlock(isLast, blockType, length, data))
                }

                val audioFrameOffset = raf.filePointer
                FlacStructure(flacOffset, blocks, audioFrameOffset)
            }
        } catch (e: Exception) {
            MuseLog.w("FlacMetadataParser", "Failed to read FLAC structure from ${file.name}", e)
            null
        }
    }

    private fun writeFlacFile(
        inputFile: File,
        outputFile: File,
        flacOffset: Long,
        blocks: List<FlacBlock>,
        audioFrameOffset: Long
    ): Boolean {
        val sameFile = inputFile.canonicalPath == outputFile.canonicalPath
        val targetTemp = File(outputFile.parentFile, "${outputFile.name}.tmp_${System.currentTimeMillis()}")

        try {
            FileOutputStream(targetTemp).use { out ->
                out.write("fLaC".toByteArray(Charsets.US_ASCII))

                for (i in blocks.indices) {
                    val block = blocks[i]
                    val isLastBlock = (i == blocks.size - 1)
                    val headerByte = (if (isLastBlock) 0x80 else 0x00) or (block.blockType and 0x7F)
                    out.write(headerByte)
                    out.write((block.length ushr 16) and 0xFF)
                    out.write((block.length ushr 8) and 0xFF)
                    out.write(block.length and 0xFF)
                    out.write(block.data)
                }

                FileInputStream(inputFile).use { input ->
                    input.channel.position(audioFrameOffset)
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }

            if (sameFile) {
                if (inputFile.delete() && targetTemp.renameTo(inputFile)) {
                    return true
                }
                FileInputStream(targetTemp).use { input ->
                    FileOutputStream(inputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetTemp.delete()
                return true
            } else {
                if (outputFile.exists()) outputFile.delete()
                return targetTemp.renameTo(outputFile)
            }
        } catch (e: Exception) {
            MuseLog.e("FlacMetadataParser", "writeFlacFile failed", e)
            if (targetTemp.exists()) targetTemp.delete()
            return false
        }
    }

    private fun findFlacHeaderOffset(raf: RandomAccessFile): Long {
        raf.seek(0)
        val header = ByteArray(10)
        val read = raf.read(header)
        if (read < 4) return -1
        if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
        ) {
            return 0L
        }
        if (read >= 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            val id3Len = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            val flacOffset = 10L + id3Len
            raf.seek(flacOffset)
            val check = ByteArray(4)
            if (raf.read(check) == 4 && String(check, Charsets.US_ASCII) == "fLaC") {
                return flacOffset
            }
        }
        return -1L
    }

    private fun parsePictureBlock(data: ByteArray): FlacPicture? {
        if (data.size < 32) return null
        var offset = 0
        val picType = readInt32BE(data, offset)
        offset += 4

        val mimeLen = readInt32BE(data, offset)
        offset += 4
        if (mimeLen < 0 || offset + mimeLen > data.size) return null
        val mime = String(data, offset, mimeLen, Charsets.US_ASCII)
        offset += mimeLen

        if (offset + 4 > data.size) return null
        val descLen = readInt32BE(data, offset)
        offset += 4
        if (descLen < 0 || offset + descLen > data.size) return null
        val desc = String(data, offset, descLen, Charsets.UTF_8)
        offset += descLen

        if (offset + 20 > data.size) return null
        val width = readInt32BE(data, offset)
        offset += 4
        val height = readInt32BE(data, offset)
        offset += 4
        val colorDepth = readInt32BE(data, offset)
        offset += 4
        val numColors = readInt32BE(data, offset)
        offset += 4
        val dataLen = readInt32BE(data, offset)
        offset += 4

        if (dataLen < 0 || offset + dataLen > data.size) return null
        val picBytes = ByteArray(dataLen)
        System.arraycopy(data, offset, picBytes, 0, dataLen)

        return FlacPicture(picType, mime, desc, width, height, colorDepth, numColors, picBytes)
    }

    private fun buildPictureBlockData(
        pictureType: Int,
        mimeType: String,
        description: String,
        width: Int,
        height: Int,
        colorDepth: Int,
        numColors: Int,
        imageBytes: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeInt32BE(out, pictureType)

        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        writeInt32BE(out, mimeBytes.size)
        out.write(mimeBytes)

        val descBytes = description.toByteArray(Charsets.UTF_8)
        writeInt32BE(out, descBytes.size)
        out.write(descBytes)

        writeInt32BE(out, width)
        writeInt32BE(out, height)
        writeInt32BE(out, colorDepth)
        writeInt32BE(out, numColors)

        writeInt32BE(out, imageBytes.size)
        out.write(imageBytes)

        return out.toByteArray()
    }

    private fun detectImageDimensions(bytes: ByteArray): Pair<Int, Int> {
        try {
            if (bytes.size >= 24 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
            ) {
                val width = readInt32BE(bytes, 16)
                val height = readInt32BE(bytes, 20)
                return Pair(width, height)
            }
            if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                var offset = 2
                while (offset + 4 < bytes.size) {
                    if (bytes[offset] != 0xFF.toByte()) break
                    val marker = bytes[offset + 1].toInt() and 0xFF
                    if (marker == 0xD9 || marker == 0xDA) break
                    val segmentLength = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 3].toInt() and 0xFF)
                    if (marker == 0xC0 || marker == 0xC2) {
                        if (offset + 9 < bytes.size) {
                            val height = ((bytes[offset + 5].toInt() and 0xFF) shl 8) or (bytes[offset + 6].toInt() and 0xFF)
                            val width = ((bytes[offset + 7].toInt() and 0xFF) shl 8) or (bytes[offset + 8].toInt() and 0xFF)
                            return Pair(width, height)
                        }
                    }
                    offset += 2 + segmentLength
                }
            }
        } catch (_: Exception) {
        }
        return Pair(500, 500)
    }

    private fun detectMimeType(bytes: ByteArray): String = when {
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun decodeBase64Safe(encoded: String): ByteArray? {
        return try {
            Base64.getDecoder().decode(encoded.trim().replace("\r", "").replace("\n", ""))
        } catch (_: Exception) {
            try {
                Base64.getMimeDecoder().decode(encoded.trim())
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun readBytesFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, offset + total, length - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private fun skipBytes(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readInt32LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeInt32LE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun writeInt32BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
