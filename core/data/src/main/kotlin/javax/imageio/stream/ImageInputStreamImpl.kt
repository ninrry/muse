package javax.imageio.stream

class ImageInputStreamImpl(private val bytes: ByteArray) : ImageInputStream {
    override fun getRawBytes(): ByteArray = bytes
}
