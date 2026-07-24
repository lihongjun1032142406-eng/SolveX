package com.tianhuiu.solvex.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

/**
 * 系统级工具集，封装振动反馈、剪贴板操作、Toast 提示等通用功能。
 * 所有方法均为静态委托，无需实例化。
 */
object SystemUtils {
    @Volatile
    private var cachedVibrator: Vibrator? = null

    private fun getVibrator(context: Context): Vibrator {
        return cachedVibrator ?: synchronized(this) {
            cachedVibrator ?: run {
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                cachedVibrator = v
                v
            }
        }
    }

    /**
     * 触发设备振动反馈。
     *
     * @param context Android 上下文
     * @param durationMillis 振动持续时间（毫秒），默认 100ms
     */
    fun vibrate(context: Context, durationMillis: Long = 100) {
        val vibrator = getVibrator(context.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMillis)
        }
    }

    fun vibrateSuccess(context: Context) = vibrate(context, 100)
    fun vibrateError(context: Context) = vibrate(context, 300)

    /**
     * 将文本复制到系统剪贴板。
     *
     * @param context Android 上下文
     * @param text 待复制的文本内容
     */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SolveX Answer", text))
    }

    /**
     * 统一结果投递：复制到剪贴板并提示。
     *
     * @param context Android 上下文
     * @param text 待复制的文本内容（空内容不执行任何操作）
     */
    fun deliverResult(context: Context, text: String) {
        if (text.isBlank()) return
        copyToClipboard(context, text)
        showToast(context, "已复制到剪贴板")
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showFeedback(
        context: Context,
        userMessage: String,
        detailedLog: String? = null,
        tag: String = "SolveX",
        priority: Int = android.util.Log.ERROR,
        throwable: Throwable? = null
    ) {
        Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
        val logContent = detailedLog ?: userMessage
        val fullLog =
            logContent + (throwable?.let { "\n" + android.util.Log.getStackTraceString(it) } ?: "")
        android.util.Log.println(priority, tag, fullLog)
    }

    /**
     * 裁剪 Bitmap。
     */
    fun cropBitmap(source: android.graphics.Bitmap, rect: android.graphics.Rect): android.graphics.Bitmap? {
        return try {
            val left = rect.left.coerceIn(0, source.width - 1)
            val top = rect.top.coerceIn(0, source.height - 1)
            val width = rect.width().coerceIn(1, source.width - left)
            val height = rect.height().coerceIn(1, source.height - top)
            android.graphics.Bitmap.createBitmap(source, left, top, width, height)
        } catch (e: Exception) {
            android.util.Log.e("SolveX", "裁剪失败: rect=$rect, bitmap=${source.width}x${source.height}", e)
            null
        }
    }

    /**
     * 检查无障碍服务是否已启用。
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val serviceName = "${context.packageName}/${serviceClass.name}"
        val raw = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        // 部分 ROM 可能包含空格、换行或多余分隔符
        return raw.split(':').any { it.trim() == serviceName }
    }
}
