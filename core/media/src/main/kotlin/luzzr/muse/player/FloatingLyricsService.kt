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
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
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
 * 系统悬浮歌词：
 * - 透明背景 + 有色歌词描边
 * - 拖动定位；点按展开锁定/关闭；长按锁定
 * - 锁定后全穿透、无解锁浮层；解锁仅通过通知栏
 */
@AndroidEntryPoint
class FloatingLyricsService : Service() {

    companion object {
        const val ACTION_SHOW = "luzzr.muse.action.FLOATING_LYRICS_SHOW"
        const val ACTION_HIDE = "luzzr.muse.action.FLOATING_LYRICS_HIDE"
        const val ACTION_CLOSE = "luzzr.muse.action.FLOATING_LYRICS_CLOSE"
        const val ACTION_TOGGLE_LOCK = "luzzr.muse.action.FLOATING_LYRICS_TOGGLE_LOCK"
        const val ACTION_LOCK = "luzzr.muse.action.FLOATING_LYRICS_LOCK"
        const val ACTION_UNLOCK = "luzzr.muse.action.FLOATING_LYRICS_UNLOCK"
        private const val PREFS = "floating_lyrics_prefs"
        private const val KEY_X = "overlay_x"
        private const val KEY_Y = "overlay_y"
        private const val KEY_LOCKED = "overlay_locked"
        private const val DEFAULT_Y = 600
        private const val TAG = "FloatingLyrics"
        private const val CONTROLS_AUTO_HIDE_MS = 3200L
        private const val LONG_PRESS_MS = 550L
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Inject lateinit var lyricsState: FloatingLyricsStateHolder
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
    private var controlsRow: LinearLayout? = null
    private var btnLock: TextView? = null
    private var btnClose: TextView? = null

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
    private var isLocked = false
    private var controlsVisible = false

    private val hideControlsRunnable = Runnable { setControlsVisible(false) }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        savedX = prefs.getInt(KEY_X, 0)
        savedY = prefs.getInt(KEY_Y, DEFAULT_Y)
        isLocked = prefs.getBoolean(KEY_LOCKED, false)
        if (::lyricsState.isInitialized) {
            lyricsState.updateLocked(isLocked)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            when (intent?.action) {
                ACTION_SHOW -> showOverlay()
                ACTION_HIDE -> {
                    syncEnabled(false)
                    hideOverlay(stop = true)
                }
                ACTION_CLOSE -> {
                    syncEnabled(false)
                    hideOverlay(stop = true)
                }
                ACTION_TOGGLE_LOCK -> toggleLock()
                ACTION_LOCK -> setLocked(true)
                ACTION_UNLOCK -> setLocked(false)
                else -> showOverlay()
            }
            START_STICKY
        } catch (e: Exception) {
            MuseLog.e(TAG, "onStartCommand failed", e)
            syncEnabled(false)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncEnabled(enabled: Boolean) {
        if (::playerState.isInitialized) {
            playerState.updateFloatingLyricsEnabled(enabled)
        } else if (::lyricsState.isInitialized) {
            lyricsState.updateFloatingLyricsEnabled(enabled)
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showOverlay() {
        if (isShowing && overlayContainer != null) {
            observeLyrics()
            return
        }

        if (!android.provider.Settings.canDrawOverlays(this)) {
            MuseLog.i(TAG, "SYSTEM_ALERT_WINDOW not granted")
            syncEnabled(false)
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
            controlsRow = container.findViewById(R.id.lyrics_controls)
            btnLock = container.findViewById(R.id.btn_lock)
            btnClose = container.findViewById(R.id.btn_close)

            // 不覆盖 XML 中的文字色；仅保证无背景色
            container.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            container.findViewById<android.view.View>(R.id.lyrics_panel)
                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            btnLock?.setOnClickListener {
                toggleLock()
                setControlsVisible(false)
            }
            btnClose?.setOnClickListener {
                syncEnabled(false)
                hideOverlay(stop = true)
            }

            container.contentDescription = getString(R.string.floating_lyrics_accessibility)
            container.isClickable = true
            container.setOnTouchListener { view, event -> onTouch(view, event) }

            val metrics = resources.displayMetrics
            val maxY = (metrics.heightPixels * 0.88f).toInt().coerceAtLeast(DEFAULT_Y)
            val safeY = savedY.coerceIn(0, maxY)
            val halfW = metrics.widthPixels / 2

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = savedX.coerceIn(-halfW, halfW)
                y = safeY
            }

            windowManager.addView(container, params)
            overlayContainer = container
            overlayParams = params
            isShowing = true
            syncEnabled(true)

            container.alpha = 0f
            container.scaleX = 0.94f
            container.scaleY = 0.94f
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()

            if (isLocked) {
                applyLockFlags(locked = true, animate = false)
            }

            observeLyrics()
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
            syncEnabled(false)
            stopSelf()
        }
    }

    private fun hideOverlay(stop: Boolean) {
        mainHandler.removeCallbacks(hideControlsRunnable)
        val container = overlayContainer
        if (container == null) {
            if (stop) stopSelf()
            return
        }

        observeJob?.cancel()
        observeJob = null
        isShowing = false
        controlsVisible = false
        persistPosition()

        container.animate().cancel()
        container.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(180)
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
                    clearViewRefs()
                    if (stop) stopSelf()
                }
            }
            .start()
    }

    private fun clearViewRefs() {
        overlayContainer = null
        overlayParams = null
        currentLineText = null
        prevLineText = null
        nextLineText = null
        emptyHint = null
        controlsRow = null
        btnLock = null
        btnClose = null
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
        setTextCrossfade(current, lyrics[validIndex].text, isCurrent = true)
        setTextCrossfade(prev, lyrics.getOrNull(validIndex - 1)?.text.orEmpty(), isCurrent = false)
        setTextCrossfade(next, lyrics.getOrNull(validIndex + 1)?.text.orEmpty(), isCurrent = false)
    }

    private fun setTextCrossfade(textView: TextView, newText: String, isCurrent: Boolean) {
        if (textView.text?.toString() == newText) return
        textView.animate().cancel()
        // 锁定时仍保持正常透明度，增强沉浸、无「变暗提示」
        val targetAlpha = if (isCurrent) 1f else 0.42f
        textView.animate()
            .alpha(0f)
            .scaleX(if (isCurrent) 0.97f else 1f)
            .scaleY(if (isCurrent) 0.97f else 1f)
            .setDuration(90)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(targetAlpha)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun setControlsVisible(visible: Boolean) {
        if (isLocked) return
        controlsVisible = visible
        val row = controlsRow ?: return
        mainHandler.removeCallbacks(hideControlsRunnable)
        if (visible) {
            row.visibility = View.VISIBLE
            row.alpha = 0f
            row.animate().alpha(1f).setDuration(160).start()
            mainHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS)
        } else {
            row.animate()
                .alpha(0f)
                .setDuration(140)
                .withEndAction { row.visibility = View.GONE }
                .start()
        }
    }

    private fun toggleLock() {
        setLocked(!isLocked)
    }

    private fun setLocked(locked: Boolean) {
        if (!isShowing && locked) {
            // 未展示时忽略锁定
            return
        }
        isLocked = locked
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_LOCKED, isLocked)
            .apply()
        if (::lyricsState.isInitialized) {
            lyricsState.updateLocked(isLocked)
        }
        applyLockFlags(locked = isLocked, animate = true)
        notifyMusicServiceRefresh()
    }

