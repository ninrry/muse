package luzzr.muse.ui.screens.audiobook

import android.os.Handler
import android.webkit.JavascriptInterface

/**
 * Narrow bridge between the immutable EPUB document and Compose reader state.
 * All callbacks are posted to the main thread so WebView never mutates UI state directly.
 */
internal class ReadAlongWebBridge(
    private val mainHandler: Handler,
    private val onSeekToSentence: (String, String?, Long) -> Unit,
    private val onLongPressSelection: (Int, Int) -> Unit,
    private val onScrollProgress: (Float) -> Unit,
    private val onPageProgress: (Float) -> Unit,
    private val onReaderTap: () -> Unit,
    private val onFollowSuspended: () -> Unit
) {
    @JavascriptInterface
    fun seekToSentence(href: String, elementId: String, positionMs: Double) {
        mainHandler.post {
            onSeekToSentence(href, elementId.takeIf { it.isNotBlank() }, positionMs.toLong())
        }
    }

    @JavascriptInterface
    fun onSelection(charStart: Int, charEnd: Int) {
        mainHandler.post { onLongPressSelection(charStart, charEnd) }
    }

    @JavascriptInterface
    fun onScrollProgress(value: Double) {
        mainHandler.post { onScrollProgress(value.toFloat().coerceIn(0f, 1f)) }
    }

    @JavascriptInterface
    fun onPageProgress(value: Double) {
        mainHandler.post { onPageProgress(value.toFloat().coerceIn(0f, 1f)) }
    }

    @JavascriptInterface
    fun onReaderTap() {
        mainHandler.post(onReaderTap)
    }

    @JavascriptInterface
    fun onFollowSuspended() {
        mainHandler.post(onFollowSuspended)
    }
}
