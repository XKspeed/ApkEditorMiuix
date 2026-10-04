package com.apkeditor.miuix.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 方法列表图标（编辑器顶栏 method 导航用）：列表样式（三行「圆点 + 横线」）。
 * miuix 图标库里没有合适的方法图标，手绘，颜色跟随 tint。
 */
@Composable
fun MethodNavIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 24f
        val stroke = 2f * scale
        listOf(6f, 12f, 18f).forEach { y ->
            drawCircle(
                color = tint,
                radius = 1.5f * scale,
                center = androidx.compose.ui.geometry.Offset(4.5f * scale, y * scale),
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(8.5f * scale, y * scale),
                end = androidx.compose.ui.geometry.Offset(19.5f * scale, y * scale),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
