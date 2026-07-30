package luzzr.muse.data.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.lyrics.LyricsFileReadResult
import luzzr.muse.domain.lyrics.LyricsFileReader

@Singleton
class ContentResolverLyricsFileReader @Inject constructor(
    @ApplicationContext private val context: Context
) : LyricsFileReader {

    override suspend fun read(uri: String): LyricsFileReadResult = withContext(Dispatchers.IO) {
        val parsedUri = runCatching { Uri.parse(uri) }.getOrNull()
            ?: return@withContext LyricsFileReadResult.Unreadable
        if (parsedUri.scheme != ContentResolver.SCHEME_CONTENT) {
            return@withContext LyricsFileReadResult.Unreadable
        }

        try {
            val input = context.contentResolver.openInputStream(parsedUri)
                ?: return@withContext LyricsFileReadResult.Unreadable
            input.use { stream ->
                val bytes = stream.readLrcBytesWithinLimit()
                    ?: return@withContext LyricsFileReadResult.TooLarge
                LyricsFileReadResult.Success(decodeLrcBytes(bytes))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MuseLog.w("LyricsFileReader", "Unable to read selected lyrics document", e)
            LyricsFileReadResult.Unreadable
        }
    }
}
