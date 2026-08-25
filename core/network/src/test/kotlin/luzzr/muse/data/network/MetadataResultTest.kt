package luzzr.muse.data.network

import luzzr.muse.domain.model.MetadataResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataResultTest {

    @Test
    fun `woman character nv is not converted to you ni`() {
        val normalizer = AndroidTextNormalizer()
        assertEquals("女人", normalizer.toSimplified("女人"))
        assertEquals("女朋友", normalizer.toSimplified("女朋友"))
        assertEquals("女歌手", normalizer.toSimplified("女歌手"))
        assertEquals("少女时代", normalizer.toSimplified("少女时代"))
    }

    @Test
    fun `prison character lao is not converted to it ta`() {
        val normalizer = AndroidTextNormalizer()
        assertEquals("牢房", normalizer.toSimplified("牢房"))
        assertEquals("亡羊补牢", normalizer.toSimplified("亡羊补牢"))
    }

    @Test
    fun `traditional female you is converted to ni`() {
        val normalizer = AndroidTextNormalizer()
        assertEquals("你好", normalizer.toSimplified("妳好"))
    }

    @Test
    fun `toSimplifiedChinese preserves woman character in MetadataResult`() {
        val result = MetadataResult(
            title = "女孩",
            artist = "韦礼安",
            album = "有人在等妳",
            genre = "流行"
        ).toSimplifiedChinese()

        assertEquals("女孩", result.title)
        assertEquals("韦礼安", result.artist)
        assertEquals("有人在等你", result.album)
    }
}
