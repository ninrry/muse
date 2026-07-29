package javax.imageio

import java.io.InputStream
import java.io.File
import javax.imageio.stream.ImageInputStream
import javax.imageio.stream.ImageInputStreamImpl

class ImageIO private constructor() {
    companion object {
        @JvmStatic
        fun read(input: InputStream): java.awt.image.BufferedImage {
            val bytes = try {
                input.readBytes()
            } catch (_: Exception) {
                byteArrayOf()
            }
            val dims = detectImageDimensions(bytes)
            return java.awt.image.BufferedImage(dims.first, dims.second)
        }

        @JvmStatic
        fun read(input: ImageInputStream): java.awt.image.BufferedImage {
            val bytes = try {
                input.getRawBytes()
            } catch (_: Exception) {
                byteArrayOf()
            }
            val dims = detectImageDimensions(bytes)
            return java.awt.image.BufferedImage(dims.first, dims.second)
        }

        @JvmStatic
        fun createImageInputStream(input: Any?): ImageInputStream? {
            if (input == null) return null
            val bytes = when (input) {
                is InputStream -> {
                    try { input.readBytes() } catch (_: Exception) { byteArrayOf() }
                }
                is File -> {
                    try { input.readBytes() } catch (_: Exception) { byteArrayOf() }
                }
                is ByteArray -> input
                else -> byteArrayOf()
            }
            return ImageInputStreamImpl(bytes)
        }

        private fun detectImageDimensions(bytes: ByteArray): Pair<Int, Int> {
            // 1. Try to use Android's BitmapFactory options (Only works at Android runtime)
            try {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    return Pair(options.outWidth, options.outHeight)
                }
            } catch (_: Throwable) {
                // Catches stub exception or NoClassDefFoundError in pure JVM unit tests
            }

            // 2. Fallback to manually parsing PNG and JPEG headers on JVM
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
            } catch (_: Exception) {
            }
            return Pair(0, 0)
        }
    }
}
