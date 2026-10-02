package com.apkeditor.miuix.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 指南针图标（miuix 图标库没有，手绘）：圆环 + 斜置菱形指针。
 * 与顶栏其它图标同高（22dp），颜色跟随传入 tint。
 */
@Composable
fun CompassIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 24f          // 以 24 视口设计，缩放到画布
        val stroke = 2f * scale      // 圆环线宽
        val center = Offset(s / 2f, s / 2f)

        // 外圈圆环
        drawCircle(
            color = tint,
            radius = s / 2f - stroke / 2f,
            center = center,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // 指针：斜置菱形（NE-SW 方向，经典指南针样式）
        val needle = Path().apply {
            moveTo(16.4f * scale, 7.6f * scale)   // 右上尖
            lineTo(13.4f * scale, 13.4f * scale)  // 右下折点
            lineTo(7.6f * scale, 16.4f * scale)   // 左下尖
            lineTo(10.6f * scale, 10.6f * scale)  // 左上折点
            close()
        }
        drawPath(needle, color = tint)

        // 中心轴点
        drawCircle(color = tint, radius = 1.1f * scale, center = center)
    }
}
