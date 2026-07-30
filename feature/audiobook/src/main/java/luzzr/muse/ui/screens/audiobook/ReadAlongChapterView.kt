package luzzr.muse.ui.screens.audiobook

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import luzzr.muse.domain.model.ReadAlongAnnotation
import luzzr.muse.domain.model.ReadAlongChapterData
import luzzr.muse.domain.model.ReadAlongFontFamily
import luzzr.muse.domain.model.ReadAlongFontWeight
import luzzr.muse.domain.model.ReadAlongPagerMode
import luzzr.muse.domain.model.ReadAlongTextIndex
import luzzr.muse.domain.model.ReadAlongTheme
import luzzr.muse.ui.components.LocalReduceMotion
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Renders the EPUB chapter and reflects the active highlight inside a WebView.
 *
 * The WebView is rebuilt only when the chapter HTML path actually changes; the
 * setup/highlight scripts are debounced so playback tick updates only visual
 * ranges, never the EPUB DOM tree. When [textIndex] is provided we ship a
 * single pre-computed script that the WebView uses to map unit indices to
 * character ranges without re-walking the DOM on every tick.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReadAlongChapterView(
    chapterData: ReadAlongChapterData,
    textIndex: ReadAlongTextIndex?,
    activeUnitIndex: Int,
    activeSentenceIndex: Int,
    targetTextRange: IntRange?,
    settings: ReadAlongSettingsState,
    annotations: List<ReadAlongAnnotation>,
    scrollProgress: Float,
    pageProgress: Float,
    onScrollProgress: (Float) -> Unit,
    onPageProgress: (Float) -> Unit,
    onTextRangeConsumed: () -> Unit,
    onReaderTap: () -> Unit,
    onSeekToSentence: (chapterHref: String, elementId: String?, positionMs: Long) -> Unit,
    onLongPressSelection: (charStart: Int, charEnd: Int) -> Unit,
    jumpMode: Boolean,
    chromeVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val readerSettings = settings
    val reduceMotion = LocalReduceMotion.current
    var pageReady by remember(chapterData.chapter.htmlPath) { mutableStateOf(false) }
    val webView = remember(chapterData.chapter.htmlPath) {
        WebView(context).apply {
            val webSettings: WebSettings = getSettings()
            webSettings.javaScriptEnabled = true
            webSettings.domStorageEnabled = true
            webSettings.blockNetworkLoads = true
            webSettings.javaScriptCanOpenWindowsAutomatically = false
            webSettings.setSupportMultipleWindows(false)
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webSettings.safeBrowsingEnabled = true
            run {
                @Suppress("DEPRECATION")
                webSettings.allowFileAccess = true
                @Suppress("DEPRECATION")
                webSettings.allowContentAccess = false
                @Suppress("DEPRECATION")
                webSettings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                webSettings.allowUniversalAccessFromFileURLs = false
            }
            setBackgroundColor(0x00000000)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                override fun onPageFinished(view: WebView, url: String?) {
                    pageReady = true
                }
            }
            addJavascriptInterface(
                object {
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
                        mainHandler.post { onReaderTap() }
                    }
                },
                "MuseReader"
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface("MuseReader")
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(chapterData.chapter.htmlPath) {
        pageReady = false
        webView.loadUrl(Uri.fromFile(File(chapterData.chapter.htmlPath)).toString())
    }

    LaunchedEffect(pageReady, readerSettings, annotations, jumpMode, chromeVisible) {
        if (pageReady) {
            webView.evaluateJavascript(buildSetupScript(readerSettings, annotations, jumpMode, reduceMotion), null)
        }
    }

    LaunchedEffect(pageReady, textIndex, chapterData.chapter.href) {
        if (pageReady && textIndex != null) {
            webView.evaluateJavascript(buildHighlightIndexScript(textIndex), null)
        }
    }

    LaunchedEffect(pageReady, targetTextRange, chapterData.chapter.href) {
        val target = targetTextRange ?: return@LaunchedEffect
        if (pageReady) {
            webView.evaluateJavascript(buildScrollToTextRangeScript(target.first, target.last + 1, reduceMotion), null)
            onTextRangeConsumed()
        }
    }

    LaunchedEffect(pageReady, readerSettings, chapterData.chapter.href, chromeVisible) {
        if (pageReady) {
            val paged = readerSettings.pagerMode == ReadAlongPagerMode.PAGED
            val progress = if (paged) pageProgress else scrollProgress
            webView.evaluateJavascript(buildRestoreScrollScript(progress, paged), null)
        }
    }

    LaunchedEffect(pageReady, activeUnitIndex, activeSentenceIndex, settings.autoFollow, textIndex) {
        if (!pageReady) return@LaunchedEffect
        val script = textIndex?.let {
            buildPrecomputedHighlightScript(it, activeUnitIndex, activeSentenceIndex, settings.autoFollow)
        } ?: "window.__museClearHighlights && window.__museClearHighlights();"
        webView.evaluateJavascript(script, null)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webView }
    )
}

