package com.tianhuiu.solvex.floating

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianhuiu.solvex.data.models.FloatingBallAppearance
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 悬浮球视图
 */
const val BALL_HIDDEN_RATIO = 0.35f

@Composable
fun FloatingBallView(
    status: BallStatus,
    displayMode: BallDisplayMode,
    isAtLeftEdge: Boolean,
    ballText: String? = null,
    appearance: FloatingBallAppearance = FloatingBallAppearance(),
    isStealthMode: Boolean = false,
) {
    val ballFullSizeDp = appearance.diameterDp
    // 可配置尺寸
    val baseSize =
        if (displayMode == BallDisplayMode.FULL) ballFullSizeDp.dp else (ballFullSizeDp * 0.6f).dp
    val baseWidth =
        if (displayMode == BallDisplayMode.FULL) ballFullSizeDp.dp else (ballFullSizeDp * BALL_HIDDEN_RATIO).dp

    val targetHeight = if (displayMode == BallDisplayMode.FULL) baseSize else baseSize * 3
    val size by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "size"
    )

    val width by animateDpAsState(
        targetValue = baseWidth,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "width"
    )

    val rotation: Float = if (status == BallStatus.RUNNING) {
        val rotationTransition = rememberInfiniteTransition(label = "rotation")
        val animatedFloat by rotationTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        animatedFloat
    } else 0f

    val backgroundColor = when (status) {
        BallStatus.IDLE -> Color(0xFF2196F3)
        BallStatus.LOW_PROFILE -> Color(0xFF9E9E9E)
        BallStatus.RUNNING -> Color(0xFF673AB7)
        BallStatus.SUCCESS -> Color(0xFF4CAF50)
        BallStatus.ERROR -> Color(0xFFF44336)
        BallStatus.PROTECTED -> Color(0xFFFFA726)
    }

    val finalAlpha = (if (isStealthMode) {
        // 隐匿模式下的透明度策略：整体大幅降低可见度
        when (status) {
            BallStatus.PROTECTED -> if (displayMode == BallDisplayMode.FULL) 0.45f else 0.15f
            BallStatus.LOW_PROFILE -> if (displayMode == BallDisplayMode.FULL) 0.25f else 0.12f
            BallStatus.ERROR -> if (displayMode == BallDisplayMode.FULL) 0.5f else 0.35f
            BallStatus.SUCCESS -> if (displayMode == BallDisplayMode.FULL) 0.5f else 0.3f
            BallStatus.RUNNING -> if (displayMode == BallDisplayMode.FULL) 0.55f else 0.35f
            BallStatus.IDLE -> if (displayMode == BallDisplayMode.FULL) 0.4f else 0.2f
        }
    } else {
        // 普通模式下的透明度
        when (status) {
            BallStatus.PROTECTED -> if (displayMode == BallDisplayMode.FULL) 0.6f else 0.2f
            BallStatus.LOW_PROFILE -> if (displayMode == BallDisplayMode.FULL) 0.3f else 0.15f
            BallStatus.ERROR -> if (displayMode == BallDisplayMode.FULL) 1.0f else 0.8f
            BallStatus.SUCCESS -> if (displayMode == BallDisplayMode.FULL) 1.0f else 0.6f
            BallStatus.RUNNING -> if (displayMode == BallDisplayMode.FULL) 1.0f else 0.55f
            BallStatus.IDLE -> if (displayMode == BallDisplayMode.FULL) 1f else 0.4f
        }
    }) * appearance.overallOpacity

    val contentAlpha = appearance.contentOpacity

    Box(
        modifier = Modifier
            .size(width = width, height = size)
            .clip(
                if (displayMode == BallDisplayMode.FULL)
                    CircleShape
                else
                    RoundedCornerShape(
                        topStart = if (isAtLeftEdge) 0.dp else 8.dp,
                        bottomStart = if (isAtLeftEdge) 0.dp else 8.dp,
                        topEnd = if (isAtLeftEdge) 8.dp else 0.dp,
                        bottomEnd = if (isAtLeftEdge) 8.dp else 0.dp
                    )
            )
            .background(backgroundColor.copy(alpha = finalAlpha))
            .padding(if (displayMode == BallDisplayMode.FULL) 4.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (displayMode == BallDisplayMode.FULL) {
            Box(modifier = Modifier.alpha(contentAlpha)) {
                when (status) {
                    BallStatus.IDLE, BallStatus.LOW_PROFILE -> {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    BallStatus.PROTECTED -> {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "已开启隐匿保护",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize().padding(2.dp)
                        )
                    }

                    BallStatus.RUNNING -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(appearance.spinnerDiameterDp.dp),
                                color = Color.White,
                                strokeWidth = 3.dp,
                                trackColor = Color.Transparent
                            )
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size((appearance.diameterDp * 0.5f).dp)
                                    .rotate(rotation)
                            )
                        }
                    }

                    BallStatus.SUCCESS -> {
                        if (!ballText.isNullOrBlank()) {
                            val scrollState = rememberScrollState()

                            // 自动滚动逻辑：针对长文本开启循环滚动
                            if (ballText.length > 2) {
                                LaunchedEffect(ballText, displayMode) {
                                    if (displayMode != BallDisplayMode.FULL) return@LaunchedEffect
                                    while (isActive) {
                                        delay(1500)
                                        scrollState.animateScrollTo(
                                            scrollState.maxValue,
                                            animationSpec = tween(
                                                durationMillis = (ballText.length * 400).coerceIn(
                                                    2000,
                                                    8000
                                                ),
                                                easing = LinearEasing
                                            )
                                        )
                                        delay(1000)
                                        scrollState.scrollTo(0)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ballText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = appearance.textSizeSp.sp,
                                    maxLines = 1,
                                    modifier = Modifier.horizontalScroll(scrollState)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    BallStatus.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
