package com.tianhuiu.solvex.floating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tianhuiu.solvex.service.SolveXAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * 选区模式枚举。
 *
 * @property LIVE_SCAN 实时扫描模式：窗口背景透明，通过无障碍服务扫描节点中的文字
 * @property IMAGE_CROP 图片裁剪模式：窗口背景黑色，在已截取的静态图上选择区域进行裁剪
 */
enum class RegionMode {
    LIVE_SCAN,
    IMAGE_CROP
}

/**
 * 无障碍取字区域选择管理器。
 *
 * 管理两层窗口结构：
 * - 外层 [FrameLayout]：控制全屏位置，仅在 [IMAGE_CROP] 模式下显示黑色背景和截图
 * - 内层 [SelectionDrawLayout]：负责绘制选区框、四角操作柄，并处理触控拖动与缩放
 *
 * 使用 [suspendCancellableCoroutine] 提供挂起式回调，调用方通过 [selectRegion] 挂起等待用户操作结果。
 *
 * @property context Android 上下文
 */
class TextRegionManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val windowParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                WindowManager.LayoutParams::class.java
                    .getMethod("setTrustedOverlay", Boolean::class.javaPrimitiveType)
                    .invoke(this, true)
            } catch (_: Exception) { }
        }
    }

    private var selectionWindow: FrameLayout? = null
    private var drawLayout: SelectionDrawLayout? = null

    val isActive: Boolean get() = selectionWindow != null

    suspend fun selectRegion(
        mode: RegionMode = RegionMode.LIVE_SCAN,
        bitmap: android.graphics.Bitmap? = null
    ): RegionSelection? = suspendCancellableCoroutine { cont ->
        show(
            mode = mode,
            background = bitmap,
            onConfirm = { selection ->
                cont.resume(selection)
                hide()
            },
            onCancel = {
                cont.resume(null)
                hide()
            }
        )
        cont.invokeOnCancellation { hide() }
    }

    fun hide() {
        selectionWindow?.let { w ->
            if (w.isAttachedToWindow) {
                try { windowManager.removeView(w) } catch (_: Exception) { }
            }
        }
        selectionWindow = null
        drawLayout = null
    }

    fun updateScreenProtection(enabled: Boolean) {
        if (enabled) {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        selectionWindow?.let {
            try { windowManager.updateViewLayout(it, windowParams) } catch (_: Exception) { }
        }
    }

    private fun show(
        mode: RegionMode,
        background: android.graphics.Bitmap? = null,
        onConfirm: (RegionSelection) -> Unit,
        onCancel: () -> Unit
    ) {
        hide()

        val metrics = android.util.DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            metrics.widthPixels = windowMetrics.bounds.width()
            metrics.heightPixels = windowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        // 外层窗口：图片模式下全屏黑色背景
        val outer = FrameLayout(context).apply {
            if (mode == RegionMode.IMAGE_CROP) {
                setBackgroundColor(Color.BLACK)
                background?.let {
                    val iv = android.widget.ImageView(context)
                    iv.setImageBitmap(it)
                    iv.scaleType = android.widget.ImageView.ScaleType.FIT_XY
                    addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
            }
        }

        val innerW = (screenW * 0.7f).toInt().coerceIn(400, 900)
        val innerH = (screenH * 0.4f).toInt().coerceIn(200, 800)

        val inner = SelectionDrawLayout(context).apply {
            this.screenWidth = screenW
            this.screenHeight = screenH
            this.onConfirm = onConfirm
            this.onCancel = onCancel
            this.mode = mode
            if (mode == RegionMode.IMAGE_CROP) {
                this.updateConfirmStatus(0, 0)
            }
        }

        // 内层容器：负责控制选区框的移动
        val innerContainer = FrameLayout(context)
        innerContainer.addView(inner, FrameLayout.LayoutParams(innerW, innerH))
        outer.addView(innerContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = (screenW - innerW) / 2
            topMargin = (screenH - innerH) / 2
        })

        inner.setOnTouchListener { _, event ->
            if (event.pointerCount > 1) return@setOnTouchListener false

            val lp = innerContainer.layoutParams as FrameLayout.LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    inner.initialX = lp.leftMargin
                    inner.initialY = lp.topMargin
                    inner.initialTime = System.currentTimeMillis()
                    inner.initialTouchX = event.rawX
                    inner.initialTouchY = event.rawY

                    inner.isInCorner(event, lp.leftMargin, lp.topMargin, innerContainer.width, innerContainer.height)
                    inner.isResizingDown = false
                    inner.blueLine = inner.isResizing
                    inner.hideButtonsAndClear()

                    return@setOnTouchListener false
                }
                MotionEvent.ACTION_UP -> {
                    inner.isResizing = false
                    if (inner.blueLine) {
                        inner.blueLine = false
                        inner.invalidate()
                    }

                    val duration = System.currentTimeMillis() - inner.initialTime
                    val moved = abs(event.rawX - inner.initialTouchX) < 10f &&
                            abs(event.rawY - inner.initialTouchY) < 10f

                    if (duration < 200L && moved) {
                        if (mode == RegionMode.LIVE_SCAN) {
                            inner.triggerScan(lp.leftMargin, lp.topMargin)
                        } else {
                            inner.updateConfirmStatus(lp.leftMargin, lp.topMargin)
                        }
                    }

                    inner.restoreButtons()
                    return@setOnTouchListener false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (inner.isResizing) {
                        inner.updateSizeFromCorner(event, innerContainer, screenW, screenH)
                        return@setOnTouchListener true
                    } else if (!inner.isResizingDown) {
                        lp.leftMargin = (inner.initialX + (event.rawX - inner.initialTouchX).toInt())
                            .coerceIn(0, screenW - innerContainer.width)
                        lp.topMargin = (inner.initialY + (event.rawY - inner.initialTouchY).toInt())
                            .coerceIn(0, screenH - innerContainer.height)
                        innerContainer.layoutParams = lp
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }

        selectionWindow = outer
        drawLayout = inner
        windowManager.addView(outer, windowParams)
    }
}

/**
 * 选区选择结果。
 *
 * @property region 选区在屏幕坐标系中的矩形区域（相对于全屏左上角）
 * @property scannedText 无障碍扫描提取到的文本内容，仅 [LIVE_SCAN] 模式使用
 */
class RegionSelection(
    val region: Rect,
    val scannedText: String
)

/**
 * 选区绘制与触控核心视图。
 *
 * 继承 [FrameLayout] 并重写 [onDraw] 在 Canvas 上绘制选区四角操作柄，
 * 支持单指拖拽容器位置、四角拖拽缩放、双指捏合缩放。
 *
 * 画笔的颜色方案在构造时固定，通过 [mode] 分支决定确定/扫描行为。
 */
class SelectionDrawLayout(context: Context) : FrameLayout(context) {

    // 每个捕获的文本块
    private data class TextItem(
        val rect: Rect,
        val text: String
    )

    // 画笔 — 淡色系
    private val redFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED; alpha = 38; style = Paint.Style.FILL
    }
    private val yellowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; alpha = 20; style = Paint.Style.FILL
    }
    private val blueStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE; alpha = 50; style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val cornerWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 12f
    }
    private val cornerBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE; alpha = 100; strokeWidth = 12f
    }

    private val capturedItems = mutableListOf<TextItem>()
    private val hintItems = mutableListOf<TextItem>()
    private var scannedText = ""

    companion object {
        const val CORNER_THRESHOLD = 100
        const val MIN_WIDTH = 200; const val MIN_HEIGHT = 150
    }

    var isResizing = false
    var isResizingDown = false
    var blueLine = false
    private var holdingCorner = 0
    var initialX = 0; var initialY = 0
    var initialTouchX = 0f; var initialTouchY = 0f
    var initialTime = 0L
    private val deltaOffset = IntArray(2)
    private var anchorRight = 0
    private var anchorBottom = 0
    private var pinchInitW = 0; private var pinchInitH = 0
    private var pinchInitDX = 0f; private var pinchInitDY = 0f

    var screenWidth = 0; var screenHeight = 0
    var onConfirm: ((RegionSelection) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var mode: RegionMode = RegionMode.LIVE_SCAN

    // 按钮文字颜色
    private val accentColor: Int

    private val hintView: TextView
    private val btnCancel: Button
    private val btnCopy: Button
    private val btnConfirm: Button
    private val buttonRow: LinearLayout

    init {
        setWillNotDraw(false)
        setBackgroundColor(0x407F7F7F.toInt())

        val attrs = intArrayOf(android.R.attr.colorAccent)
        val ta = context.obtainStyledAttributes(attrs)
        accentColor = ta.getColor(0, 0xFF90CAF9.toInt())
        ta.recycle()

        hintView = TextView(context).apply {
            text = "拖动四角调整选区，点击选区扫描文字"
            setTextColor(Color.WHITE); textSize = 13f
            gravity = Gravity.CENTER; setPadding(16, 4, 16, 4)
        }
        addView(hintView)

        btnCancel = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "取消"; setTextColor(accentColor)
            setOnClickListener { onCancel?.invoke() }
            setPadding(12, 4, 12, 4)
        }
        btnCopy = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "复制"; visibility = GONE; setTextColor(accentColor)
            setOnClickListener {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SolveX", scannedText))
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            setPadding(12, 4, 12, 4)
        }
        btnConfirm = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "点击选区确认"; isEnabled = false; setTextColor(accentColor)
            setOnClickListener {
                val region = screenRect()
                onConfirm?.invoke(RegionSelection(region, scannedText))
            }
            setPadding(12, 4, 12, 4)
        }

        buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(btnCancel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) })
            addView(btnCopy, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) })
            addView(btnConfirm, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) })
        }
        addView(buttonRow)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)

        hintView.measure(
            MeasureSpec.makeMeasureSpec(w - 32, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        buttonRow.measure(
            MeasureSpec.makeMeasureSpec(w - 24, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t

        val hintW = hintView.measuredWidth; val hintH = hintView.measuredHeight
        hintView.layout((w - hintW) / 2, 12, (w + hintW) / 2, 12 + hintH)

        val btnW = buttonRow.measuredWidth; val btnH = buttonRow.measuredHeight
        buttonRow.layout((w - btnW) / 2, h - btnH - 8, (w + btnW) / 2, h - 8)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (item in capturedItems) {
            canvas.drawRect(item.rect, redFill)
            canvas.drawRect(item.rect, blueStroke)
        }
        for (item in hintItems) {
            canvas.drawRect(item.rect, yellowFill)
            canvas.drawRect(item.rect, blueStroke)
        }

        val ll = 100
        val lp = if (blueLine) cornerBlue else cornerWhite

        canvas.drawLine(0f, 0f, ll.toFloat(), 0f, lp)
        canvas.drawLine(0f, 0f, 0f, ll.toFloat(), lp)
        canvas.drawLine(0f, height.toFloat(), ll.toFloat(), height.toFloat(), lp)
        canvas.drawLine(0f, height.toFloat(), 0f, (height - ll).toFloat(), lp)
        canvas.drawLine((width - ll).toFloat(), 0f, width.toFloat(), 0f, lp)
        canvas.drawLine(width.toFloat(), 0f, width.toFloat(), ll.toFloat(), lp)
        canvas.drawLine((width - ll).toFloat(), height.toFloat(), width.toFloat(), height.toFloat(), lp)
        canvas.drawLine(width.toFloat(), (height - ll).toFloat(), width.toFloat(), height.toFloat(), lp)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 1 &&
            (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP)
        ) {
            if (event.action == MotionEvent.ACTION_UP) {
                performClick()
            }
            return true
        }

        if (event.pointerCount == 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    pinchInitW = width; pinchInitH = height
                    pinchInitDX = abs(event.getX(0) - event.getX(1))
                    pinchInitDY = abs(event.getY(0) - event.getY(1))
                    isResizing = true; blueLine = true; invalidate()
                }
                MotionEvent.ACTION_MOVE -> if (isResizing) {
                    val newDX = abs(event.getX(0) - event.getX(1))
                    val newDY = abs(event.getY(0) - event.getY(1))
                    val nw = (pinchInitW + (newDX - pinchInitDX)).toInt()
                    val nh = (pinchInitH + (newDY - pinchInitDY)).toInt()
                    val params = layoutParams
                    params.width = nw.coerceIn(MIN_WIDTH, screenWidth)
                    params.height = nh.coerceIn(MIN_HEIGHT, screenHeight)
                    layoutParams = params
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    isResizing = false; isResizingDown = true
                    blueLine = false; invalidate()
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    fun isInCorner(event: MotionEvent, left: Int, top: Int, w: Int, h: Int) {
        anchorRight = left + w
        anchorBottom = top + h

        when {
            event.rawX < left + CORNER_THRESHOLD && event.rawY < top + CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 1
            }
            event.rawX < left + CORNER_THRESHOLD && event.rawY > anchorBottom - CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 2
            }
            event.rawX > anchorRight - CORNER_THRESHOLD && event.rawY < top + CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 3
            }
            event.rawX > anchorRight - CORNER_THRESHOLD && event.rawY > anchorBottom - CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 4
            }
            else -> isResizing = false
        }
        deltaOffset[0] = (event.rawX - left).toInt()
        deltaOffset[1] = (event.rawY - top).toInt()
    }

    fun updateSizeFromCorner(
        event: MotionEvent,
        innerContainer: View,
        screenW: Int,
        screenH: Int
    ) {
        val lp = innerContainer.layoutParams as FrameLayout.LayoutParams

        when (holdingCorner) {
            1 -> {
                lp.leftMargin = event.rawX.toInt() - deltaOffset[0]
                lp.width = anchorRight - lp.leftMargin
                lp.topMargin = event.rawY.toInt() - deltaOffset[1]
                lp.height = anchorBottom - lp.topMargin
            }
            2 -> {
                lp.leftMargin = event.rawX.toInt() - deltaOffset[0]
                lp.width = anchorRight - lp.leftMargin
                lp.height = event.rawY.toInt() - lp.topMargin + (anchorBottom - lp.topMargin) - deltaOffset[1]
            }
            3 -> {
                lp.width = event.rawX.toInt() - lp.leftMargin + (anchorRight - lp.leftMargin) - deltaOffset[0]
                lp.topMargin = event.rawY.toInt() - deltaOffset[1]
                lp.height = anchorBottom - lp.topMargin
            }
            4 -> {
                lp.width = event.rawX.toInt() - lp.leftMargin + (anchorRight - lp.leftMargin) - deltaOffset[0]
                lp.height = event.rawY.toInt() - lp.topMargin + (anchorBottom - lp.topMargin) - deltaOffset[1]
            }
        }

        lp.width = lp.width.coerceIn(MIN_WIDTH, screenW)
        lp.height = lp.height.coerceIn(MIN_HEIGHT, screenH)
        innerContainer.layoutParams = lp
    }

    fun triggerScan(left: Int, top: Int) {
        val region = Rect(left, top, left + width, top + height)
        val svc = SolveXAccessibilityService.instance
        val result = svc?.scanRegionWithRects(region)

        if (result != null) {
            capturedItems.clear(); hintItems.clear()

            for (item in result.capturedItems) {
                val localRect = Rect(
                    item.rect.left - left, item.rect.top - top,
                    item.rect.right - left, item.rect.bottom - top
                )
                capturedItems.add(TextItem(localRect, item.text))
            }
            for (item in result.hintItems) {
                val localRect = Rect(
                    item.rect.left - left, item.rect.top - top,
                    item.rect.right - left, item.rect.bottom - top
                )
                hintItems.add(TextItem(localRect, item.text))
            }

            rebuildScannedText()

            if (scannedText.isNotEmpty()) {
                hintView.text = "扫描到 ${scannedText.length} 个字符"
                btnConfirm.text = "确认提取"; btnConfirm.isEnabled = true
                btnCopy.visibility = VISIBLE
            } else {
                hintView.text = "拖动四角调整选区，点击选区扫描文字"
                Toast.makeText(context, "当前选区未发现文本，请调整选区后重试", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "扫描失败，请确认无障碍服务已开启", Toast.LENGTH_SHORT).show()
        }
        requestLayout(); invalidate()
    }

    fun updateConfirmStatus(left: Int, top: Int) {
        btnConfirm.text = "确认裁剪区域"; btnConfirm.isEnabled = true
        hintView.text = "区域: ${width}x${height}px"
    }

    private fun rebuildScannedText() {
        val capturedText = capturedItems.joinToString("\n") { it.text }
        val hintText = hintItems.joinToString("\n") { it.text }
        scannedText = listOf(capturedText, hintText).filter { it.isNotEmpty() }.joinToString("\n")
    }

    /**
     * 获取当前视图在屏幕上的矩形区域。
     *
     * @return 视图左上角及右下角在屏幕坐标系中的 Rect
     */
    fun screenRect(): Rect {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + width, loc[1] + height)
    }

    fun hideButtonsAndClear() {
        btnCancel.visibility = INVISIBLE; btnCopy.visibility = INVISIBLE
        btnConfirm.visibility = INVISIBLE
        hintView.visibility = INVISIBLE
        capturedItems.clear(); hintItems.clear(); invalidate()
    }

    fun restoreButtons() {
        btnCancel.visibility = VISIBLE
        btnCopy.visibility = VISIBLE
        btnConfirm.visibility = VISIBLE
        hintView.visibility = VISIBLE
    }
}
