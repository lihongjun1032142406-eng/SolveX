package com.tianhuiu.solvex.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tianhuiu.solvex.SolveXApplication
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * 提供基于无障碍服务的静默截屏能力。
 */
class SolveXAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SolveXAccessibilityService? = null
            private set

        /**
         * 标记服务是否已被系统标记为中断/不可用。
         */
        @Volatile
        var isInterrupted = false
            private set

        /**
         * 用于处理截屏数据的单线程执行器。
         */
        private val screenshotExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "SolveX-Screenshot").apply { priority = Thread.NORM_PRIORITY }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SolveXA11y", "Accessibility service created")
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            instance = this
            isInterrupted = false
            Log.d("SolveXA11y", "Accessibility service connected")
            // 通知 ViewModel 刷新权限状态
            try {
                (applicationContext as? SolveXApplication)?.viewModel?.checkPermissions()
            } catch (_: Exception) { }
        } catch (e: Exception) {
            Log.e("SolveXA11y", "onServiceConnected failed", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理具体事件，但必须捕获异常防止冒泡到 system_server
        // 如果未捕获的异常从这里抛出，系统会标记服务为故障状态
    }

    override fun onInterrupt() {
        // 系统通知服务中断（通常发生在进程被强制终止前）。
        // 必须立即清理实例引用，否则 system_server 检测到服务无响应时会标记为故障。
        Log.d("SolveXA11y", "Accessibility service interrupted")
        isInterrupted = true
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SolveXA11y", "Accessibility service destroyed")
        instance = null
        // 通知 ViewModel 刷新权限状态
        try {
            (applicationContext as? SolveXApplication)?.viewModel?.checkPermissions()
        } catch (_: Exception) { }
    }

    /**
     * 单个文字块的矩形信息。
     */
    data class TextRectInfo(
        val rect: Rect,
        val text: String
    )

    /**
     * 带矩形反馈的区域扫描结果。每个文字块与矩形一一对应。
     */
    class RegionScanResult(
        val text: String,
        val capturedItems: List<TextRectInfo>,
        val hintItems: List<TextRectInfo>
    )

    // 常见 UI 元素描述，不应作为文本内容
    private val uiNoisePatterns = setOf(
         "image", "icon", "banner", "ad", "navigation",
        "toolbar",  "tab", "menu", "link", "logo", "avatar"
    )

    /**
     * 扫描指定区域，返回文本及节点矩形（用于 UI 视觉反馈）。
     * capturedItems: 完全包含在选区内节点的矩形与文字（绘制红色）
     * hintItems: 部分重叠节点的矩形与文字（绘制黄色）
     */
    fun scanRegionWithRects(region: Rect, expandedPx: Int = 30): RegionScanResult {
        if (isInterrupted) {
            Log.w("SolveXA11y", "scanRegionWithRects: service is interrupted")
            return RegionScanResult("", emptyList(), emptyList())
        }
        
        val expanded = Rect(region)
        expanded.inset(-expandedPx, -expandedPx)
        val text = StringBuilder(1024)
        val visited = HashSet<Int>()
        val addedTexts = HashSet<String>()
        val captured = mutableListOf<TextRectInfo>()
        val hints = mutableListOf<TextRectInfo>()

        // 尝试从根节点扫描
        val root = rootInActiveWindow
        if (root != null) {
            try {
                scanRects(root, text, visited, addedTexts, expanded, captured, hints)
            } finally {
                root.recycle()
            }
        }

        // 如果根节点扫描结果为空，且有权限访问所有窗口，则遍历所有窗口
        if (captured.isEmpty() && hints.isEmpty()) {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (window in windowList.reversed()) {
                    val windowRoot = window.root ?: continue
                    try {
                        scanRects(windowRoot, text, visited, addedTexts, expanded, captured, hints)
                    } finally {
                        windowRoot.recycle()
                    }
                }
            }
        }

        Log.d("SolveXA11y", "scanRegionWithRects: ${text.length} chars, ${captured.size} captured, ${hints.size} hints")
        return RegionScanResult(text.toString().trim(), captured, hints)
    }

    private fun scanRects(
        node: AccessibilityNodeInfo,
        out: StringBuilder,
        visited: HashSet<Int>,
        addedTexts: HashSet<String>,
        region: Rect,
        captured: MutableList<TextRectInfo>,
        hints: MutableList<TextRectInfo>
    ) {
        if (!visited.add(System.identityHashCode(node))) return
        try {
            // 有些视图可能被标记为不可见但实际上有内容，或者坐标在屏幕外但在 Region 内（比如 ScrollView 内部）
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            // 如果节点在选区之外太远，直接跳过其子节点优化性能
            if (!Rect.intersects(region, bounds) && !region.contains(bounds)) {
                 // 注意：不能直接 return，因为子节点可能在 region 内（例如父节点很大但不可见）
                 // 但如果 bounds 是空的，大概率其子节点也没戏
                 if (bounds.isEmpty) return
            }

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val piece = when {
                !text.isNullOrBlank() -> text
                !desc.isNullOrBlank() && desc.length > 2 &&
                    uiNoisePatterns.none { desc.contains(it, ignoreCase = true) } -> desc
                else -> null
            }

            if (piece != null && piece.isNotEmpty() && addedTexts.add(piece)) {
                if (region.contains(bounds)) {
                    captured.add(TextRectInfo(Rect(bounds), piece))
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(piece)
                } else if (Rect.intersects(region, bounds)) {
                    hints.add(TextRectInfo(Rect(bounds), piece))
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(piece)
                }
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanRects(child, out, visited, addedTexts, region, captured, hints)
                child.recycle()
            }
        } catch (e: Exception) {
            Log.w("SolveXA11y", "scanRects node error", e)
        }
    }

    suspend fun takeScreenshotCompat(): Bitmap? {
        if (isInterrupted) {
            Log.w("SolveXA11y", "takeScreenshotCompat: service is interrupted")
            return null
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w("SolveXA11y", "takeScreenshot requires API 30+")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val buffer = screenshot.hardwareBuffer
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    buffer,
                                    screenshot.colorSpace
                                )
                                // copy 在后台线程执行
                                val mutableBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                                if (mutableBitmap !== bitmap && bitmap != null) {
                                    bitmap.recycle()
                                }
                                buffer.close()
                                if (cont.isActive) cont.resume(mutableBitmap)
                            } catch (e: Exception) {
                                Log.e("SolveXA11y", "Screenshot processing failed", e)
                                if (cont.isActive) cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e("SolveXA11y", "takeScreenshot failed: $errorCode")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("SolveXA11y", "takeScreenshot exception", e)
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
