package com.tianhuiu.solvex.mode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome

/**
 * 统一通用模式。
 * 合并了原有的常规模式与自动模式，提供最均衡的体验。
 */
object UniversalMode : Mode {
    override val id = "universal"
    override val displayName = "通用模式"
    override val description = "全能解析，支持自动打开抽屉"
    override val icon = Icons.Default.AutoAwesome
    override val shouldCrop = true
    override fun defaultConfig() = ModeConfig(autoOpenDrawer = true)
}
