package luzzr.muse.data.network

import luzzr.muse.core.log.MuseLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpResponse(
    val code: Int,
    val body: String,
    val isSuccessful: Boolean
)

fun OkHttpClient.Builder.defaultMuseConfig(): OkHttpClient.Builder {
    return this
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent").isNullOrBlank()) {
                chain.proceed(request.newBuilder().header("User-Agent", MOBILE_USER_AGENT).build())
            } else {
                chain.proceed(request)
            }
        }
}

suspend fun OkHttpClient.safeGet(
    tag: String,
    url: String,
    headers: Map<String, String> = emptyMap()
): HttpResponse? {
    return safeCall(tag, "GET $url") {
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            newCall(builder.build()).execute().use { response ->
                HttpResponse(
                    code = response.code,
                    body = response.body?.string().orEmpty(),
                    isSuccessful = response.isSuccessful
                )
            }
        }
    }
}

suspend fun OkHttpClient.safePost(
    tag: String,
    url: String,
    body: String,
    contentType: String = "application/x-www-form-urlencoded",
    headers: Map<String, String> = emptyMap()
): HttpResponse? {
    return safeCall(tag, "POST $url") {
        withContext(Dispatchers.IO) {
            val mediaType = contentType.toMediaType()
            val builder = Request.Builder()
                .url(url)
                .post(body.toRequestBody(mediaType))
            headers.forEach { (k, v) -> builder.header(k, v) }
            newCall(builder.build()).execute().use { response ->
                HttpResponse(
                    code = response.code,
                    body = response.body?.string().orEmpty(),
                    isSuccessful = response.isSuccessful
                )
            }
        }
    }
}

suspend fun OkHttpClient.safeGetWithReferer(
    tag: String,
    url: String,
    referer: String,
    userAgent: String = MOBILE_USER_AGENT
): HttpResponse? {
    return safeCall(tag, "GET $url") {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
                .build()
            newCall(request).execute().use { response ->
                HttpResponse(
                    code = response.code,
                    body = response.body?.string().orEmpty(),
                    isSuccessful = response.isSuccessful
                )
            }
        }
    }
}

suspend fun <T> safeCall(
    tag: String,
    operation: String,
    block: suspend () -> T
): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SocketTimeoutException) {
        MuseLog.e(tag, "$operation: timeout", e)
        null
    } catch (e: UnknownHostException) {
        MuseLog.e(tag, "$operation: host unreachable", e)
        null
    } catch (e: IOException) {
        MuseLog.e(tag, "$operation: IO error", e)
        null
    } catch (e: Exception) {
        MuseLog.e(tag, "$operation: unexpected error", e)
        null
    }
}

internal const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

internal const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
