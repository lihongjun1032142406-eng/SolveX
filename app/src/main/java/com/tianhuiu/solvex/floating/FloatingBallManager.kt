package com.tianhuiu.solvex.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tianhuiu.solvex.data.models.FloatingBallAppearance
import kotlin.math.abs

class FloatingBallManager(private val context: Context) {

    companion object {
        private const val AUTO_HIDE_DELAY_MS = 5000L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                WindowManager.LayoutParams::class.java
                    .getMethod("setTrustedOverlay", Boolean::class.javaPrimitiveType)
                    .invoke(this, true)
            } catch (_: Exception) { }
        }
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 500
    }

    private var composeView: ComposeView? = null

    private val _enableAutoHide = mutableStateOf(value = true)
    var enableAutoHide: Boolean
        get() = _enableAutoHide.value
        set(value) {
            _enableAutoHide.value = value
            if (!value) {
                handler.removeCallbacks(hideRunnable)
                if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                    displayMode = BallDisplayMode.FULL
                    snapToEdge()
                }
            } else {
                resetHideTimer()
            }
        }
    var appearance by mutableStateOf(FloatingBallAppearance())
    var status by mutableStateOf(BallStatus.IDLE)
    private var displayMode by mutableStateOf(BallDisplayMode.FULL)
    private var isAtLeftEdge by mutableStateOf(value = true)
    private var ballText by mutableStateOf<String?>(null)

    private val handler = Handler(Looper.getMainLooper())

    var defaultIdleStatus: BallStatus = BallStatus.IDLE

    private val hideRunnable = Runnable {
        displayMode = BallDisplayMode.HIDDEN_STRIP
        snapToEdge()
        onBallAutoHidden?.invoke()
    }

    var onSingleClick: (() -> Unit)? = null
    var onDoubleClick: ((x: Int, y: Int) -> Unit)? = null
    var onLongClick: (() -> Unit)? = null
    var onBallAutoHidden: (() -> Unit)? = null

    fun show() {
        val existing = composeView
        if ((existing != null) && (existing.parent != null)) return

        if (existing != null) {
            windowManager.addView(existing, layoutParams)
            resetHideTimer()
            return
        }

        composeView = ComposeView(context).apply {
            (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }

            setContent {
                FloatingBallView(
                    status = status,
                    displayMode = displayMode,
                    isAtLeftEdge = isAtLeftEdge,
                    ballText = ballText,
                    appearance = appearance,
                    isStealthMode = defaultIdleStatus == BallStatus.LOW_PROFILE
                )
            }

            setOnTouchListener(FloatingBallTouchListener())
        }

        windowManager.addView(composeView, layoutParams)
        resetHideTimer()
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        handler.post {
            composeView?.let {
                if (it.parent != null) {
                    try {
                        windowManager.removeView(it)
                    } catch (_: Exception) {
                    }
                }
            }
            composeView = null
        }
    }

    fun updateSecureFlag(enabled: Boolean) {
        handler.post {
            val currentFlags = layoutParams.flags
            val newFlags = if (enabled) {
                currentFlags or WindowManager.LayoutParams.FLAG_SECURE
            } else {
                currentFlags and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }

            if (currentFlags != newFlags) {
                layoutParams.flags = newFlags
                composeView?.let {
                    if (it.parent != null) {
                        windowManager.updateViewLayout(it, layoutParams)
                    }
                }
            }
        }
    }

    fun tempHide() {
        handler.removeCallbacks(hideRunnable)
        handler.post {
            composeView?.let {
                if (it.parent != null) {
                    try {
                        windowManager.removeView(it)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun restore() {
        handler.post {
            composeView?.let {
                if (it.parent == null) {
                    try {
                        windowManager.addView(it, layoutParams)
                    } catch (_: Exception) {
                    }
                }
            }
            resetHideTimer()
        }
    }

    // 答案展示保护：非紧急状态不得覆盖正在显示的答案
    fun updateStatus(newStatus: BallStatus) {
        if (ballText != null && status == BallStatus.SUCCESS &&
            newStatus != BallStatus.SUCCESS && newStatus != BallStatus.ERROR) {
            return
        }
        status = newStatus
        if ((newStatus == BallStatus.SUCCESS) || (newStatus == BallStatus.ERROR)) {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed({
                status = defaultIdleStatus
                ballText = null
                if (enableAutoHide) {
                    displayMode = BallDisplayMode.HIDDEN_STRIP
                    snapToEdge()
                }
            }, AUTO_HIDE_DELAY_MS)
        } else {
            resetHideTimer()
        }
    }

    fun showText(text: String) {
        ballText = text
        displayMode = BallDisplayMode.FULL
        snapToEdge()
        updateStatus(BallStatus.SUCCESS)
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(hideRunnable)
        if (enableAutoHide) {
            handler.postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
        }
    }

    private fun getCurrentBallWidthPx(): Int {
        val density = context.resources.displayMetrics.density
        val dpValue =
            if (displayMode == BallDisplayMode.FULL) appearance.diameterDp else (appearance.diameterDp * BALL_HIDDEN_RATIO)
        return (dpValue * density).toInt()
    }

    private fun snapToEdge() {
        val view = composeView ?: return
        if (view.parent == null) return

        val screenWidth = context.resources.displayMetrics.widthPixels
        val ballWidth = getCurrentBallWidthPx()
        val centerX = layoutParams.x + (ballWidth / 2)
        isAtLeftEdge = centerX < (screenWidth / 2)

        layoutParams.x = if (isAtLeftEdge) 0 else screenWidth - ballWidth
        windowManager.updateViewLayout(view, layoutParams)
    }

    private inner class FloatingBallTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isMoving = false
        private var lastLayoutUpdateTime = 0L

        private val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                        displayMode = BallDisplayMode.FULL
                        snapToEdge()
                        resetHideTimer()
                        return true
                    }
                    // 运行中或闲置中都触发各自的单击逻辑
                    onSingleClick?.invoke()
                    resetHideTimer()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                        displayMode = BallDisplayMode.FULL
                        snapToEdge()
                        resetHideTimer()
                        return true
                    }
                    if (displayMode == BallDisplayMode.FULL) {
                        val centerX = layoutParams.x + getCurrentBallWidthPx() / 2
                        val centerY = layoutParams.y + layoutParams.height / 2
                        onDoubleClick?.invoke(centerX, centerY)
                        resetHideTimer()
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                        displayMode = BallDisplayMode.FULL
                        snapToEdge()
                        resetHideTimer()
                    } else if (status != BallStatus.RUNNING) {
                        // 闲置状态长按：原逻辑是切换引擎，此处保留或改为用户要求的逻辑
                        onLongClick?.invoke()
                        resetHideTimer()
                    }
                    // 运行时长按不做特殊逻辑，交由 ACTION_MOVE 处理拖动
                }
            })

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (gestureDetector.onTouchEvent(event)) return true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    // 判断是否满足拖动阈值
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        // 运行状态长按拖动逻辑：如果正在运行，必须是长按触发（简单处理：只要移动就算拖动，符合安卓悬浮球习惯）
                        // 隐藏状态任意触摸都恢复并允许拖动
                        if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                            displayMode = BallDisplayMode.FULL
                            snapToEdge()
                        }
                        
                        isMoving = true
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()

                        val screenWidth = context.resources.displayMetrics.widthPixels
                        val ballWidth = getCurrentBallWidthPx()
                        isAtLeftEdge = (layoutParams.x + (ballWidth / 2)) < (screenWidth / 2)

                        val now = System.currentTimeMillis()
                        if (now - lastLayoutUpdateTime >= 16) {
                            lastLayoutUpdateTime = now
                            val view = composeView
                            if (view != null && view.parent != null) {
                                windowManager.updateViewLayout(view, layoutParams)
                            }
                        }
                        handler.removeCallbacks(hideRunnable)
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isMoving) {
                        snapToEdge()
                        resetHideTimer()
                    }
                    return true
                }
            }
            return false
        }
    }
}