internal fun buildScrollToTextRangeScript(start: Int, end: Int, reduceMotion: Boolean = false): String {
    val safeStart = start.coerceAtLeast(0)
    val safeEnd = end.coerceAtLeast(safeStart + 1)
    val scrollBehavior = if (reduceMotion) "auto" else "smooth"
    return """
        (() => {
          const root = document.body;
          if (!root) return;
          const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
          const nodes = [];
          let joined = '';
          let node;
          while ((node = walker.nextNode())) {
            const parent = node.parentElement;
            if (parent && (parent.closest('script,style') || parent.closest('[aria-hidden="true"]'))) continue;
            nodes.push({ node, start: joined.length });
            joined += node.nodeValue || '';
          }
          const from = Math.min($safeStart, joined.length);
          const to = Math.min(Math.max($safeEnd, from + 1), joined.length);
          if (to <= from) return;
          const point = (offset) => {
            for (let i = 0; i < nodes.length; i += 1) {
              const item = nodes[i];
              const length = (item.node.nodeValue || '').length;
              if (offset <= item.start + length) return [item.node, Math.max(0, offset - item.start)];
            }
            const last = nodes[nodes.length - 1];
            return [last.node, (last.node.nodeValue || '').length];
          };
          const startPoint = point(from);
          const endPoint = point(to);
          const range = document.createRange();
          range.setStart(startPoint[0], startPoint[1]);
          range.setEnd(endPoint[0], endPoint[1]);
          const parent = range.startContainer.parentElement;
          const target = parent && parent.closest
            ? (parent.closest('span[data-muse-unit], mark.muse-sentence') || parent)
            : parent;
          if (target && target.scrollIntoView) {
            target.scrollIntoView({ behavior: '$scrollBehavior', block: 'center', inline: 'nearest' });
          }
        })();
    """.trimIndent()
}

internal fun buildRestoreScrollScript(progress: Float, paged: Boolean): String {
    val safe = progress.coerceIn(0f, 1f)
    return """
        (() => {
          const apply = () => {
            const isPaged = document.body.classList.contains('muse-paged');
            const scroller = isPaged ? (document.getElementById('muse-viewport') || document.documentElement) : (document.scrollingElement || document.documentElement);
            const viewport = isPaged
              ? Math.max(1, scroller.clientWidth || window.innerWidth)
              : Math.max(1, scroller.clientHeight || window.innerHeight);
            const max = isPaged
              ? Math.max(0, scroller.scrollWidth - viewport)
              : Math.max(0, scroller.scrollHeight - viewport);
            if (isPaged) {
              const raw = max * $safe;
              const page = Math.round(raw / viewport);
              scroller.scrollLeft = Math.min(max, Math.max(0, page * viewport));
            } else {
              scroller.scrollTop = max * $safe;
            }
          };
          requestAnimationFrame(() => requestAnimationFrame(() => {
            if (document.fonts && document.fonts.ready) document.fonts.ready.then(apply, apply);
            else apply();
          }));
        })();
    """.trimIndent()
}

