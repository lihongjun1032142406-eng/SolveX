package com.tianhuiu.solvex.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.models.DrawerSide
import com.tianhuiu.solvex.data.models.HistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 抽屉管理器。
 */
class DrawerManager(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var observeJob: Job? = null
    private val currentItem = mutableStateOf<HistoryItem?>(null)

    // 实时 UI 缓冲
    private var liveQuery = ""
    private var liveResult = ""

    private val autoScroll = mutableStateOf(true)

    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                WindowManager.LayoutParams::class.java
                    .getMethod("setTrustedOverlay", Boolean::class.javaPrimitiveType)
                    .invoke(this, true)
            } catch (_: Exception) { }
        }
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
    }

    private var lastHistoryId: String? = null

    /**
     * 显示抽屉。
     */
    fun show(
        historyId: String,
        side: DrawerSide,
        widthPercent: Float,
        showMetadata: Boolean = false,
        autoScrollEnabled: Boolean = true
    ) {
        autoScroll.value = autoScrollEnabled

        if (lastHistoryId != historyId) {
            clearLiveBuffer()
        }
        lastHistoryId = historyId

        if (composeView != null) {
            updateContent(historyId)
            return
        }

        val finalWidthPercent = widthPercent.coerceIn(0.3f, 0.9f)

        composeView = ComposeView(context).apply {
            (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }

            setContent {
                MaterialTheme {
                    val item by currentItem
                    val displayItem = item?.let {
                        it.copy(
                            query = liveQuery.ifEmpty { it.query },
                            result = liveResult.ifEmpty { it.result }
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 背景遮罩，点击关闭
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { hide() }
                        )

                        // 抽屉内容
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(finalWidthPercent)
                                .align(if (side == DrawerSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                                .clickable(enabled = false) {}
                        ) {
                            DrawerView(
                                item = displayItem,
                                showMetadata = showMetadata,
                                autoScroll = autoScroll.value,
                                onClose = { hide() }
                            )
                        }
                    }
                }
            }
        }

        windowManager.addView(composeView, layoutParams)
        updateContent(historyId)
    }

    private fun updateContent(historyId: String) {
        observeJob?.cancel()
        observeJob = scope.launch {
            historyRepository.historyItemsFlow.collect { items ->
                currentItem.value = items.find { it.id == historyId }
            }
        }
    }

    /**
     * 实时追加提取文本。
     */
    fun appendLiveQuery(delta: String) {
        liveQuery += delta
        currentItem.value = currentItem.value?.copy(query = liveQuery)
    }

    /**
     * 覆盖式设置提取文本（用于无障碍取字等一次性场景）。
     */
    fun setLiveQuery(text: String) {
        liveQuery = text
        currentItem.value = currentItem.value?.copy(query = liveQuery)
    }

    /**
     * 实时追加解析结果。
     */
    fun appendLiveResult(delta: String) {
        liveResult += delta
        currentItem.value = currentItem.value?.copy(result = liveResult)
    }

    /**
     * 清除实时缓冲区。
     */
    fun clearLiveBuffer() {
        liveQuery = ""
        liveResult = ""
        // 触发一次 recomposition 用 DB 数据刷新
        currentItem.value = currentItem.value
    }

    /**
     * 更新防截屏标志。
     */
    fun updateSecureFlag(enabled: Boolean) {
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

    /**
     * 隐藏抽屉。
     */
    fun hide() {
        observeJob?.cancel()
        observeJob = null
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

    fun isShowing(): Boolean = composeView != null
}
