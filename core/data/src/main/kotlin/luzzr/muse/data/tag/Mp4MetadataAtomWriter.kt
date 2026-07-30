package luzzr.muse.data.tag

import luzzr.muse.core.log.MuseLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

internal object Mp4MetadataAtomWriter {

    fun writeTextMetadata(
        file: File,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null
    ): Boolean {
        return writeTextMetadata(
            sourceFile = file,
            targetFile = file,
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre
        )
    }

    fun writeTextMetadata(
        sourceFile: File,
        targetFile: File,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        genre: String? = null
    ): Boolean {
        val updates = linkedMapOf<Int, Any?>()
        title?.let { updates[ATOM_TITLE] = it }
        artist?.let { updates[ATOM_ARTIST] = it }
        album?.let { updates[ATOM_ALBUM] = it }
        genre?.let { updates[ATOM_GENRE] = it }
        year?.let { updates[ATOM_YEAR] = it.toString() }
        return writeMetadata(sourceFile, targetFile, updates)
    }

    fun writeArtwork(sourceFile: File, targetFile: File, artworkBytes: ByteArray?, mimeType: String? = null): Boolean {
        val updates = mapOf(ATOM_COVR to artworkBytes)
        return writeMetadata(sourceFile, targetFile, updates, mimeType)
    }

    fun writeMetadata(sourceFile: File, targetFile: File, updates: Map<Int, Any?>, artworkMimeType: String? = null): Boolean {
        if (updates.isEmpty()) {
            val startedAt = System.currentTimeMillis()
            if (!sourceFile.samePathAs(targetFile)) {
                sourceFile.copyTo(targetFile, overwrite = true, bufferSize = COPY_BUFFER_SIZE)
            }
            return true
        }

        return try {
            val startedAt = System.currentTimeMillis()
            val sourceLength = sourceFile.length()
            val topLevel = parseTopLevelBoxes(sourceFile, sourceLength)
            val moov = topLevel.firstOrNull { it.type == ATOM_MOOV } ?: return false
            val oldMoovSize = moov.end - moov.start
            if (oldMoovSize <= 0L || oldMoovSize > MAX_MOOV_BYTES) {
                MuseLog.e("Mp4MetadataAtomWriter", "writeMetadata: moov atom is too large: $oldMoovSize")
                return false
            }
            val oldMoov = readFileRange(sourceFile, moov.start, oldMoovSize.toInt())
            val oldMoovPayload = oldMoov.copyOfRange(moov.headerSize, oldMoov.size)

            var newMoov = makeBox(ATOM_MOOV, updateMoovPayload(oldMoovPayload, updates, artworkMimeType))
            val delta = newMoov.size - oldMoov.size
            val firstMdat = topLevel.firstOrNull { it.type == ATOM_MDAT }
            if (delta != 0 && firstMdat != null && firstMdat.start > moov.start) {
                newMoov = adjustChunkOffsets(newMoov, delta.toLong())
            }

            rewriteFileWithMoov(sourceFile, targetFile, sourceLength, moov, newMoov)
            MuseLog.w(
                "Mp4MetadataAtomWriter",
                "writeMetadata: wrote bytes=${targetFile.length()} delta=$delta ms=${System.currentTimeMillis() - startedAt}"
            )
            true
        } catch (e: OutOfMemoryError) {
            MuseLog.e("Mp4MetadataAtomWriter", "writeMetadata failed: MP4 metadata rewrite ran out of memory", e)
            false
        } catch (e: Exception) {
            MuseLog.e("Mp4MetadataAtomWriter", "writeMetadata failed", e)
            false
        }
    }

