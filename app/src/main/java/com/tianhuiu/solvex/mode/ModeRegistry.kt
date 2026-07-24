package com.tianhuiu.solvex.mode

/**
 * 模式注册中心。
 * 全局已统一采用通用模式，通过设置项灵活切换解析行为。
 */
object ModeRegistry {
    fun getUniversal(): Mode = UniversalMode

    val all: List<Mode> get() = listOf(UniversalMode)
}
