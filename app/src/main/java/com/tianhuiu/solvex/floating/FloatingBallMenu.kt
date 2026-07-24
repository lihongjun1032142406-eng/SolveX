package com.tianhuiu.solvex.floating

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val MenuRadius = 104.dp
private val ButtonSize = 46.dp
private val ContainerSize = 58.dp
private val ScreenMargin = 8.dp

/**
 * 扇形菜单数据条目。
 */
data class FloatingMenuEntry(
    val id: String,
    val icon: ImageVector,
    val color: Color,
    val isActive: Boolean = false
)

/**
 * 扇形弹出菜单。
 * 
 * @param anchorX 锚点 X
 * @param anchorY 锚点 Y
 * @param enabledItems 当前允许显示的菜单项 ID 集合（不在此集合内的项将直接隐藏）
 * @param activeItems 当前应高亮显示的菜单项 ID 集合
 * @param onItemClick 点击回调
 * @param onDismiss 关闭回调
 * @param onDismissFinished 动画结束回调
 */
@Composable
fun FloatingBallMenu(
    anchorX: Int,
    anchorY: Int,
    enabledItems: Set<String>,
    activeItems: Set<String> = emptySet(),
    onItemClick: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewportWidthPx: Float? = null,
    viewportHeightPx: Float? = null,
    closeSignal: Int = 0
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = viewportWidthPx ?: (configuration.screenWidthDp.toFloat() * density.density)
    val screenHeightPx =
        viewportHeightPx ?: (configuration.screenHeightDp.toFloat() * density.density)

    val anchorCenterX = with(density) { anchorX.toDp() }
    val anchorCenterY = with(density) { anchorY.toDp() }

    // 主色调
    val themeColor = Color(0xFF6750A4)

    val entries = remember(enabledItems, activeItems) {
        listOf(
            FloatingMenuEntry(
                "engine",
                Icons.Default.SettingsSuggest,
                themeColor,
                isActive = "engine" in activeItems
            ),
            FloatingMenuEntry(
                "search",
                Icons.Default.Cloud,
                themeColor,
                isActive = "search" in activeItems
            ),
            FloatingMenuEntry("settings", Icons.Default.Settings, themeColor, isActive = false),
            FloatingMenuEntry("baidu", Icons.Default.Language, themeColor, isActive = false)
        ).filter { it.id in enabledItems }
    }

    if (entries.isEmpty()) {
        LaunchedEffect(Unit) { onDismissFinished() }
        return
    }

    val relativeX = anchorX.toFloat() / screenWidthPx
    val relativeY = anchorY.toFloat() / screenHeightPx

    val startAngle = remember(relativeX, relativeY) {
        val dx = 0.5f - relativeX
        val dy = relativeY - 0.5f
        val centerAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
        centerAngleDeg - 45.0
    }
    val step = if (entries.size > 1) 90.0 / (entries.size - 1) else 0.0

    val clampedRadius =
        remember(anchorX, anchorY, screenWidthPx, screenHeightPx, entries.size, startAngle) {
            computeClampedRadius(
                anchorX,
                anchorY,
                startAngle,
                step,
                entries.size,
                screenWidthPx,
                screenHeightPx,
                density.density
            )
        }

    var closing by remember { mutableStateOf(false) }
    val expansionProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        expansionProgress.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
    }

    LaunchedEffect(closing) {
        if (closing) {
            expansionProgress.animateTo(
                0f,
                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
            )
            onDismissFinished()
        }
    }

    LaunchedEffect(closeSignal) {
        if (closeSignal > 0 && !closing) {
            closing = true
            onDismiss()
        }
    }

    fun requestClose() {
        if (!closing) {
            closing = true
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.12f * expansionProgress.value))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { requestClose() }
            )
    ) {
        entries.forEachIndexed { index, entry ->
            val angleDeg = startAngle + index * step
            val angleRad = Math.toRadians(angleDeg)
            val currentRadius = clampedRadius * expansionProgress.value
            val targetX = anchorCenterX + (currentRadius.value * cos(angleRad)).dp
            val targetY = anchorCenterY - (currentRadius.value * sin(angleRad)).dp

            MenuItem(
                entry = entry,
                centerX = targetX,
                centerY = targetY,
                index = index,
                totalCount = entries.size,
                closing = closing,
                onClick = {
                    onItemClick(entry.id)
                    requestClose()
                }
            )
        }
    }
}

private fun computeClampedRadius(
    anchorX: Int,
    anchorY: Int,
    startAngle: Double,
    step: Double,
    entryCount: Int,
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: Float
): Dp {
    val maxRadiusPx = MenuRadius.value * density
    val marginPx = ScreenMargin.value * density
    val halfContainerPx = (ContainerSize.value * density) / 2f
    var clampedPx = maxRadiusPx

    for (index in 0 until entryCount) {
        val angleDeg = startAngle + index * step
        val angleRad = Math.toRadians(angleDeg)
        val targetX = anchorX + (clampedPx * cos(angleRad)).toFloat()
        val targetY = anchorY - (clampedPx * sin(angleRad)).toFloat()

        val minX = targetX - halfContainerPx
        if (minX < marginPx) {
            val overshoot = marginPx - minX
            clampedPx -= overshoot / abs(cos(angleRad).toFloat()).coerceAtLeast(0.01f)
        }
        val maxX = targetX + halfContainerPx
        if (maxX > screenWidthPx - marginPx) {
            val overshoot = maxX - (screenWidthPx - marginPx)
            clampedPx -= overshoot / abs(cos(angleRad).toFloat()).coerceAtLeast(0.01f)
        }
        val minY = targetY - halfContainerPx
        if (minY < marginPx) {
            val overshoot = marginPx - minY
            clampedPx -= overshoot / abs(sin(angleRad).toFloat()).coerceAtLeast(0.01f)
        }
        val maxY = targetY + halfContainerPx
        if (maxY > screenHeightPx - marginPx) {
            val overshoot = maxY - (screenHeightPx - marginPx)
            clampedPx -= overshoot / abs(sin(angleRad).toFloat()).coerceAtLeast(0.01f)
        }
    }
    return (clampedPx.coerceAtLeast(ButtonSize.value * density) / density).dp
}

@Composable
private fun MenuItem(
    entry: FloatingMenuEntry,
    centerX: Dp,
    centerY: Dp,
    index: Int,
    totalCount: Int,
    closing: Boolean,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(180, delayMillis = index * 35))
        alpha.animateTo(1f, tween(180, delayMillis = index * 35))
    }

    LaunchedEffect(closing) {
        if (closing) {
            val reverseDelay = (totalCount - 1 - index) * 35
            scale.animateTo(0f, tween(160, delayMillis = reverseDelay))
            alpha.animateTo(0f, tween(160, delayMillis = reverseDelay))
        }
    }

    val topLeftX = centerX - ContainerSize / 2
    val topLeftY = centerY - ContainerSize / 2

    Box(
        modifier = Modifier
            .offset(topLeftX, topLeftY)
            .size(ContainerSize)
            .scale(scale.value)
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(ButtonSize)
                .shadow(3.dp, CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                ),
            shape = CircleShape,
            color = if (entry.isActive) entry.color else Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = if (entry.isActive) Color.White else entry.color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