    fun readMetadata(file: File): TagEditor.FileMetadata? {
        return try {
            val fileLength = file.length()
            val topLevel = parseTopLevelBoxes(file, fileLength)
            val moov = topLevel.firstOrNull { it.type == ATOM_MOOV } ?: return null
            val oldMoovSize = moov.end - moov.start
            if (oldMoovSize <= 0L || oldMoovSize > MAX_MOOV_BYTES) return null
            val moovData = readFileRange(file, moov.start, oldMoovSize.toInt())
            val moovPayload = moovData.copyOfRange(moov.headerSize, moovData.size)

            val udta = findBoxInPayload(moovPayload, ATOM_UDTA) ?: return null
            val meta = findBoxInPayload(udta.payload(), ATOM_META) ?: return null
            val metaPayload = meta.payload()
            if (metaPayload.size < FULL_BOX_HEADER_SIZE) return null
            val metaChildren = metaPayload.copyOfRange(FULL_BOX_HEADER_SIZE, metaPayload.size)
            val ilst = findBoxInPayload(metaChildren, ATOM_ILST) ?: return null

            val ilstPayload = ilst.payload()
            val boxes = parseBoxes(ilstPayload, 0, ilstPayload.size)

            val metadata = TagEditor.FileMetadata()
            for (box in boxes) {
                val dataBox = findBoxInPayload(box.payload(), ATOM_DATA) ?: continue
                val dataPayload = dataBox.payload()
                if (dataPayload.size < 8) continue
                val type = readUInt32(dataPayload, 0).toInt()
                val valueBytes = dataPayload.copyOfRange(8, dataPayload.size)

                when (box.type) {
                    ATOM_TITLE -> if (type == DATA_TYPE_UTF8) metadata.title = String(valueBytes, Charsets.UTF_8).trim()
                    ATOM_ARTIST -> if (type == DATA_TYPE_UTF8) metadata.artist = String(valueBytes, Charsets.UTF_8).trim()
                    ATOM_ALBUM -> if (type == DATA_TYPE_UTF8) metadata.album = String(valueBytes, Charsets.UTF_8).trim()
                    ATOM_GENRE -> if (type == DATA_TYPE_UTF8) metadata.genre = String(valueBytes, Charsets.UTF_8).trim()
                    ATOM_YEAR -> if (type == DATA_TYPE_UTF8) metadata.year = String(valueBytes, Charsets.UTF_8).trim().take(4).toIntOrNull()
                    ATOM_ALBUM_ARTIST -> if (type == DATA_TYPE_UTF8) metadata.albumArtist = String(valueBytes, Charsets.UTF_8).trim()
                    ATOM_TRACK -> {
                        if (valueBytes.size >= 4) {
                            val trackNum = ((valueBytes[2].toInt() and 0xFF) shl 8) or (valueBytes[3].toInt() and 0xFF)
                            metadata.trackNumber = trackNum
                        }
                    }
                }
            }
            metadata
        } catch (e: Exception) {
            MuseLog.e("Mp4MetadataAtomWriter", "readMetadata failed", e)
            null
        }
    }

    fun readArtwork(file: File): ByteArray? {
        return try {
            val fileLength = file.length()
            val topLevel = parseTopLevelBoxes(file, fileLength)
            val moov = topLevel.firstOrNull { it.type == ATOM_MOOV } ?: return null
            val oldMoovSize = moov.end - moov.start
            if (oldMoovSize <= 0L || oldMoovSize > MAX_MOOV_BYTES) return null
            val moovData = readFileRange(file, moov.start, oldMoovSize.toInt())
            val moovPayload = moovData.copyOfRange(moov.headerSize, moovData.size)

            val udta = findBoxInPayload(moovPayload, ATOM_UDTA) ?: return null
            val meta = findBoxInPayload(udta.payload(), ATOM_META) ?: return null
            val metaPayload = meta.payload()
            if (metaPayload.size < FULL_BOX_HEADER_SIZE) return null
            val metaChildren = metaPayload.copyOfRange(FULL_BOX_HEADER_SIZE, metaPayload.size)
            val ilst = findBoxInPayload(metaChildren, ATOM_ILST) ?: return null

            val covr = findBoxInPayload(ilst.payload(), ATOM_COVR) ?: return null
            val dataBox = findBoxInPayload(covr.payload(), ATOM_DATA) ?: return null
            val dataPayload = dataBox.payload()
            if (dataPayload.size < 8) return null
            dataPayload.copyOfRange(8, dataPayload.size)
        } catch (e: Exception) {
            MuseLog.e("Mp4MetadataAtomWriter", "readArtwork failed", e)
            null
        }
    }

