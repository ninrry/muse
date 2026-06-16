package java.awt.image

class BufferedImage(private val width: Int, private val height: Int) : java.awt.Image() {
    fun getWidth(): Int = width
    fun getHeight(): Int = height
    fun getWidth(observer: Any?): Int = width
    fun getHeight(observer: Any?): Int = height
}
