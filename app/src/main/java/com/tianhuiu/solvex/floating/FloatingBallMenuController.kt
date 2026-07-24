package com.tianhuiu.solvex.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 扇形菜单控制器。
 *
 * 管理一个全屏 ComposeView 覆盖层，在其上渲染 [FloatingBallMenu] 组合项。
 * 支持点击空白区域关闭，并通过回调委托菜单项点击事件给调用方。
 *
 * @property context Android 上下文
 * @property windowManager 窗口管理器，用于添加/移除覆盖层
 * @property getEnabledItems 返回当前可见的菜单项 ID 集合
 * @property getActiveItems 返回当前处于激活状态的菜单项 ID 集合
 * @property onItemClick 菜单项点击回调
 */
class FloatingBallMenuController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val getEnabledItems: () -> Set<String>,
    private val getActiveItems: () -> Set<String> = { emptySet() },
    private val onItemClick: (String) -> Unit
) {
    private var menuView: ComposeView? = null

    fun show(anchorX: Int, anchorY: Int) {
        if (menuView != null) return

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.2f
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
        }

        val view = ComposeView(context).apply {
            (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }

            setContent {
                MaterialTheme {
                    FloatingBallMenu(
                        anchorX = anchorX,
                        anchorY = anchorY,
                        enabledItems = getEnabledItems(),
                        activeItems = getActiveItems(),
                        onItemClick = { id ->
                            onItemClick(id)
                        },
                        onDismiss = {
                            // 动画开始前可以做逻辑，但目前逻辑在 onDismissFinished
                        },
                        onDismissFinished = {
                            dismiss()
                        }
                    )
                }
            }
        }

        menuView = view
        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            menuView = null
        }
    }

    fun dismiss() {
        menuView?.let { view ->
            if (view.parent != null) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) { }
            }
        }
        menuView = null
    }

    fun isVisible(): Boolean = menuView != null
}
