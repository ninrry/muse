package luzzr.muse.player

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.media.R

/**
 * 系统悬浮歌词（不使用 startForeground，避免 FGS 类型崩溃）。
 * 进程由 MusicService 媒体前台服务保活。
 */
@AndroidEntryPoint
class FloatingLyricsService : Service() {

    companion object {
        const val ACTION_SHOW = "luzzr.muse.action.FLOATING_LYRICS_SHOW"
        const val ACTION_HIDE = "luzzr.muse.action.FLOATING_LYRICS_HIDE"
        const val ACTION_CLOSE = "luzzr.muse.action.FLOATING_LYRICS_CLOSE"
        private const val PREFS = "floating_lyrics_prefs"
        private const val KEY_X = "overlay_x"
        private const val KEY_Y = "overlay_y"
        private const val DEFAULT_Y = 600
        private const val TAG = "FloatingLyrics"
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Inject lateinit var playerState: PlayerState
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private var observeJob: Job? = null

    private var overlayContainer: FloatingLyricsContainer? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private var currentLineText: TextView? = null
    private var prevLineText: TextView? = null
    private var nextLineText: TextView? = null
    private var emptyHint: TextView? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMoved = false
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private var savedX = 0
    private var savedY = DEFAULT_Y
    private var isShowing = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        savedX = prefs.getInt(KEY_X, 0)
        savedY = prefs.getInt(KEY_Y, DEFAULT_Y)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            when (intent?.action) {
                ACTION_SHOW -> showOverlay()
                ACTION_HIDE -> {
                    hideOverlay(stop = true)
                }
                ACTION_CLOSE -> {
                    if (::playerState.isInitialized) {
                        playerState.updateFloatingLyricsEnabled(false)
                    }
                    hideOverlay(stop = true)
                }
                else -> showOverlay()
            }
            START_STICKY
        } catch (e: Exception) {
            MuseLog.e(TAG, "onStartCommand failed", e)
            if (::playerState.isInitialized) {
                playerState.updateFloatingLyricsEnabled(false)
            }
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showOverlay() {
        if (isShowing && overlayContainer != null) {
            observeLyrics()
            return
        }

        if (!android.provider.Settings.canDrawOverlays(this)) {
            MuseLog.w(TAG, "SYSTEM_ALERT_WINDOW not granted")
            if (::playerState.isInitialized) {
                playerState.updateFloatingLyricsEnabled(false)
            }
            stopSelf()
            return
        }

        try {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val container = inflater.inflate(
                R.layout.overlay_floating_lyrics,
                null
            ) as FloatingLyricsContainer

            currentLineText = container.findViewById(R.id.lyrics_current)
            prevLineText = container.findViewById(R.id.lyrics_prev)
            nextLineText = container.findViewById(R.id.lyrics_next)
            emptyHint = container.findViewById(R.id.lyrics_empty_hint)

            // 初始显示
            currentLineText?.apply {
                alpha = 1f
                textSize = 18f
                setTextColor(0xFFF3ECE4.toInt())
            }
            prevLineText?.apply {
                alpha = 0.45f
                textSize = 13f
            }
            nextLineText?.apply {
                alpha = 0.45f
                textSize = 13f
            }

            container.contentDescription = getString(R.string.floating_lyrics_accessibility)
            container.isClickable = true
            container.setOnClickListener {
                // 单击不关闭，避免误触；长按关闭在 touch 中处理
            }
            container.setOnTouchListener { view, event -> onTouch(view, event) }

            val metrics = resources.displayMetrics
            val maxY = (metrics.heightPixels * 0.85f).toInt().coerceAtLeast(DEFAULT_Y)
            val safeY = savedY.coerceIn(0, maxY)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = safeY
            }

            windowManager.addView(container, params)
            overlayContainer = container
            overlayParams = params
            isShowing = true

            container.alpha = 0f
            container.animate()
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()

            observeLyrics()
            // 立即刷一帧当前状态
            if (::playerState.isInitialized) {
                updateLyricsDisplay(
                    playerState.currentLyrics.value,
                    playerState.currentLyricLine.value
                )
            }
        } catch (e: Exception) {
            MuseLog.e(TAG, "showOverlay failed", e)
            isShowing = false
            overlayContainer = null
            overlayParams = null
            if (::playerState.isInitialized) {
                playerState.updateFloatingLyricsEnabled(false)
            }
            stopSelf()
        }
    }