internal fun buildSetupScript(
    settings: ReadAlongSettingsState,
    annotations: List<ReadAlongAnnotation>,
    jumpMode: Boolean,
    reduceMotion: Boolean = false
): String {
    val palette = when (settings.theme) {
        ReadAlongTheme.PAPER -> "#F4F1EA|#3E3A35|#D9D0BF|#A6805A"
        ReadAlongTheme.SEPIA -> "#F1E6D0|#4B4033|#D9C5A4|#9B6E42"
        ReadAlongTheme.NIGHT -> "#252321|#E9E0D3|#4E4841|#D8AF78"
    }.split('|')
    val fontFamily = when (settings.fontFamily) {
        ReadAlongFontFamily.BOOK -> null
        ReadAlongFontFamily.SERIF -> "Georgia, 'Noto Serif CJK SC', 'Source Han Serif SC', serif"
        ReadAlongFontFamily.SANS -> "'Noto Sans CJK SC', 'Source Han Sans SC', system-ui, sans-serif"
        ReadAlongFontFamily.MONO -> "'JetBrains Mono', 'Source Code Pro', monospace"
        ReadAlongFontFamily.SYSTEM -> "system-ui, -apple-system, sans-serif"
    }
    val fontFamilyRule = fontFamily?.let { "font-family: $it;" }.orEmpty()
    val fontWeight = when (settings.fontWeight) {
        ReadAlongFontWeight.REGULAR -> 400
        ReadAlongFontWeight.MEDIUM -> 500
        ReadAlongFontWeight.SEMIBOLD -> 600
    }
    val annotationJson = annotationsToJson(annotations)
    val scrollBehavior = if (reduceMotion) "auto" else "smooth"
    return """
        (() => {
          const root = document.documentElement;
          const old = document.getElementById('muse-readalong-style');
          if (old) old.remove();
          const style = document.createElement('style');
          style.id = 'muse-readalong-style';
          style.textContent = `
            :root { color-scheme: ${if (settings.theme == ReadAlongTheme.NIGHT) "dark" else "light"}; }
            html, body { margin: 0; padding: 0; min-height: 100%; background: ${palette[0]}; color: ${palette[1]};
              overflow-x: hidden;
              overflow-y: ${if (settings.pagerMode == ReadAlongPagerMode.PAGED) "hidden" else "auto"}; }
            html.muse-paged-root { overflow: hidden; }
            body { $fontFamilyRule
              font-size: ${settings.fontScale}em; line-height: ${settings.lineHeightScale};
              font-weight: $fontWeight; letter-spacing: 0;
              padding: 28px 24px 96px; max-width: 760px; margin: 0 auto;
              word-break: break-word; text-rendering: optimizeLegibility; }
            body.muse-paged { height: 100vh; min-height: 100vh; width: 100%; min-width: 100%; max-width: none; box-sizing: border-box;
              margin: 0; padding: 0; overflow: visible; white-space: normal; }
            body.muse-paged #muse-viewport { width: 100%; min-width: 100%; height: 100vh; min-height: 100vh; box-sizing: border-box;
              overflow-x: auto; overflow-y: hidden; }
            body.muse-paged #muse-pager { width: 100%; min-width: 100%; height: 100vh; min-height: 100vh; box-sizing: border-box;
              padding: 28px 24px 96px; column-width: calc(100% - 48px); column-gap: 48px;
              column-fill: auto; overflow: visible; }
            p { margin: 0 0 ${settings.paragraphSpacing}em; text-align: justify; }
            h1, h2, h3, h4 { color: ${palette[1]}; line-height: 1.3; margin: 1.4em 0 .7em; }
            img { max-width: 100%; height: auto; border-radius: 14px; }
            a { color: ${palette[3]}; }
            ::highlight(muse-sentence-active) { background-color: ${palette[2]}; color: inherit; }
            ::highlight(muse-unit-active) { background-color: ${palette[3]}; color: ${palette[0]}; }
            #muse-highlight-overlay { position: fixed; inset: 0; overflow: hidden; pointer-events: none; z-index: 2147483647; }
            #muse-highlight-overlay .muse-sentence-rect { position: absolute; background: ${palette[2]}; border-radius: 4px; }
            #muse-highlight-overlay .muse-unit-rect { position: absolute; background: ${palette[3]}; border-radius: 4px; }
            [data-muse] { cursor: pointer; }
            mark.muse-anno-yellow { background: rgba(255, 213, 79, 0.4); border-radius: 3px; padding: 0 2px; }
            mark.muse-anno-green { background: rgba(129, 199, 132, 0.4); border-radius: 3px; padding: 0 2px; }
            mark.muse-anno-pink { background: rgba(244, 143, 177, 0.4); border-radius: 3px; padding: 0 2px; }
            mark.muse-anno-blue { background: rgba(100, 181, 246, 0.4); border-radius: 3px; padding: 0 2px; }
            mark.muse-anno-underline { background: transparent; border-bottom: 2px solid ${palette[3]}; border-radius: 0; padding: 0; }
            ::selection { background: ${palette[2]}; }
          `;
          document.head.appendChild(style);
          const paged = ${settings.pagerMode == ReadAlongPagerMode.PAGED};
          let viewport = document.getElementById('muse-viewport');
          let pager = document.getElementById('muse-pager');
          if (paged && !pager) {
            viewport = document.createElement('div');
            viewport.id = 'muse-viewport';
            pager = document.createElement('div');
            pager.id = 'muse-pager';
            while (document.body.firstChild) pager.appendChild(document.body.firstChild);
            viewport.appendChild(pager);
            document.body.appendChild(viewport);
          } else if (paged && pager && !viewport) {
            viewport = document.createElement('div');
            viewport.id = 'muse-viewport';
            pager.replaceWith(viewport);
            viewport.appendChild(pager);
          } else if (!paged && pager) {
            while (pager.firstChild) document.body.appendChild(pager.firstChild);
            pager.remove();
            viewport?.remove();
            pager = null;
            viewport = null;
          }
          document.body.classList.toggle('muse-paged', paged);
          document.documentElement.classList.toggle('muse-paged-root', paged);
          if (paged && pager && viewport) {
            const pageWidth = Math.max(1, viewport.clientWidth || window.innerWidth);
            const pageHeight = Math.max(1, viewport.clientHeight || window.innerHeight);
            viewport.style.display = 'block';
            viewport.style.width = `${'$'}{pageWidth}px`;
            viewport.style.minWidth = `${'$'}{pageWidth}px`;
            viewport.style.height = `${'$'}{pageHeight}px`;
            viewport.style.minHeight = `${'$'}{pageHeight}px`;
            viewport.style.overflowX = 'auto';
            viewport.style.overflowY = 'hidden';
            pager.style.display = 'block';
            pager.style.width = `${'$'}{pageWidth}px`;
            pager.style.minWidth = `${'$'}{pageWidth}px`;
            pager.style.height = `${'$'}{pageHeight}px`;
            pager.style.minHeight = `${'$'}{pageHeight}px`;
            pager.style.columnWidth = `${'$'}{Math.max(1, pageWidth - 48)}px`;
            pager.style.columnGap = '48px';
            pager.style.columnFill = 'auto';
          }
          window.__museJumpMode = ${jumpMode};
          window.__musePaged = paged;
          if (!window.__museReadAlongClickInstalled) {
            const pagedWidth = () => Math.max(
              1,
              document.getElementById('muse-viewport')?.clientWidth || window.innerWidth
            );
            document.addEventListener('click', (event) => {
              if (window.__museSuppressNextTap && Date.now() < window.__museSuppressNextTap) return;
              const target = event.target && event.target.closest
                ? event.target.closest('[data-muse]')
                : null;
              if (window.__museJumpMode) {
                if (!target || !window.MuseReader) return;
                const href = target.dataset.museHref || '';
                const elementId = target.dataset.museElement || '';
                const position = Number(target.dataset.museStart || '0');
                window.MuseReader.seekToSentence(href, elementId, position);
                return;
              }
              if (!window.MuseReader) return;
              const width = pagedWidth();
              if (window.__musePaged && event.clientX < width * 0.28) {
                const scroller = document.getElementById('muse-viewport');
                if (scroller) scroller.scrollBy({ left: -width, behavior: '$scrollBehavior' });
                return;
              }
              if (window.__musePaged && event.clientX > width * 0.72) {
                const scroller = document.getElementById('muse-viewport');
                if (scroller) scroller.scrollBy({ left: width, behavior: '$scrollBehavior' });
                return;
              }
              window.MuseReader.onReaderTap();
            }, {passive: true});
            const selectionOffset = (node, offset) => {
              const range = document.createRange();
              range.selectNodeContents(document.body);
              try { range.setEnd(node, offset); } catch (_) { return -1; }
              return range.toString().length;
            };
            const selectionState = { start: -1, end: -1, at: 0 };
            const notifySelection = (start, end) => {
              if (!window.__museJumpMode || end <= start || !window.MuseReader) return;
              const now = Date.now();
              if (selectionState.start === start && selectionState.end === end && now - selectionState.at < 800) return;
              selectionState.start = start;
              selectionState.end = end;
              selectionState.at = now;
              window.MuseReader.onSelection(start, end);
            };
            document.addEventListener('selectionchange', () => {
              if (!window.__museJumpMode) return;
              const sel = window.getSelection();
              if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return;
              const range = sel.getRangeAt(0);
              const a = selectionOffset(range.startContainer, range.startOffset);
              const b = selectionOffset(range.endContainer, range.endOffset);
              notifySelection(Math.min(a, b), Math.max(a, b));
            });
            let longPressTimer = null;
            let longPressPoint = null;
            let swipeStart = null;
            const clearLongPress = () => {
              if (longPressTimer) clearTimeout(longPressTimer);
              longPressTimer = null;
              longPressPoint = null;
            };
            document.addEventListener('touchstart', (event) => {
              if (event.touches.length !== 1) return;
              const point = event.touches[0];
              const scroller = window.__musePaged ? document.getElementById('muse-viewport') : null;
              swipeStart = { x: point.clientX, y: point.clientY, scrollLeft: scroller ? scroller.scrollLeft : 0 };
              if (!window.__museJumpMode) return;
              longPressPoint = { x: point.clientX, y: point.clientY };
              clearTimeout(longPressTimer);
              longPressTimer = setTimeout(() => {
                if (!longPressPoint) return;
                let caretRange = null;
                if (document.caretRangeFromPoint) {
                  caretRange = document.caretRangeFromPoint(longPressPoint.x, longPressPoint.y);
                } else if (document.caretPositionFromPoint) {
                  const caret = document.caretPositionFromPoint(longPressPoint.x, longPressPoint.y);
                  if (caret) {
                    caretRange = document.createRange();
                    caretRange.setStart(caret.offsetNode, caret.offset);
                    caretRange.collapse(true);
                  }
                }
                if (!caretRange) return;
                const offset = selectionOffset(caretRange.startContainer, caretRange.startOffset);
                if (offset >= 0) notifySelection(offset, offset + 1);
              }, 550);
            }, {passive: true});
            document.addEventListener('touchmove', (event) => {
              if (event.touches.length !== 1) return;
              const point = event.touches[0];
              if (longPressPoint && Math.hypot(point.clientX - longPressPoint.x, point.clientY - longPressPoint.y) > 18) clearLongPress();
              if (!swipeStart || !window.__musePaged) return;
              const dx = point.clientX - swipeStart.x;
              const dy = point.clientY - swipeStart.y;
              if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy)) event.preventDefault();
            }, {passive: false});
            document.addEventListener('touchend', (event) => {
              const start = swipeStart;
              swipeStart = null;
              clearLongPress();
              if (!start || !window.__musePaged || !event.changedTouches || event.changedTouches.length !== 1) return;
              const point = event.changedTouches[0];
              const dx = point.clientX - start.x;
              const dy = point.clientY - start.y;
              if (Math.abs(dx) < 48 || Math.abs(dx) <= Math.abs(dy)) return;
              const scroller = document.getElementById('muse-viewport');
              if (!scroller) return;
              const width = pagedWidth();
              const page = Math.round(start.scrollLeft / width);
              const targetPage = Math.max(0, Math.min(Math.round((scroller.scrollWidth - width) / width), page + (dx < 0 ? 1 : -1)));
              scroller.scrollTo({ left: targetPage * width, behavior: '$scrollBehavior' });
              window.__museSuppressNextTap = Date.now() + 450;
            }, {passive: true});
            document.addEventListener('touchcancel', () => { swipeStart = null; clearLongPress(); }, {passive: true});
            let scrollTimer = null;
            let snapTimer = null;
            const handleReaderScroll = () => {
              if (window.__museRefreshHighlightOverlay) window.__museRefreshHighlightOverlay();
              if (scrollTimer) clearTimeout(scrollTimer);
              if (snapTimer) clearTimeout(snapTimer);
              scrollTimer = setTimeout(() => {
                const paged = document.body.classList.contains('muse-paged');
                const scroller = paged ? (document.getElementById('muse-viewport') || document.documentElement) : (document.scrollingElement || document.documentElement);
                const viewport = paged
                  ? pagedWidth()
                  : Math.max(1, scroller.clientHeight || window.innerHeight);
                const max = paged
                  ? Math.max(0, scroller.scrollWidth - viewport)
                  : Math.max(0, scroller.scrollHeight - viewport);
                const current = paged ? scroller.scrollLeft : scroller.scrollTop;
                const progress = max > 0 ? Math.min(1, Math.max(0, current / max)) : 0;
                if (window.MuseReader) {
                  if (paged) window.MuseReader.onPageProgress(progress);
                  else window.MuseReader.onScrollProgress(progress);
                }
                if (paged && max > 0 && viewport > 0) {
                  const pageSize = viewport;
                  const target = Math.min(max, Math.max(0, Math.round(current / pageSize) * pageSize));
                  if (Math.abs(target - current) > 2) {
                    snapTimer = setTimeout(() => scroller.scrollTo({ left: target, behavior: '$scrollBehavior' }), 80);
                  }
                }
              }, 120);
            };
            window.addEventListener('scroll', handleReaderScroll, {passive: true});
            window.__museReadAlongScrollHandler = handleReaderScroll;
            window.__museReadAlongClickInstalled = true;
          }
          const scrollHandler = window.__museReadAlongScrollHandler;
          const oldScrollTarget = window.__museReadAlongScrollTarget;
          if (oldScrollTarget && oldScrollTarget !== viewport && scrollHandler) {
            oldScrollTarget.removeEventListener('scroll', scrollHandler);
          }
          if (paged && viewport && scrollHandler && oldScrollTarget !== viewport) {
            viewport.addEventListener('scroll', scrollHandler, {passive: true});
            window.__museReadAlongScrollTarget = viewport;
          } else if (!paged) {
            window.__museReadAlongScrollTarget = null;
          }
          const unwrapMuseNode = (node) => {
            const parent = node && node.parentNode;
            if (!parent) return;
            while (node.firstChild) parent.insertBefore(node.firstChild, node);
            parent.removeChild(node);
            parent.normalize();
          };
          const annotationData = $annotationJson;
          if (annotationData.length) {
            document.querySelectorAll('mark[data-muse-anno]').forEach(unwrapMuseNode);
            annotationData.forEach((range) => {
              applyAnnotation(range);
            });
          }
          function applyAnnotation(range) {
            const { start, end, color, href, elementId } = range;
            const target = document.body;
            if (!target) return;
            const walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT);
            const nodes = []; let joined = ''; let node;
            while ((node = walker.nextNode())) { nodes.push({node, start: joined.length}); joined += node.nodeValue || ''; }
            const from = Math.max(0, Math.min(start, joined.length));
            if (from < 0) return;
            let startPoint = null; let endPoint = null;
            for (const item of nodes) {
              const length = (item.node.nodeValue || '').length;
              if (!startPoint && from >= item.start && from <= item.start + length) startPoint = [item.node, from - item.start];
              if (!endPoint && (from + (end - start)) >= item.start && (from + (end - start)) <= item.start + length) {
                endPoint = [item.node, (from + (end - start)) - item.start];
                break;
              }
            }
            if (!startPoint || !endPoint) return;
            const range_ = document.createRange();
            range_.setStart(startPoint[0], startPoint[1]); range_.setEnd(endPoint[0], endPoint[1]);
            const mark = document.createElement('mark');
            mark.className = 'muse-anno-' + color;
            mark.dataset.museAnno = 'true';
            mark.dataset.museHref = href || '';
            mark.dataset.museElement = elementId || '';
            mark.dataset.museCharStart = String(start);
            mark.dataset.museCharEnd = String(end);
            try { range_.surroundContents(mark); } catch (_) { const f = range_.extractContents(); mark.appendChild(f); range_.insertNode(mark); }
          }
          if (window.__museInvalidateHighlightRanges) window.__museInvalidateHighlightRanges();
        })();
    """.trimIndent()
}

