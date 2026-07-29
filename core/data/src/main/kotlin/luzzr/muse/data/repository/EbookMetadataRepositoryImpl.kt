package luzzr.muse.data.repository

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.ebook.EpubMetadataParser
import luzzr.muse.domain.ebook.EbookMetadataParser
import luzzr.muse.domain.model.EbookMetadata
import luzzr.muse.domain.repository.EbookMetadataRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class EbookMetadataRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    epubMetadataParser: EpubMetadataParser
) : EbookMetadataRepository {

    private val parsers: List<EbookMetadataParser> = listOf(epubMetadataParser)

    override suspend fun extract(uri: String, displayName: String?, mimeType: String?): OperationResult<EbookMetadata> =
        withContext(Dispatchers.IO) {
            val temporaryFile = File.createTempFile("muse_ebook_", ".tmp", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri.toUri())
                    ?: return@withContext OperationResult.Failure(OperationError.NOT_FOUND, "Unable to open ebook")
                input.use { source ->
                    temporaryFile.outputStream().use { destination ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_ARCHIVE_BYTES) {
                                return@withContext OperationResult.Failure(OperationError.UNSUPPORTED_FILE, "Ebook exceeds size limit")
                            }
                            destination.write(buffer, 0, count)
                        }
                    }
                }
                val parser = parsers.firstOrNull { it.supports(displayName, mimeType) }
                    ?: parsers.firstOrNull { it.recognizes(temporaryFile) }
                    ?: return@withContext OperationResult.Failure(OperationError.UNSUPPORTED_FILE, "Unsupported ebook format")
                parser.parse(temporaryFile)
            } catch (e: SecurityException) {
                OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.IO, e.message)
            } finally {
                temporaryFile.delete()
            }
        }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024
    }
}