    fun readArtworkMime(file: File): String? {
        return try {
            val fileLength = file.length()
            val topLevel = parseTopLevelBoxes(file, fileLength)
            val moov = topLevel.firstOrNull { it.type == ATOM_MOOV } ?: return null
            val oldMoovSize = moov.end - moov.start
            if (oldMoovSize <= 0L || oldMoovSize > MAX_MOOV_BYTES) return null
            val moovData = readFileRange(file, moov.start, oldMoovSize.toInt())
            val moovPayload = moovData.copyOfRange(moov.headerSize, moovData.size)

            val udta = findBoxInPayload(moovPayload, ATOM_UDTA) ?: return null
            val meta = findBoxInPayload(udta.payload(), ATOM_META) ?: return null
            val metaPayload = meta.payload()
            if (metaPayload.size < FULL_BOX_HEADER_SIZE) return null
            val metaChildren = metaPayload.copyOfRange(FULL_BOX_HEADER_SIZE, metaPayload.size)
            val ilst = findBoxInPayload(metaChildren, ATOM_ILST) ?: return null

            val covr = findBoxInPayload(ilst.payload(), ATOM_COVR) ?: return null
            val dataBox = findBoxInPayload(covr.payload(), ATOM_DATA) ?: return null
            val dataPayload = dataBox.payload()
            if (dataPayload.size < 8) return null
            val type = readUInt32(dataPayload, 0).toInt()
            when (type) {
                13 -> "image/jpeg"
                14 -> "image/png"
                else -> {
                    val bytes = dataPayload.copyOfRange(8, dataPayload.size)
                    if (
                        bytes.size >= 4 &&
                        bytes[0] == 0x89.toByte() &&
                        bytes[1] == 0x50.toByte() &&
                        bytes[2] == 0x4E.toByte() &&
                        bytes[3] == 0x47.toByte()
                    ) {
                        "image/png"
                    } else {
                        "image/jpeg"
                    }
                }
            }
        } catch (e: Exception) {
            MuseLog.e("Mp4MetadataAtomWriter", "readArtworkMime failed", e)
            null
        }
    }

    fun getDurationMs(file: File): Long {
        return try {
            val fileLength = file.length()
            val topLevel = parseTopLevelBoxes(file, fileLength)
            val moov = topLevel.firstOrNull { it.type == ATOM_MOOV } ?: return 0L
            val oldMoovSize = moov.end - moov.start
            if (oldMoovSize <= 0L || oldMoovSize > MAX_MOOV_BYTES) return 0L
            val moovData = readFileRange(file, moov.start, oldMoovSize.toInt())
            val moovPayload = moovData.copyOfRange(moov.headerSize, moovData.size)

            val mvhd = findBoxInPayload(moovPayload, ATOM_MVHD) ?: return 0L
            val payload = mvhd.payload()
            if (payload.size < 4) return 0L
            val version = payload[0].toInt() and 0xFF

            val timescale: Long
            val duration: Long
            if (version == 1) {
                if (payload.size < 32) return 0L
                timescale = readUInt32(payload, 20)
                duration = readUInt64(payload, 24)
            } else {
                if (payload.size < 20) return 0L
                timescale = readUInt32(payload, 12)
                duration = readUInt32(payload, 16)
            }
            if (timescale <= 0L) return 0L
            (duration * 1000L) / timescale
        } catch (e: Exception) {
            MuseLog.e("Mp4MetadataAtomWriter", "getDurationMs failed", e)
            0L
        }
    }