internal fun buildHighlightIndexScript(textIndex: ReadAlongTextIndex): String {
    val unitRangesJson = JSONArray().apply {
        textIndex.unitRanges.forEach { range ->
            if (range.isEmpty()) {
                put(JSONArray().put(-1).put(-1))
            } else {
                put(JSONArray().put(range.first).put(range.last + 1))
            }
        }
    }
    val sentenceRangesJson = JSONArray().apply {
        textIndex.sentenceRanges.forEach { range ->
            if (range.isEmpty()) {
                put(JSONArray().put(-1).put(-1))
            } else {
                put(JSONArray().put(range.first).put(range.last + 1))
            }
        }
    }
    return """
        (() => {
          if (!document.body || window.__museHighlightIndex) return;
          const unitRanges = $unitRangesJson;
          const sentenceRanges = $sentenceRangesJson;
          const root = document.body;
          let textNodes = null;
          let activeSentenceRange = null;
          let activeUnitRange = null;
          let overlay = null;
          const state = { unit: -1, sentence: -1, autoFollow: false };
          const supportsCustomHighlights = () => Boolean(
            window.CSS && CSS.highlights && typeof CSS.highlights.set === 'function' && typeof window.Highlight === 'function'
          );
          const collectTextNodes = () => {
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            const nodes = []; let joined = ''; let node;
            while ((node = walker.nextNode())) {
              const parent = node.parentElement;
              if (parent && (parent.closest('script,style') || parent.closest('[data-muse-skip]'))) continue;
              const val = (node.nodeValue || '');
              const collapsed = val.replace(/\s+/g, ' ');
              nodes.push({ node, start: joined.length });
              joined += collapsed;
            }
            return nodes;
          };
          const pointAt = (nodes, offset, preferEnd) => {
            for (const item of nodes) {
              const length = (item.node.nodeValue || '').length;
              const boundary = item.start + length;
              if (offset < boundary || (preferEnd && offset === boundary)) {
                return [item.node, Math.max(0, offset - item.start)];
              }
            }
            const last = nodes[nodes.length - 1];
            return last ? [last.node, (last.node.nodeValue || '').length] : null;
          };
          const rangeFor = (spec) => {
            if (!spec || spec[0] < 0 || spec[1] <= spec[0]) return null;
            textNodes = textNodes || collectTextNodes();
            const start = pointAt(textNodes, spec[0], false);
            const end = pointAt(textNodes, spec[1], true);
            if (!start || !end) return null;
            const range = document.createRange();
            range.setStart(start[0], start[1]);
            range.setEnd(end[0], end[1]);
            return range;
          };
          const clearCssHighlights = () => {
            if (!supportsCustomHighlights()) return;
            CSS.highlights.delete('muse-sentence-active');
            CSS.highlights.delete('muse-unit-active');
          };
          const clearOverlay = () => {
            if (overlay) overlay.replaceChildren();
          };
          const ensureOverlay = () => {
            if (overlay) return overlay;
            overlay = document.getElementById('muse-highlight-overlay');
            if (overlay) return overlay;
            overlay = document.createElement('div');
            overlay.id = 'muse-highlight-overlay';
            overlay.dataset.museSkip = 'true';
            overlay.setAttribute('aria-hidden', 'true');
            document.body.appendChild(overlay);
            return overlay;
          };
          const drawRects = (range, className) => {
            if (!range) return;
            const layer = ensureOverlay();
            Array.from(range.getClientRects()).forEach((rect) => {
              if (rect.width <= 0 || rect.height <= 0) return;
              const piece = document.createElement('i');
              piece.className = className;
              piece.style.left = `${'$'}{rect.left}px`;
              piece.style.top = `${'$'}{rect.top}px`;
              piece.style.width = `${'$'}{rect.width}px`;
              piece.style.height = `${'$'}{rect.height}px`;
              layer.appendChild(piece);
            });
          };
          const drawHighlights = () => {
            clearCssHighlights();
            clearOverlay();
            if (supportsCustomHighlights()) {
              if (activeSentenceRange) CSS.highlights.set('muse-sentence-active', new Highlight(activeSentenceRange));
              if (activeUnitRange) CSS.highlights.set('muse-unit-active', new Highlight(activeUnitRange));
              return;
            }
            drawRects(activeSentenceRange, 'muse-sentence-rect');
            drawRects(activeUnitRange, 'muse-unit-rect');
          };
          const clearHighlights = () => {
            activeSentenceRange = null;
            activeUnitRange = null;
            clearCssHighlights();
            clearOverlay();
          };
          const followRange = (range) => {
            if (!range) return;
            const paged = root.classList.contains('muse-paged');
            if (paged) {
              const scroller = document.getElementById('muse-viewport');
              if (!scroller) return;
              const width = Math.max(1, scroller.clientWidth || window.innerWidth);
              const rect = Array.from(range.getClientRects()).find((item) => item.width > 0 && item.height > 0);
              if (!rect) return;
              const page = Math.max(0, Math.floor((scroller.scrollLeft + rect.left) / width));
              const target = page * width;
              if (Math.abs(scroller.scrollLeft - target) > 1) {
                scroller.scrollLeft = target;
              }
              return;
            }
            const scroller = document.scrollingElement || document.documentElement;
            const rect = range.getBoundingClientRect();
            if (rect.width <= 0 || rect.height <= 0) return;
            const target = Math.max(0, scroller.scrollTop + rect.top - window.innerHeight * 0.32);
            if (Math.abs(scroller.scrollTop - target) > 1) {
              scroller.scrollTo({ top: target, behavior: 'smooth' });
            }
          };
          const applyHighlight = (unitIndex, sentenceIndex, autoFollow) => {
            if (state.unit === unitIndex && state.sentence === sentenceIndex && state.autoFollow === autoFollow) return;
            const previousSentence = state.sentence;
            const sentenceRange = sentenceIndex >= 0 ? rangeFor(sentenceRanges[sentenceIndex]) : null;
            const unitRange = unitIndex >= 0 ? rangeFor(unitRanges[unitIndex]) : null;
            activeSentenceRange = sentenceRange;
            activeUnitRange = unitRange;
            drawHighlights();
            if (autoFollow && previousSentence >= 0 && previousSentence !== sentenceIndex) {
              followRange(sentenceRange || unitRange);
            }
            state.unit = unitIndex;
            state.sentence = sentenceIndex;
            state.autoFollow = autoFollow;
          };
          window.__museHighlightIndex = true;
          window.__museReadAlongState = state;
          window.__museApplyHighlight = applyHighlight;
          window.__museClearHighlights = clearHighlights;
          window.__museRefreshHighlightOverlay = () => {
            if (!supportsCustomHighlights()) drawHighlights();
          };
          window.__museInvalidateHighlightRanges = () => {
            const request = { unit: state.unit, sentence: state.sentence, autoFollow: state.autoFollow };
            textNodes = null;
            state.unit = -2;
            state.sentence = -2;
            clearHighlights();
            if (request.unit >= 0 || request.sentence >= 0) {
              applyHighlight(request.unit, request.sentence, false);
            }
          };
          const pending = window.__musePendingHighlight;
          if (pending) {
            delete window.__musePendingHighlight;
            applyHighlight(pending.unit, pending.sentence, pending.autoFollow);
          }
        })();
    """.trimIndent()
}

