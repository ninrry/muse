package luzzr.muse.data.ebook

import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.ebook.EbookMetadataParser
import luzzr.muse.domain.model.EbookMetadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class EpubMetadataParser @Inject constructor() : EbookMetadataParser {

    override fun supports(displayName: String?, mimeType: String?): Boolean =
        mimeType.equals(EPUB_MIME_TYPE, ignoreCase = true) || displayName?.endsWith(".epub", ignoreCase = true) == true

    override fun recognizes(file: File): Boolean = try {
        ZipFile(file).use { zip ->
            val mimeType = readArchiveMimeType(zip)
            mimeType.equals(EPUB_MIME_TYPE, ignoreCase = true) || zip.getEntry(CONTAINER_PATH) != null
        }
    } catch (_: IOException) {
        false
    }

    private fun readArchiveMimeType(zip: ZipFile): String? {
        val entry = zip.getEntry(MIMETYPE_PATH) ?: return null
        if (entry.size !in 1..MAX_MIMETYPE_BYTES) return null
        return zip.getInputStream(entry).bufferedReader(StandardCharsets.US_ASCII).use { it.readText().trim() }
    }

    override suspend fun parse(file: File): OperationResult<EbookMetadata> = withContext(Dispatchers.IO) {
        try {
            ZipFile(file).use { zip ->
                validateEntries(zip)
                val container = parseXml(readEntry(zip, CONTAINER_PATH, MAX_XML_BYTES))
                val packagePath = container.getElementsByTagNameNS("*", "rootfile")
                    .item(0)
                    ?.let { it as? Element }
                    ?.getAttribute("full-path")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::normalizeArchivePath)
                    ?: return@withContext OperationResult.Failure(
                        OperationError.UNSUPPORTED_FILE,
                        "EPUB container does not reference a package document"
                    )

                val packageDocument = parseXml(readEntry(zip, packagePath, MAX_XML_BYTES))
                val title = packageDocument.elements("title").firstOrNull()?.textContent?.trim().orEmpty()
                val author = packageDocument.elements("creator")
                    .map { it.textContent.trim() }
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" / ")

                val manifest = packageDocument.elements("item").mapNotNull { element ->
                    val id = element.getAttribute("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val href = element.getAttribute("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val path = resolveArchivePath(packagePath, href)
                    ManifestItem(
                        id = id,
                        path = path,
                        mediaType = element.getAttribute("media-type").ifBlank { guessImageMediaType(path).orEmpty() },
                        properties = element.getAttribute("properties").split(Regex("\\s+")).filter(String::isNotBlank).toSet()
                    )
                }
                val coverItem = findCoverItem(zip, packageDocument, packagePath, manifest)
                val coverBytes = coverItem?.let { readEntry(zip, it.path, MAX_COVER_BYTES) }

                OperationResult.Success(
                    EbookMetadata(
                        title = title,
                        author = author,
                        coverBytes = coverBytes,
                        coverMediaType = coverItem?.mediaType?.takeIf(String::isNotBlank)
                    )
                )
            }
        } catch (e: SecurityException) {
            OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
        } catch (e: ZipException) {
            OperationResult.Failure(OperationError.UNSUPPORTED_FILE, "Invalid EPUB archive: ${e.message}")
        } catch (e: IllegalArgumentException) {
            OperationResult.Failure(OperationError.UNSUPPORTED_FILE, e.message)
        } catch (e: IOException) {
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UNSUPPORTED_FILE, e.message)
        }
    }

    private fun validateEntries(zip: ZipFile) {
        val entries = zip.entries().toList()
        require(entries.size <= MAX_ENTRY_COUNT) { "EPUB contains too many entries" }
        entries.forEach { entry ->
            require(entry.name.replace('\\', '/').split('/').none { it == ".." }) { "Unsafe EPUB path: ${entry.name}" }
            normalizeArchivePath(entry.name)
            require(entry.size <= MAX_UNCOMPRESSED_ENTRY_BYTES) { "EPUB entry is too large: ${entry.name}" }
        }
    }

    private fun findCoverItem(zip: ZipFile, document: Document, packagePath: String, manifest: List<ManifestItem>): ManifestItem? {
        val epub3Cover = manifest.firstOrNull { "cover-image" in it.properties && it.isImage }

        val coverId = document.elements("meta")
            .firstOrNull { it.getAttribute("name").equals("cover", ignoreCase = true) }
            ?.getAttribute("content")
            ?.takeIf(String::isNotBlank)
        val epub2Cover = manifest.firstOrNull { it.id == coverId && it.isImage }

        val guideHref = document.elements("reference")
            .firstOrNull { it.getAttribute("type").split(Regex("\\s+")).any { type -> type.equals("cover", true) } }
            ?.getAttribute("href")
            ?.takeIf(String::isNotBlank)
        val guideCover = guideHref?.let {
            val guidePath = resolveArchivePath(packagePath, guideHref)
            manifest.firstOrNull { item -> item.path == guidePath && item.isImage }
                ?: findCoverPageImage(zip, guidePath, manifest)
        }

        val namedCover = manifest.firstOrNull {
            it.isImage && (it.id.contains("cover", true) || it.path.substringAfterLast('/').contains("cover", true))
        }
        return epub3Cover ?: epub2Cover ?: guideCover ?: namedCover
    }

    private fun findCoverPageImage(zip: ZipFile, coverPagePath: String, manifest: List<ManifestItem>): ManifestItem? {
        val entry = zip.getEntry(coverPagePath) ?: return null
        if (entry.size > MAX_XML_BYTES) return null
        val document = runCatching { parseXml(readEntry(zip, coverPagePath, MAX_XML_BYTES)) }.getOrNull() ?: return null
        val source = document.elements("img").firstOrNull()?.getAttribute("src")
            ?: document.elements("image").firstOrNull()?.let {
                it.getAttribute("href").ifBlank { it.getAttributeNS("http://www.w3.org/1999/xlink", "href") }
            }
        val imagePath = source?.takeIf(String::isNotBlank)?.let { resolveArchivePath(coverPagePath, it) } ?: return null
        return manifest.firstOrNull { it.path == imagePath && it.isImage }
            ?: ManifestItem("guide-cover", imagePath, guessImageMediaType(imagePath).orEmpty(), emptySet())
                .takeIf { zip.getEntry(imagePath) != null }
    }

    private fun readEntry(zip: ZipFile, path: String, maxBytes: Long): ByteArray {
        val normalized = normalizeArchivePath(path)
        val entry = zip.getEntry(normalized) ?: throw IllegalArgumentException("Missing EPUB entry: $normalized")
        require(!entry.isDirectory) { "Expected a file entry: $normalized" }
        require(entry.size <= maxBytes) { "EPUB entry exceeds size limit: $normalized" }
        zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "EPUB entry exceeds size limit: $normalized" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.setExpandEntityReferences(false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }

    private fun Document.elements(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun resolveArchivePath(baseFile: String, href: String): String {
        val decoded = URLDecoder.decode(
            href.substringBefore('#').substringBefore('?').replace("+", "%2B"),
            StandardCharsets.UTF_8.name()
        )
        val baseDirectory = baseFile.substringBeforeLast('/', "")
        return normalizeArchivePath(listOf(baseDirectory, decoded).filter(String::isNotBlank).joinToString("/"))
    }

    private fun normalizeArchivePath(rawPath: String): String {
        val clean = rawPath.replace('\\', '/').trim()
        require(clean.isNotBlank() && !clean.startsWith('/')) { "Unsafe EPUB path: $rawPath" }
        val parts = mutableListOf<String>()
        clean.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> require(parts.isNotEmpty()) { "Unsafe EPUB path: $rawPath" }.also { parts.removeAt(parts.lastIndex) }
                else -> parts += part
            }
        }
        require(parts.isNotEmpty()) { "Unsafe EPUB path: $rawPath" }
        return parts.joinToString("/")
    }

    private fun guessImageMediaType(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> null
    }

    private data class ManifestItem(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: Set<String>
    ) {
        val isImage: Boolean get() = mediaType.startsWith("image/")
    }

    private companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"
        const val MIMETYPE_PATH = "mimetype"
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val MAX_MIMETYPE_BYTES = 128L
        const val MAX_ENTRY_COUNT = 10_000
        const val MAX_XML_BYTES = 2L * 1024 * 1024
        const val MAX_COVER_BYTES = 15L * 1024 * 1024
        const val MAX_UNCOMPRESSED_ENTRY_BYTES = 100L * 1024 * 1024
    }
}