    private fun findBoxInPayload(payload: ByteArray, targetType: Int): Mp4Box? {
        val boxes = parseBoxes(payload, 0, payload.size)
        return boxes.firstOrNull { it.type == targetType }
    }

    private fun parseTopLevelBoxes(file: File, fileLength: Long): List<FileBox> {
        if (fileLength <= 0L) return emptyList()
        val boxes = mutableListOf<FileBox>()
        RandomAccessFile(file, "r").use { input ->
            var offset = 0L
            while (offset + BOX_HEADER_SIZE <= fileLength) {
                input.seek(offset)
                val size32 = input.readUInt32()
                val type = input.readInt32()
                val headerSize: Int
                val boxSize: Long
                when (size32) {
                    0L -> {
                        headerSize = BOX_HEADER_SIZE
                        boxSize = fileLength - offset
                    }
                    1L -> {
                        if (offset + LARGE_BOX_HEADER_SIZE > fileLength) return boxes
                        headerSize = LARGE_BOX_HEADER_SIZE
                        boxSize = input.readUInt64()
                    }
                    else -> {
                        headerSize = BOX_HEADER_SIZE
                        boxSize = size32
                    }
                }
                if (boxSize < headerSize.toLong() || boxSize > fileLength - offset) return boxes
                boxes += FileBox(offset, offset + boxSize, headerSize, type)
                offset += boxSize
            }
        }
        return boxes
    }

    private fun readFileRange(file: File, start: Long, size: Int): ByteArray {
        val data = ByteArray(size)
        RandomAccessFile(file, "r").use { input ->
            input.seek(start)
            input.readFully(data)
        }
        return data
    }

    @Suppress("ThrowsCount")
    private fun rewriteFileWithMoov(sourceFile: File, targetFile: File, sourceLength: Long, moov: FileBox, newMoov: ByteArray) {
        val parent = targetFile.parentFile ?: throw IOException("MP4 target file has no parent directory")
        val stamp = System.nanoTime()
        val rewritten = File(parent, ".${targetFile.name}.muse-mp4-$stamp.tmp")
        val backup = File(parent, ".${targetFile.name}.muse-mp4-$stamp.bak")
        try {
            FileOutputStream(rewritten).use { output ->
                copyRange(sourceFile, output, 0L, moov.start)
                output.write(newMoov)
                copyRange(sourceFile, output, moov.end, sourceLength - moov.end)
                output.flush()
            }

            val expectedLength = sourceLength - (moov.end - moov.start) + newMoov.size
            if (rewritten.length() != expectedLength) {
                throw IOException("MP4 rewrite length mismatch: expected=$expectedLength actual=${rewritten.length()}")
            }

            if (!sourceFile.samePathAs(targetFile)) {
                if (targetFile.exists() && !targetFile.delete()) {
                    throw IOException("Unable to replace existing MP4 target file")
                }
                if (!rewritten.renameTo(targetFile)) {
                    throw IOException("Unable to move rewritten MP4 target into place")
                }
                return
            }

            if (!targetFile.renameTo(backup)) {
                throw IOException("Unable to stage original MP4 file for replacement")
            }
            if (!rewritten.renameTo(targetFile)) {
                backup.renameTo(targetFile)
                throw IOException("Unable to replace MP4 file after metadata rewrite")
            }
            backup.delete()
        } catch (e: Exception) {
            rewritten.delete()
            if (!targetFile.exists() && backup.exists()) {
                backup.renameTo(targetFile)
            }
            throw e
        }
    }

    private fun File.samePathAs(other: File): Boolean {
        val thisPath = runCatching { canonicalFile.path }.getOrElse { absoluteFile.path }
        val otherPath = runCatching { other.canonicalFile.path }.getOrElse { other.absoluteFile.path }
        return thisPath == otherPath
    }