    private fun hideOverlay(stop: Boolean) {
        val container = overlayContainer
        if (container == null) {
            if (stop) stopSelf()
            return
        }

        observeJob?.cancel()
        observeJob = null
        isShowing = false

        persistPosition()

        container.animate().cancel()
        container.animate()
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                mainHandler.post {
                    try {
                        if (container.isAttachedToWindow) {
                            windowManager.removeView(container)
                        }
                    } catch (e: Exception) {
                        MuseLog.w(TAG, "removeView failed", e)
                    }
                    overlayContainer = null
                    overlayParams = null
                    currentLineText = null
                    prevLineText = null
                    nextLineText = null
                    emptyHint = null
                    if (stop) stopSelf()
                }
            }
            .start()
    }

    private fun observeLyrics() {
        if (!::playerState.isInitialized) return
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            combine(
                playerState.currentLyrics,
                playerState.currentLyricLine
            ) { lyrics, lineIndex -> lyrics to lineIndex }
                .collect { (lyrics, lineIndex) ->
                    updateLyricsDisplay(lyrics, lineIndex)
                }
        }
    }

    private fun updateLyricsDisplay(lyrics: List<LrcLine>, currentIndex: Int) {
        val current = currentLineText ?: return
        val prev = prevLineText ?: return
        val next = nextLineText ?: return
        val empty = emptyHint ?: return

        if (lyrics.isEmpty() || currentIndex < 0) {
            current.visibility = View.GONE
            prev.visibility = View.GONE
            next.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE
        current.visibility = View.VISIBLE
        prev.visibility = View.VISIBLE
        next.visibility = View.VISIBLE

        val validIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
        setTextCrossfade(current, lyrics[validIndex].text)
        setTextCrossfade(prev, lyrics.getOrNull(validIndex - 1)?.text.orEmpty())
        setTextCrossfade(next, lyrics.getOrNull(validIndex + 1)?.text.orEmpty())
    }

    private fun setTextCrossfade(textView: TextView, newText: String) {
        if (textView.text?.toString() == newText) return
        textView.animate().cancel()
        textView.animate()
            .alpha(0f)
            .setDuration(100)
            .withEndAction {
                textView.text = newText
                // 当前行更亮
                val isCurrent = textView.id == R.id.lyrics_current
                textView.animate()
                    .alpha(if (isCurrent) 1f else 0.45f)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                hasMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                hasMoved = hasMoved || abs(dx) > touchSlop || abs(dy) > touchSlop
                if (!hasMoved) return true

                val displayMetrics = resources.displayMetrics
                params.x = (initialX + dx).coerceIn(
                    -displayMetrics.widthPixels / 3,
                    displayMetrics.widthPixels / 3
                )
                params.y = (initialY + dy).coerceIn(
                    0,
                    displayMetrics.heightPixels - view.height.coerceAtLeast(1)
                )
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) {
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (hasMoved) {
                    savedX = params.x
                    savedY = params.y
                    persistPosition()
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    // 短按关闭
                    if (::playerState.isInitialized) {
                        playerState.updateFloatingLyricsEnabled(false)
                    }
                    hideOverlay(stop = true)
                }
                hasMoved = false
            }
        }
        return true
    }

    private fun persistPosition() {
        val params = overlayParams
        if (params != null) {
            savedX = params.x
            savedY = params.y
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_X, savedX)
            .putInt(KEY_Y, savedY)
            .apply()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceJob.cancel()
        serviceScope.cancel()
        val container = overlayContainer
        if (container != null) {
            try {
                container.animate().cancel()
                if (container.isAttachedToWindow) {
                    windowManager.removeView(container)
                }
            } catch (_: Exception) {
            }
        }
        overlayContainer = null
        overlayParams = null
        isShowing = false
        super.onDestroy()
    }
}