internal fun buildPrecomputedHighlightScript(
    textIndex: ReadAlongTextIndex,
    activeUnitIndex: Int,
    activeSentenceIndex: Int,
    autoFollow: Boolean
): String {
    val validSentence = activeSentenceIndex in textIndex.sentenceRanges.indices &&
        !textIndex.sentenceRanges[activeSentenceIndex].isEmpty()
    val validUnit = activeUnitIndex in textIndex.unitRanges.indices &&
        !textIndex.unitRanges[activeUnitIndex].isEmpty()
    val sentence = if (validSentence) activeSentenceIndex else -1
    val unit = if (validUnit) activeUnitIndex else -1
    return """
        (() => {
          const request = { unit: $activeUnitIndex, sentence: $sentence, autoFollow: $autoFollow };
          if (window.__museApplyHighlight) {
            window.__museApplyHighlight(request.unit, request.sentence, request.autoFollow);
          } else {
            window.__musePendingHighlight = request;
          }
        })();
    """.trimIndent()
}

private fun annotationsToJson(annotations: List<ReadAlongAnnotation>): String {
    val array = JSONArray()
    annotations.forEach { ann ->
        array.put(
            JSONObject().apply {
                put("id", ann.id)
                put("start", ann.charStart)
                put("end", ann.charEnd)
                put("color", ann.color.name.lowercase())
                put("href", ann.chapterHref)
                put("elementId", ann.elementId.orEmpty())
                put("note", ann.note)
            }
        )
    }
    return array.toString()
}