    private fun copyRange(source: File, output: FileOutputStream, start: Long, length: Long) {
        if (length <= 0L) return
        RandomAccessFile(source, "r").use { input ->
            input.seek(start)
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            var remaining = length
            while (remaining > 0L) {
                val readSize = minOf(buffer.size.toLong(), remaining).toInt()
                val count = input.read(buffer, 0, readSize)
                if (count < 0) throw IOException("Unexpected EOF while rewriting MP4 file")
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    private fun updateMoovPayload(payload: ByteArray, updates: Map<Int, Any?>, artworkMimeType: String?): ByteArray {
        return replaceOrAppend(
            payload = payload,
            targetType = ATOM_UDTA,
            replace = { box -> makeBox(ATOM_UDTA, updateUdtaPayload(box.payload(), updates, artworkMimeType)) },
            append = { makeBox(ATOM_UDTA, makeMetaBox(makeIlstBox(updates, artworkMimeType))) }
        )
    }

    private fun updateUdtaPayload(payload: ByteArray, updates: Map<Int, Any?>, artworkMimeType: String?): ByteArray {
        return replaceOrAppend(
            payload = payload,
            targetType = ATOM_META,
            replace = { box -> updateMetaBox(box, updates, artworkMimeType) },
            append = { makeMetaBox(makeIlstBox(updates, artworkMimeType)) }
        )
    }

    private fun updateMetaBox(box: Mp4Box, updates: Map<Int, Any?>, artworkMimeType: String?): ByteArray {
        val payload = box.payload()
        val fullBoxHeader = if (payload.size >= FULL_BOX_HEADER_SIZE) {
            payload.copyOfRange(0, FULL_BOX_HEADER_SIZE)
        } else {
            ByteArray(FULL_BOX_HEADER_SIZE)
        }
        val children = if (payload.size >= FULL_BOX_HEADER_SIZE) {
            payload.copyOfRange(FULL_BOX_HEADER_SIZE, payload.size)
        } else {
            ByteArray(0)
        }
        var hasHdlr = false
        parseBoxes(children, 0, children.size).forEach { child ->
            if (child.type == ATOM_HDLR) hasHdlr = true
        }
        val childrenWithIlst = replaceOrAppend(
            payload = children,
            targetType = ATOM_ILST,
            replace = { ilst -> makeIlstBox(updates, artworkMimeType, ilst.payload()) },
            append = { makeIlstBox(updates, artworkMimeType) }
        )
        val output = ByteArrayOutputStream()
        output.write(fullBoxHeader)
        if (!hasHdlr) output.write(makeHdlrBox())
        output.write(childrenWithIlst)
        return makeBox(ATOM_META, output.toByteArray())
    }

    private fun makeMetaBox(ilstBox: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(ByteArray(FULL_BOX_HEADER_SIZE))
        output.write(makeHdlrBox())
        output.write(ilstBox)
        return makeBox(ATOM_META, output.toByteArray())
    }

    private fun makeIlstBox(
        updates: Map<Int, Any?>,
        artworkMimeType: String? = null,
        existingPayload: ByteArray = ByteArray(0)
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val boxes = parseBoxes(existingPayload, 0, existingPayload.size)
        var cursor = 0
        for (box in boxes) {
            if (box.start > cursor) {
                output.write(existingPayload, cursor, box.start - cursor)
            }
            if (box.type !in updates.keys) {
                output.write(existingPayload, box.start, box.end - box.start)
            }
            cursor = box.end
        }
        if (cursor < existingPayload.size) {
            output.write(existingPayload, cursor, existingPayload.size - cursor)
        }
        updates.forEach { (type, value) ->
            when (value) {
                is String -> {
                    output.write(makeTextItemBox(type, value))
                }
                is ByteArray -> {
                    output.write(makeImageItemBox(type, value, artworkMimeType))
                }
            }
        }
        return makeBox(ATOM_ILST, output.toByteArray())
    }

    private fun makeTextItemBox(type: Int, value: String): ByteArray {
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        val dataPayload = ByteArrayOutputStream().apply {
            writeUInt32(DATA_TYPE_UTF8.toLong())
            writeUInt32(0)
            write(valueBytes)
        }.toByteArray()
        return makeBox(type, makeBox(ATOM_DATA, dataPayload))
    }

    private fun makeImageItemBox(type: Int, bytes: ByteArray, mimeType: String?): ByteArray {
        val dataType = when {
            mimeType?.contains("png", ignoreCase = true) == true -> 14
            mimeType?.contains("jpeg", ignoreCase = true) == true || mimeType?.contains("jpg", ignoreCase = true) == true -> 13
            bytes.size >= 4 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> 14
            else -> 13
        }
        val dataPayload = ByteArrayOutputStream().apply {
            writeUInt32(dataType.toLong())
            writeUInt32(0)
            write(bytes)
        }.toByteArray()
        return makeBox(type, makeBox(ATOM_DATA, dataPayload))
    }

    private fun makeHdlrBox(): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            writeUInt32(0)
            writeUInt32(0)
            writeUInt32(ATOM_MDIR.toLong())
            write(ByteArray(12))
            write(0)
        }.toByteArray()
        return makeBox(ATOM_HDLR, payload)
    }

    private fun replaceOrAppend(payload: ByteArray, targetType: Int, replace: (Mp4Box) -> ByteArray, append: () -> ByteArray): ByteArray {
        val output = ByteArrayOutputStream(payload.size)
        val boxes = parseBoxes(payload, 0, payload.size)
        var found = false
        var cursor = 0
        for (box in boxes) {
            if (box.start > cursor) {
                output.write(payload, cursor, box.start - cursor)
            }
            if (box.type == targetType) {
                output.write(replace(box))
                found = true
            } else {
                output.write(payload, box.start, box.end - box.start)
            }
            cursor = box.end
        }
        if (cursor < payload.size) {
            output.write(payload, cursor, payload.size - cursor)
        }
        if (!found) output.write(append())
        return output.toByteArray()
    }

    private fun adjustChunkOffsets(moovBox: ByteArray, delta: Long): ByteArray {
        val adjusted = moovBox.copyOf()
        adjustOffsetsInContainer(adjusted, BOX_HEADER_SIZE, adjusted.size, delta)
        return adjusted
    }

    private fun adjustOffsetsInContainer(data: ByteArray, start: Int, end: Int, delta: Long) {
        for (box in parseBoxes(data, start, end)) {
            val payloadStart = box.start + box.headerSize
            when (box.type) {
                ATOM_STCO -> adjustStco(data, payloadStart, box.end, delta)
                ATOM_CO64 -> adjustCo64(data, payloadStart, box.end, delta)
                ATOM_META -> {
                    if (payloadStart + FULL_BOX_HEADER_SIZE <= box.end) {
                        adjustOffsetsInContainer(data, payloadStart + FULL_BOX_HEADER_SIZE, box.end, delta)
                    }
                }
                in CONTAINER_ATOMS -> adjustOffsetsInContainer(data, payloadStart, box.end, delta)
            }
        }
    }

    private fun adjustStco(data: ByteArray, payloadStart: Int, end: Int, delta: Long) {
        if (payloadStart + 8 > end) return
        val count = readUInt32(data, payloadStart + 4).toInt()
        var offset = payloadStart + 8
        repeat(count) {
            if (offset + 4 > end) return
            val adjusted = readUInt32(data, offset) + delta
            require(adjusted in 0..UINT_MAX) { "stco offset out of range after metadata write" }
            writeUInt32(data, offset, adjusted)
            offset += 4
        }
    }

    private fun adjustCo64(data: ByteArray, payloadStart: Int, end: Int, delta: Long) {
        if (payloadStart + 8 > end) return
        val count = readUInt32(data, payloadStart + 4).toInt()
        var offset = payloadStart + 8
        repeat(count) {
            if (offset + 8 > end) return
            val adjusted = readUInt64(data, offset) + delta
            require(adjusted >= 0) { "co64 offset out of range after metadata write" }
            writeUInt64(data, offset, adjusted)
            offset += 8
        }
    }

    private fun parseBoxes(data: ByteArray, start: Int, end: Int): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var offset = start
        while (offset + BOX_HEADER_SIZE <= end) {
            val size32 = readUInt32(data, offset)
            val type = readInt32(data, offset + 4)
            val headerSize: Int
            val boxSize: Long
            when (size32) {
                0L -> {
                    headerSize = BOX_HEADER_SIZE
                    boxSize = (end - offset).toLong()
                }
                1L -> {
                    if (offset + LARGE_BOX_HEADER_SIZE > end) return boxes
                    headerSize = LARGE_BOX_HEADER_SIZE
                    boxSize = readUInt64(data, offset + 8)
                }
                else -> {
                    headerSize = BOX_HEADER_SIZE
                    boxSize = size32
                }
            }
            if (boxSize < headerSize || boxSize > Int.MAX_VALUE || offset + boxSize > end) return boxes
            boxes += Mp4Box(data, offset, offset + boxSize.toInt(), headerSize, type)
            offset += boxSize.toInt()
        }
        return boxes
    }

