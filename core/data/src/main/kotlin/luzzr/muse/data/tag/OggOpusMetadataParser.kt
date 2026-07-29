package luzzr.muse.data.tag

import androidx.core.graphics.scale
import luzzr.muse.core.log.MuseLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal object OggOpusMetadataParser {

    fun calculateOggCrc(data: ByteArray, start: Int, length: Int): Int {
        var crc = 0
        for (i in 0 until length) {
            val byte = data[start + i].toInt() and 0xFF
            crc = crc xor (byte shl 24)
            for (j in 0 until 8) {
                if ((crc and 0x80000000.toInt()) != 0) {
                    crc = (crc shl 1) xor 0x04C11DB7
                } else {
                    crc = crc shl 1
                }
            }
        }
        return crc
    }

    class OggPage(
        val headerType: Byte,
        val granulePos: Long,
        val serialNumber: Int,
        val sequenceNumber: Int,
        val segments: ByteArray,
        val payload: ByteArray
    ) {
        fun toByteArray(): ByteArray {
            val headerSize = 27 + segments.size
            val pageSize = headerSize + payload.size
            val result = ByteArray(pageSize)

            result[0] = 'O'.code.toByte()
            result[1] = 'g'.code.toByte()
            result[2] = 'g'.code.toByte()
            result[3] = 'S'.code.toByte()
            result[4] = 0 // version
            result[5] = headerType

            for (i in 0..7) {
                result[6 + i] = ((granulePos ushr (i * 8)) and 0xFF).toByte()
            }

            result[14] = (serialNumber and 0xFF).toByte()
            result[15] = ((serialNumber ushr 8) and 0xFF).toByte()
            result[16] = ((serialNumber ushr 16) and 0xFF).toByte()
            result[17] = ((serialNumber ushr 24) and 0xFF).toByte()

            result[18] = (sequenceNumber and 0xFF).toByte()
            result[19] = ((sequenceNumber ushr 8) and 0xFF).toByte()
            result[20] = ((sequenceNumber ushr 16) and 0xFF).toByte()
            result[21] = ((sequenceNumber ushr 24) and 0xFF).toByte()

            result[22] = 0
            result[23] = 0
            result[24] = 0
            result[25] = 0

            result[26] = segments.size.toByte()
            System.arraycopy(segments, 0, result, 27, segments.size)
            System.arraycopy(payload, 0, result, headerSize, payload.size)

            val crc = calculateOggCrc(result, 0, result.size)
            result[22] = (crc and 0xFF).toByte()
            result[23] = ((crc ushr 8) and 0xFF).toByte()
            result[24] = ((crc ushr 16) and 0xFF).toByte()
            result[25] = ((crc ushr 24) and 0xFF).toByte()

            return result
        }
    }

    fun readPage(input: FileInputStream): OggPage? {
        val header = ByteArray(27)
        val readHeader = input.read(header)
        if (readHeader < 27) return null
        if (header[0] != 'O'.code.toByte() || header[1] != 'g'.code.toByte() ||
            header[2] != 'g'.code.toByte() || header[3] != 'S'.code.toByte()
        ) {
            return null
        }

        val headerType = header[5]

        var granulePos = 0L
        for (i in 0..7) {
            granulePos = granulePos or ((header[6 + i].toLong() and 0xFF) shl (i * 8))
        }

        val serialNumber = (header[14].toInt() and 0xFF) or
            ((header[15].toInt() and 0xFF) shl 8) or
            ((header[16].toInt() and 0xFF) shl 16) or
            ((header[17].toInt() and 0xFF) shl 24)

        val sequenceNumber = (header[18].toInt() and 0xFF) or
            ((header[19].toInt() and 0xFF) shl 8) or
            ((header[20].toInt() and 0xFF) shl 16) or
            ((header[21].toInt() and 0xFF) shl 24)

        val pageSegmentsCount = header[26].toInt() and 0xFF
        val segments = ByteArray(pageSegmentsCount)
        if (pageSegmentsCount > 0) {
            val readSeg = input.read(segments)
            if (readSeg < pageSegmentsCount) return null
        }

        var payloadSize = 0
        for (b in segments) {
            payloadSize += b.toInt() and 0xFF
        }

        val payload = ByteArray(payloadSize)
        if (payloadSize > 0) {
            val readPayload = input.read(payload)
            if (readPayload < payloadSize) return null
        }

        return OggPage(headerType, granulePos, serialNumber, sequenceNumber, segments, payload)
    }

    class OpusTags(
        var vendor: String,
        val comments: LinkedHashMap<String, String>
    ) {
        companion object {
            fun parse(payload: ByteArray): OpusTags? {
                if (payload.size < 8) return null
                val prefix = String(payload, 0, 8, Charsets.US_ASCII)
                if (prefix != "OpusTags") return null

                var offset = 8
                if (offset + 4 > payload.size) return null
                val vendorLen = readInt32LE(payload, offset)
                offset += 4
                if (offset + vendorLen > payload.size) return null
                val vendor = String(payload, offset, vendorLen, Charsets.UTF_8)
                offset += vendorLen

                if (offset + 4 > payload.size) return null
                val commentCount = readInt32LE(payload, offset)
                offset += 4

                val comments = LinkedHashMap<String, String>()
                for (i in 0 until commentCount) {
                    if (offset + 4 > payload.size) break
                    val commentLen = readInt32LE(payload, offset)
                    offset += 4
                    if (offset + commentLen > payload.size) break
                    val commentStr = String(payload, offset, commentLen, Charsets.UTF_8)
                    offset += commentLen

                    val equalsIndex = commentStr.indexOf('=')
                    if (equalsIndex > 0) {
                        val key = commentStr.substring(0, equalsIndex).uppercase()
                        val value = commentStr.substring(equalsIndex + 1)
                        comments[key] = value
                    }
                }

                return OpusTags(vendor, comments)
            }

            private fun readInt32LE(data: ByteArray, offset: Int): Int {
                return (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16) or
                    ((data[offset + 3].toInt() and 0xFF) shl 24)
            }
        }

        fun toByteArray(): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write("OpusTags".toByteArray(Charsets.US_ASCII))

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

        private fun writeInt32LE(out: java.io.ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }
    }

    fun isOggOpusFile(file: File): Boolean {
        if (!file.exists() || file.length() < 64) return false
        return try {
            FileInputStream(file).use { input ->
                val page0 = readPage(input) ?: return false
                page0.payload.size >= 8 && String(page0.payload, 0, 8, Charsets.US_ASCII) == "OpusHead"
            }
        } catch (_: Exception) {
            false
        }
    }

    fun readMetadata(file: File): TagEditor.FileMetadata? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            FileInputStream(file).use { input ->
                val page0 = readPage(input) ?: return null
                if (page0.payload.size < 8 || String(page0.payload, 0, 8, Charsets.US_ASCII) != "OpusHead") {
                    return null
                }
                val page1 = readPage(input) ?: return null
                val opusTags = OpusTags.parse(page1.payload) ?: return null

                TagEditor.FileMetadata(
                    title = opusTags.comments["TITLE"],
                    artist = opusTags.comments["ARTIST"],
                    album = opusTags.comments["ALBUM"],
                    year = opusTags.comments["DATE"]?.take(4)?.toIntOrNull()
                        ?: opusTags.comments["YEAR"]?.take(4)?.toIntOrNull(),
                    genre = opusTags.comments["GENRE"]
                )
            }
        } catch (e: Exception) {
            MuseLog.w("OggOpusMetadataParser", "Failed to read Ogg Opus metadata from ${file.name}", e)
            null
        }
    }

    fun readArtwork(file: File): ByteArray? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            FileInputStream(file).use { input ->
                val page0 = readPage(input) ?: return null
                if (page0.payload.size < 8 || String(page0.payload, 0, 8, Charsets.US_ASCII) != "OpusHead") {
                    return null
                }
                val page1 = readPage(input) ?: return null
                val opusTags = OpusTags.parse(page1.payload) ?: return null
                val base64Str = opusTags.comments["METADATA_BLOCK_PICTURE"] ?: return null
                val blockBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)

                var offset = 0
                if (offset + 4 > blockBytes.size) return null
                val type = readInt32BE(blockBytes, offset)
                offset += 4

                if (offset + 4 > blockBytes.size) return null
                val mimeLen = readInt32BE(blockBytes, offset)
                offset += 4

                offset += mimeLen

                if (offset + 4 > blockBytes.size) return null
                val descLen = readInt32BE(blockBytes, offset)
                offset += 4

                offset += descLen

                offset += 4 // width
                offset += 4 // height
                offset += 4 // depth
                offset += 4 // colors indexed

                if (offset + 4 > blockBytes.size) return null
                val picLen = readInt32BE(blockBytes, offset)
                offset += 4

                if (offset + picLen > blockBytes.size) return null
                val picBytes = ByteArray(picLen)
                System.arraycopy(blockBytes, offset, picBytes, 0, picLen)
                picBytes
            }
        } catch (e: Exception) {
            MuseLog.w("OggOpusMetadataParser", "Failed to read Ogg Opus artwork from ${file.name}", e)
            null
        }
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    fun writeMetadata(
        sourceFile: File,
        targetFile: File,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null
    ): Boolean {
        if (!sourceFile.exists()) return false
        val tempFile = File(targetFile.parentFile, ".${targetFile.name}.ogg-opus.tmp")
        try {
            var success = false
            var firstAudioPageStartPos = -1L

            FileInputStream(sourceFile).use { input ->
                val page0 = readPage(input) ?: return false
                if (page0.payload.size < 8 || String(page0.payload, 0, 8, Charsets.US_ASCII) != "OpusHead") {
                    return false
                }

                val page1 = readPage(input) ?: return false
                val opusTags = OpusTags.parse(page1.payload) ?: OpusTags("libopus", LinkedHashMap())

                title?.let { opusTags.comments["TITLE"] = it }
                artist?.let { opusTags.comments["ARTIST"] = it }
                album?.let { opusTags.comments["ALBUM"] = it }
                year?.let { opusTags.comments["DATE"] = it.toString() }
                genre?.let { opusTags.comments["GENRE"] = it }

                val newTagsPayload = opusTags.toByteArray()
                val payloadSize = newTagsPayload.size
                if (payloadSize >= 65025) {
                    MuseLog.e("OggOpusMetadataParser", "OpusTags payload exceeds page limit: size=$payloadSize")
                    return false
                }

                val segmentCount = payloadSize / 255 + 1
                val segments = ByteArray(segmentCount)
                for (i in 0 until segmentCount - 1) {
                    segments[i] = 255.toByte()
                }
                segments[segmentCount - 1] = (payloadSize % 255).toByte()

                val newPage1 = OggPage(
                    headerType = 0,
                    granulePos = 0L,
                    serialNumber = page0.serialNumber,
                    sequenceNumber = 1,
                    segments = segments,
                    payload = newTagsPayload
                )

                while (true) {
                    val pos = input.channel.position()
                    val page = readPage(input) ?: break
                    if (page.granulePos > 0) {
                        firstAudioPageStartPos = pos
                        break
                    }
                }

                FileOutputStream(tempFile).use { output ->
                    output.write(page0.toByteArray())
                    output.write(newPage1.toByteArray())
                    if (firstAudioPageStartPos != -1L) {
                        input.channel.transferTo(firstAudioPageStartPos, sourceFile.length() - firstAudioPageStartPos, output.channel)
                    }
                }
                success = true
            }

            if (success) {
                if (tempFile.length() <= 0L) {
                    tempFile.delete()
                    return false
                }
                if (targetFile.exists() && !targetFile.delete()) {
                    tempFile.delete()
                    return false
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.delete()
                    return false
                }
                return true
            }
        } catch (e: Exception) {
            MuseLog.e("OggOpusMetadataParser", "Failed to write Ogg Opus metadata", e)
            tempFile.delete()
        }
        return false
    }

    fun writeArtwork(sourceFile: File, targetFile: File, artworkBytes: ByteArray, mimeType: String): Boolean {
        if (!sourceFile.exists()) return false
        val tempFile = File(targetFile.parentFile, ".${targetFile.name}.ogg-opus-art.tmp")
        try {
            var success = false
            var firstAudioPageStartPos = -1L

            FileInputStream(sourceFile).use { input ->
                val page0 = readPage(input) ?: return false
                if (page0.payload.size < 8 || String(page0.payload, 0, 8, Charsets.US_ASCII) != "OpusHead") {
                    return false
                }

                val page1 = readPage(input) ?: return false
                val opusTags = OpusTags.parse(page1.payload) ?: OpusTags("libopus", LinkedHashMap())

                val processedArtworkBytes = compressImageIfNeeded(artworkBytes)
                val resolvedMime = detectMimeTypeFromBytes(processedArtworkBytes, mimeType)
                val blockBytes = makeMetadataBlockPicture(processedArtworkBytes, resolvedMime)
                val base64Str = android.util.Base64.encodeToString(blockBytes, android.util.Base64.NO_WRAP)

                opusTags.comments["METADATA_BLOCK_PICTURE"] = base64Str
                val newTagsPayload = opusTags.toByteArray()
                val payloadSize = newTagsPayload.size
                if (payloadSize >= 65025) {
                    MuseLog.e("OggOpusMetadataParser", "OpusTags payload exceeds page limit after compression: size=$payloadSize")
                    return false
                }

                val segmentCount = payloadSize / 255 + 1
                val segments = ByteArray(segmentCount)
                for (i in 0 until segmentCount - 1) {
                    segments[i] = 255.toByte()
                }
                segments[segmentCount - 1] = (payloadSize % 255).toByte()

                val newPage1 = OggPage(
                    headerType = 0,
                    granulePos = 0L,
                    serialNumber = page0.serialNumber,
                    sequenceNumber = 1,
                    segments = segments,
                    payload = newTagsPayload
                )

                while (true) {
                    val pos = input.channel.position()
                    val page = readPage(input) ?: break
                    if (page.granulePos > 0) {
                        firstAudioPageStartPos = pos
                        break
                    }
                }

                FileOutputStream(tempFile).use { output ->
                    output.write(page0.toByteArray())
                    output.write(newPage1.toByteArray())
                    if (firstAudioPageStartPos != -1L) {
                        input.channel.transferTo(firstAudioPageStartPos, sourceFile.length() - firstAudioPageStartPos, output.channel)
                    }
                }
                success = true
            }

            if (success) {
                if (tempFile.length() <= 0L) {
                    tempFile.delete()
                    return false
                }
                if (targetFile.exists() && !targetFile.delete()) {
                    tempFile.delete()
                    return false
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.delete()
                    return false
                }
                return true
            }
        } catch (e: Exception) {
            MuseLog.e("OggOpusMetadataParser", "Failed to write Ogg Opus artwork", e)
            tempFile.delete()
        }
        return false
    }

    private fun compressImageIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size <= 28000) return bytes
        try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            var quality = 80
            var maxDim = 400
            var result = bytes

            while (result.size > 28000 && (maxDim > 100 || quality > 30)) {
                val width = bitmap.width
                val height = bitmap.height
                if (width <= 0 || height <= 0) break

                val newWidth: Int
                val newHeight: Int
                if (width > height) {
                    newWidth = maxDim
                    newHeight = (height * maxDim) / width
                } else {
                    newHeight = maxDim
                    newWidth = (width * maxDim) / height
                }

                val scaled = bitmap.scale(newWidth, newHeight)
                val out = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                scaled.recycle()
                result = out.toByteArray()

                if (result.size > 28000) {
                    if (quality > 40) {
                        quality -= 15
                    } else {
                        maxDim -= 100
                        quality = 80
                    }
                }
            }
            bitmap.recycle()
            return result
        } catch (e: Throwable) {
            MuseLog.e("OggOpusMetadataParser", "Failed to compress artwork image", e)
        }
        return bytes
    }

    private fun detectMimeTypeFromBytes(bytes: ByteArray, defaultMime: String): String = when {
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> defaultMime
    }

    private fun detectImageDimensions(bytes: ByteArray): Pair<Int, Int> {
        try {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return Pair(options.outWidth, options.outHeight)
            }
        } catch (_: Throwable) {}

        try {
            if (bytes.size >= 24 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
            ) {
                val width = ((bytes[16].toInt() and 0xFF) shl 24) or
                    ((bytes[17].toInt() and 0xFF) shl 16) or
                    ((bytes[18].toInt() and 0xFF) shl 8) or
                    (bytes[19].toInt() and 0xFF)
                val height = ((bytes[20].toInt() and 0xFF) shl 24) or
                    ((bytes[21].toInt() and 0xFF) shl 16) or
                    ((bytes[22].toInt() and 0xFF) shl 8) or
                    (bytes[23].toInt() and 0xFF)
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
                            val height = ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                                (bytes[offset + 6].toInt() and 0xFF)
                            val width = ((bytes[offset + 7].toInt() and 0xFF) shl 8) or
                                (bytes[offset + 8].toInt() and 0xFF)
                            return Pair(width, height)
                        }
                    }
                    offset += 2 + segmentLength
                }
            }
        } catch (_: Exception) {}
        return Pair(0, 0)
    }

    private fun makeMetadataBlockPicture(artworkBytes: ByteArray, mimeType: String): ByteArray {
        val dims = detectImageDimensions(artworkBytes)
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        val out = java.io.ByteArrayOutputStream()
        writeInt32BE(out, 3)
        writeInt32BE(out, mimeBytes.size)
        out.write(mimeBytes)
        writeInt32BE(out, 0)
        writeInt32BE(out, dims.first)
        writeInt32BE(out, dims.second)
        writeInt32BE(out, 24)
        writeInt32BE(out, 0)
        writeInt32BE(out, artworkBytes.size)
        out.write(artworkBytes)
        return out.toByteArray()
    }

    private fun writeInt32BE(out: java.io.ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