    private fun notifyMusicServiceRefresh() {
        try {
            startService(
                Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_REFRESH_NOTIFICATION
                }
            )
        } catch (e: Exception) {
            MuseLog.w(TAG, "notifyMusicServiceRefresh failed", e)
        }
    }

    private fun applyLockFlags(locked: Boolean, animate: Boolean) {
        val params = overlayParams ?: return
        val container = overlayContainer ?: return
        try {
            if (locked) {
                // 全穿透，无任何解锁浮层 / 锁定角标
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                controlsRow?.visibility = View.GONE
                controlsVisible = false
                // 保持歌词全亮度沉浸
                currentLineText?.alpha = 1f
                prevLineText?.alpha = 0.42f
                nextLineText?.alpha = 0.42f
                if (animate) {
                    container.animate().alpha(1f).setDuration(160).start()
                }
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                currentLineText?.alpha = 1f
                prevLineText?.alpha = 0.42f
                nextLineText?.alpha = 0.42f
                if (animate) {
                    container.animate().alpha(1f).setDuration(160).start()
                }
            }
            windowManager.updateViewLayout(container, params)
        } catch (e: Exception) {
            MuseLog.e(TAG, "Failed to update lock state", e)
        }
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false
        if (isLocked) return false

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
                val halfW = displayMetrics.widthPixels / 2
                params.x = (initialX + dx).coerceIn(-halfW + 40, halfW - 40)
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
                    if (pressDuration >= LONG_PRESS_MS) {
                        toggleLock()
                    } else {
                        // 短按：展开/收起控制条（不关闭）
                        setControlsVisible(!controlsVisible)
                    }
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
            .putBoolean(KEY_LOCKED, isLocked)
            .apply()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        observeJob?.cancel()
        serviceJob.cancel()
        serviceScope.cancel()
        if (::lyricsState.isInitialized) {
            lyricsState.updateLocked(false)
        }
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
        clearViewRefs()
        isShowing = false
        super.onDestroy()
    }
}