    private fun makeBox(type: Int, payload: ByteArray): ByteArray {
        val size = BOX_HEADER_SIZE + payload.size
        require(size.toLong() <= UINT_MAX) { "MP4 atom is too large" }
        val result = ByteArray(size)
        result[0] = ((size ushr 24) and 0xFF).toByte()
        result[1] = ((size ushr 16) and 0xFF).toByte()
        result[2] = ((size ushr 8) and 0xFF).toByte()
        result[3] = (size and 0xFF).toByte()
        result[4] = ((type ushr 24) and 0xFF).toByte()
        result[5] = ((type ushr 16) and 0xFF).toByte()
        result[6] = ((type ushr 8) and 0xFF).toByte()
        result[7] = (type and 0xFF).toByte()
        System.arraycopy(payload, 0, result, BOX_HEADER_SIZE, payload.size)
        return result
    }

    private data class Mp4Box(
        val source: ByteArray,
        val start: Int,
        val end: Int,
        val headerSize: Int,
        val type: Int
    ) {
        fun payload(): ByteArray = source.copyOfRange(start + headerSize, end)
    }

    private data class FileBox(
        val start: Long,
        val end: Long,
        val headerSize: Int,
        val type: Int
    )

    private fun ByteArrayOutputStream.writeUInt32(value: Long) {
        write(((value ushr 24) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write((value and 0xFF).toInt())
    }

    private fun RandomAccessFile.readUInt32(): Long {
        val b1 = read()
        val b2 = read()
        val b3 = read()
        val b4 = read()
        if (b1 < 0 || b2 < 0 || b3 < 0 || b4 < 0) {
            throw IOException("Unexpected EOF while reading MP4 atom header")
        }
        return ((b1.toLong() and 0xFF) shl 24) or
            ((b2.toLong() and 0xFF) shl 16) or
            ((b3.toLong() and 0xFF) shl 8) or
            (b4.toLong() and 0xFF)
    }

    private fun RandomAccessFile.readUInt64(): Long {
        var value = 0L
        repeat(8) {
            val next = read()
            if (next < 0) throw IOException("Unexpected EOF while reading MP4 large atom header")
            value = (value shl 8) or (next.toLong() and 0xFF)
        }
        return value
    }

    private fun RandomAccessFile.readInt32(): Int = readUInt32().toInt()

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt64(data: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(8) { index ->
            value = (value shl 8) or (data[offset + index].toLong() and 0xFF)
        }
        return value
    }

    private fun readInt32(data: ByteArray, offset: Int): Int = readUInt32(data, offset).toInt()

    private fun writeUInt32(data: ByteArray, offset: Int, value: Long) {
        data[offset] = ((value ushr 24) and 0xFF).toByte()
        data[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeUInt64(data: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            data[offset + index] = ((value ushr ((7 - index) * 8)) and 0xFF).toByte()
        }
    }

    private fun fourCc(a: Int, b: Int, c: Int, d: Int): Int {
        return ((a and 0xFF) shl 24) or
            ((b and 0xFF) shl 16) or
            ((c and 0xFF) shl 8) or
            (d and 0xFF)
    }

    private const val BOX_HEADER_SIZE = 8
    private const val LARGE_BOX_HEADER_SIZE = 16
    private const val FULL_BOX_HEADER_SIZE = 4
    private const val DATA_TYPE_UTF8 = 1
    private const val UINT_MAX = 0xFFFF_FFFFL
    private const val COPY_BUFFER_SIZE = 256 * 1024
    private const val MAX_MOOV_BYTES = 16L * 1024L * 1024L

    private val ATOM_ALBUM = fourCc(0xA9, 'a'.code, 'l'.code, 'b'.code)
    private val ATOM_ARTIST = fourCc(0xA9, 'A'.code, 'R'.code, 'T'.code)
    private val ATOM_CO64 = fourCc('c'.code, 'o'.code, '6'.code, '4'.code)
    private val ATOM_COVR = fourCc('c'.code, 'o'.code, 'v'.code, 'r'.code)
    private val ATOM_DATA = fourCc('d'.code, 'a'.code, 't'.code, 'a'.code)
    private val ATOM_GENRE = fourCc(0xA9, 'g'.code, 'e'.code, 'n'.code)
    private val ATOM_HDLR = fourCc('h'.code, 'd'.code, 'l'.code, 'r'.code)
    private val ATOM_ILST = fourCc('i'.code, 'l'.code, 's'.code, 't'.code)
    private val ATOM_MDAT = fourCc('m'.code, 'd'.code, 'a'.code, 't'.code)
    private val ATOM_MDIR = fourCc('m'.code, 'd'.code, 'i'.code, 'r'.code)
    private val ATOM_META = fourCc('m'.code, 'e'.code, 't'.code, 'a'.code)
    private val ATOM_MOOV = fourCc('m'.code, 'o'.code, 'o'.code, 'v'.code)
    private val ATOM_MVHD = fourCc('m'.code, 'v'.code, 'h'.code, 'd'.code)
    private val ATOM_STCO = fourCc('s'.code, 't'.code, 'c'.code, 'o'.code)
    private val ATOM_TITLE = fourCc(0xA9, 'n'.code, 'a'.code, 'm'.code)
    private val ATOM_UDTA = fourCc('u'.code, 'd'.code, 't'.code, 'a'.code)
    private val ATOM_YEAR = fourCc(0xA9, 'd'.code, 'a'.code, 'y'.code)
    private val ATOM_ALBUM_ARTIST = fourCc('a'.code, 'A'.code, 'R'.code, 'T'.code)
    private val ATOM_TRACK = fourCc('t'.code, 'r'.code, 'k'.code, 'n'.code)

    private val CONTAINER_ATOMS = setOf(
        fourCc('d'.code, 'i'.code, 'n'.code, 'f'.code),
        fourCc('e'.code, 'd'.code, 't'.code, 's'.code),
        fourCc('m'.code, 'd'.code, 'i'.code, 'a'.code),
        fourCc('m'.code, 'i'.code, 'n'.code, 'f'.code),
        fourCc('m'.code, 'o'.code, 'o'.code, 'v'.code),
        fourCc('s'.code, 't'.code, 'b'.code, 'l'.code),
        fourCc('t'.code, 'r'.code, 'a'.code, 'k'.code),
        ATOM_UDTA
    )
}
