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
 *
 * 支持功能：
 * - 拖动移动位置
 * - 锁定歌词（锁定后无法通过短按关闭，但可拖动）
 * - 穿透点击（锁定后可点击穿透到底层应用）
 */
@AndroidEntryPoint
class FloatingLyricsService : Service() {

    companion object {
        const val ACTION_SHOW = "luzzr.muse.action.FLOATING_LYRICS_SHOW"
        const val ACTION_HIDE = "luzzr.muse.action.FLOATING_LYRICS_HIDE"
        const val ACTION_CLOSE = "luzzr.muse.action.FLOATING_LYRICS_CLOSE"
        const val ACTION_TOGGLE_LOCK = "luzzr.muse.action.FLOATING_LYRICS_TOGGLE_LOCK"
        private const val PREFS = "floating_lyrics_prefs"
        private const val KEY_X = "overlay_x"
        private const val KEY_Y = "overlay_y"
        private const val DEFAULT_Y = 600
        private const val TAG = "FloatingLyrics"
        private const val LOCK_LONG_PRESS_DURATION_MS = 800L  // 长按800ms锁定
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Inject lateinit var lyricsState: FloatingLyricsStateHolder
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private var observeJob: Job? = null

    private var overlayContainer: FloatingLyricsContainer? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private var currentLineText: TextView? = null
    private var prevLineText: TextView? = null
    private var nextLineText: TextView? = null
    private var emptyHint: TextView? = null
    private var lockIndicator: TextView? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMoved = false
    private var touchStartTime = 0L
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private var savedX = 0
    private var savedY = DEFAULT_Y
    private var isShowing = false

    // 锁定状态
    private var isLocked = false

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
                    if (::lyricsState.isInitialized) {
                        lyricsState.updateFloatingLyricsEnabled(false)
                    }
                    hideOverlay(stop = true)
                }
                ACTION_TOGGLE_LOCK -> {
                    toggleLock()
                }
                else -> showOverlay()
            }
            START_STICKY
        } catch (e: Exception) {
            MuseLog.e(TAG, "onStartCommand failed", e)
            if (::lyricsState.isInitialized) {
                lyricsState.updateFloatingLyricsEnabled(false)
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
            MuseLog.i(TAG, "SYSTEM_ALERT_WINDOW not granted")
            if (::lyricsState.isInitialized) {
                lyricsState.updateFloatingLyricsEnabled(false)
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
            lockIndicator = container.findViewById(R.id.lyrics_lock_indicator)

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

            // 锁定指示器初始隐藏
            lockIndicator?.visibility = View.GONE

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
            if (::lyricsState.isInitialized) {
                updateLyricsDisplay(
                    lyricsState.currentLyrics.value,
                    lyricsState.currentLyricLine.value
                )
            }
        } catch (e: Exception) {
            MuseLog.e(TAG, "showOverlay failed", e)
            isShowing = false
            overlayContainer = null
            overlayParams = null
            if (::lyricsState.isInitialized) {
                lyricsState.updateFloatingLyricsEnabled(false)
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
        isLocked = false

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
                    lockIndicator = null
                    if (stop) stopSelf()
                }
            }
            .start()
    }

    private fun observeLyrics() {
        if (!::lyricsState.isInitialized) return
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            combine(
                lyricsState.currentLyrics,
                lyricsState.currentLyricLine
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

    private fun toggleLock() {
        isLocked = !isLocked
        updateLockState()
    }

    private fun updateLockState() {
        val params = overlayParams ?: return
        val container = overlayContainer ?: return
        val indicator = lockIndicator

        try {
            if (isLocked) {
                // 锁定状态：添加 FLAG_NOT_TOUCHABLE 实现穿透点击
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                indicator?.visibility = View.VISIBLE
                // 降低歌词透明度作为锁定视觉反馈
                currentLineText?.alpha = 0.85f
                prevLineText?.alpha = 0.3f
                nextLineText?.alpha = 0.3f
                MuseLog.d(TAG, "Lyrics locked - touch passthrough enabled")
            } else {
                // 解锁状态：移除穿透标志
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                indicator?.visibility = View.GONE
                // 恢复歌词透明度
                currentLineText?.alpha = 1f
                prevLineText?.alpha = 0.45f
                nextLineText?.alpha = 0.45f
                MuseLog.d(TAG, "Lyrics unlocked - touch passthrough disabled")
            }
            windowManager.updateViewLayout(container, params)
        } catch (e: Exception) {
            MuseLog.e(TAG, "Failed to update lock state", e)
        }
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
                touchStartTime = System.currentTimeMillis()
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
                val pressDuration = System.currentTimeMillis() - touchStartTime

                if (hasMoved) {
                    savedX = params.x
                    savedY = params.y
                    persistPosition()
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    // 短按操作
                    if (pressDuration >= LOCK_LONG_PRESS_DURATION_MS) {
                        // 长按：切换锁定状态
                        toggleLock()
                    } else if (!isLocked) {
                        // 未锁定时短按关闭
                        if (::lyricsState.isInitialized) {
                            lyricsState.updateFloatingLyricsEnabled(false)
                        }
                        hideOverlay(stop = true)
                    }
                    // 锁定状态下短按不执行任何操作（允许穿透点击）
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
        isLocked = false
        super.onDestroy()
    }
}