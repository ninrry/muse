package luzzr.muse.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.NotificationCompat
import luzzr.muse.MuseApp
import luzzr.muse.R
import luzzr.muse.data.network.LrcLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * System overlay service that displays synchronized floating lyrics
 * above all apps (NetEase Music-style 悬浮歌词).
 *
 * Architecture:
 * - Creates a TYPE_APPLICATION_OVERLAY window via WindowManager
 * - Observes PlayerState for lyrics data and current line
 * - Renders lyrics using a custom ViewGroup with animated transitions
 * - Supports drag-to-reposition and tap-to-toggle visibility
 */
class FloatingLyricsService : Service() {

    companion object {
        const val ACTION_SHOW = "luzzr.muse.action.FLOATING_LYRICS_SHOW"
        const val ACTION_HIDE = "luzzr.muse.action.FLOATING_LYRICS_HIDE"
        const val ACTION_CLOSE = "luzzr.muse.action.FLOATING_LYRICS_CLOSE"
        const val CHANNEL_ID = "muse_floating_lyrics"
        const val NOTIFICATION_ID = 1002
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var windowManager: WindowManager
    private lateinit var playerState: PlayerState
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var overlayContainer: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // Lyrics UI elements
    private var currentLineText: TextView? = null
    private var prevLineText: TextView? = null
    private var nextLineText: TextView? = null
    private var emptyHint: TextView? = null

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Saved position
    private var savedX = 0
    private var savedY = 600 // default Y offset from top

    // Track current lyrics data
    private var lastKnownLines: List<LrcLine> = emptyList()
    private var lastKnownLineIndex: Int = -1

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        playerState = (application as MuseApp).playerState

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮歌词",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示悬浮歌词时保持服务运行"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_CLOSE -> {
                playerState.updateFloatingLyricsEnabled(false)
                hideOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlayContainer != null) return // already showing

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val container = inflater.inflate(R.layout.overlay_floating_lyrics, null) as FrameLayout

        // Cache UI references
        currentLineText = container.findViewById(R.id.lyrics_current)
        prevLineText = container.findViewById(R.id.lyrics_prev)
        nextLineText = container.findViewById(R.id.lyrics_next)
        emptyHint = container.findViewById(R.id.lyrics_empty_hint)

        // Restore saved position
        container.x = savedX.toFloat()
        container.y = savedY.toFloat()

        // Touch handling for drag
        container.setOnTouchListener { _, event ->
            onTouch(container, event)
            true
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = savedY
        }

        windowManager.addView(container, params)
        overlayContainer = container
        overlayParams = params

        // Show persistent notification for the overlay service
        startForeground(NOTIFICATION_ID, buildOverlayNotification())

        // Observe lyrics data
        observeLyrics()

        // Fade-in animation
        container.alpha = 0f
        container.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun hideOverlay() {
        val container = overlayContainer ?: return

        // Save position before hiding
        savedX = container.x.toInt()
        savedY = container.y.toInt()

        // Fade-out animation
        container.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                try {
                    windowManager.removeView(container)
                } catch (_: Exception) { }
                overlayContainer = null
                overlayParams = null
                serviceScope.coroutineContext.cancelChildren()
            }
            .start()
    }

    private fun observeLyrics() {
        serviceScope.launch {
            combine(
                playerState.currentLyrics,
                playerState.currentLyricLine
            ) { lyrics, lineIndex ->
                Pair(lyrics, lineIndex)
            }.collect { (lyrics, lineIndex) ->
                lastKnownLines = lyrics
                lastKnownLineIndex = lineIndex
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
            // No lyrics available — show hint
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

        val validIndex = currentIndex.coerceIn(0, lyrics.size - 1)
        val currentLine = lyrics[validIndex]
        val prevLine = lyrics.getOrNull(validIndex - 1)
        val nextLine = lyrics.getOrNull(validIndex + 1)

        // Animate current line text change
        animateTextChange(current, currentLine.text)
        animateTextChange(prev, prevLine?.text ?: "")
        animateTextChange(next, nextLine?.text ?: "")
    }

    /**
     * Smoothly transition text with cross-fade animation
     */
    private fun animateTextChange(textView: TextView, newText: String) {
        if (textView.text == newText) return

        textView.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                params.x = initialX + dx
                params.y = initialY + dy

                // Clamp to screen bounds
                val displayMetrics = resources.displayMetrics
                params.x = params.x.coerceIn(
                    -view.width / 2,
                    displayMetrics.widthPixels - view.width / 2
                )
                params.y = params.y.coerceIn(0, displayMetrics.heightPixels - view.height)

                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) { }
            }
            MotionEvent.ACTION_UP -> {
                // Save final position
                savedX = params.x
                savedY = params.y
            }
        }
        return true
    }

    /**
     * Build persistent notification for the floating lyrics service.
     * Includes a "关闭" action to stop the overlay.
     */
    private fun buildOverlayNotification(): Notification {
        val closeIntent = Intent(this, FloatingLyricsService::class.java).apply {
            action = ACTION_CLOSE
        }
        val closePending = PendingIntent.getForegroundService(
            this, 1, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, luzzr.muse.MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮歌词")
            .setContentText("歌词已悬浮显示")
            .setSmallIcon(R.drawable.ic_lyrics)
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setColorized(true)
            .setColor(0xFF8B7355.toInt())
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "关闭", closePending
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        hideOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceJob.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
