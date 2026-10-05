package com.deepseek.balancewidget.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.balancewidget.core.CoverCrop
import com.deepseek.balancewidget.data.CompactCoverStore
import kotlin.math.roundToInt

/** 裁剪窗口固定 280dp 见方 —— 不管导入多大的图，框大小都不变。 */
private val ViewportSize = 280.dp

/** 输出图上圆角半径：卡片 22dp / 180dp 宽 ≈ 512px 图上的 63px。 */
private const val OUTPUT_CORNER_RADIUS = 63f

private val CropBg = Color(0xF20B1420)
private val CropPanel = Color(0xFF16202E)
private val BrandBlue = Color(0xFF1B6BFF)
private val TextDim = Color(0xFF8FA6C0)

/**
 * 封面裁剪界面：固定大小的正方形取景框，图片可拖动 / 双指缩放，框内所见即所得。
 *
 * @param source 已经解码好的原图（见 [com.deepseek.balancewidget.data.CoverImageLoader]）
 * @param onConfirm 回调裁剪结果（[CompactCoverStore.OUTPUT_SIZE] 见方）
 */
@Composable
fun CoverCropScreen(
    source: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val viewportPx = with(LocalDensity.current) { ViewportSize.toPx() }
    val image = remember(source) { source.asImageBitmap() }
    val minScale = remember(source, viewportPx) {
        CoverCrop.minScale(source.width, source.height, viewportPx)
    }
    var scale by remember(source) { mutableStateOf(minScale) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var rounded by remember(source) { mutableStateOf(true) }

    // 返回键 = 取消裁剪，而不是退出整个设置页
    BackHandler(onBack = onCancel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CropBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("裁剪封面图", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "拖动移动位置，双指缩放；框内就是插件上显示的画面",
                color = TextDim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))

            val frameShape = RoundedCornerShape(if (rounded) 22.dp else 0.dp)
            Canvas(
                modifier = Modifier
                    .size(ViewportSize)
                    .clip(frameShape)
                    .background(CropPanel)
                    .border(2.dp, BrandBlue, frameShape)
                    .pointerInput(source, minScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(minScale, minScale * 8f)
                            val (x, y) = CoverCrop.clampOffset(
                                source.width,
                                source.height,
                                viewportPx,
                                next,
                                offset.x + pan.x,
                                offset.y + pan.y,
                            )
                            scale = next
                            offset = Offset(x, y)
                        }
                    },
            ) {
                val (dx, dy) = CoverCrop.drawOffset(
                    source.width,
                    source.height,
                    size.width,
                    scale,
                    offset.x,
                    offset.y,
                )
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                    dstSize = IntSize(
                        (source.width * scale).roundToInt(),
                        (source.height * scale).roundToInt(),
                    ),
                    filterQuality = FilterQuality.Medium,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = rounded,
                    onClick = { rounded = !rounded },
                    label = { Text("圆角", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandBlue,
                        selectedLabelColor = Color.White,
                        labelColor = TextDim,
                    ),
                )
                OutlinedButton(onClick = {
                    scale = minScale
                    offset = Offset.Zero
                }) { Text("复位") }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel) { Text("取消") }
                Button(
                    onClick = {
                        onConfirm(
                            renderCrop(
                                source = source,
                                viewportPx = viewportPx,
                                scale = scale,
                                offsetX = offset.x,
                                offsetY = offset.y,
                                rounded = rounded,
                            ),
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                ) { Text("确定") }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "输出 ${CompactCoverStore.OUTPUT_SIZE}×${CompactCoverStore.OUTPUT_SIZE} PNG" +
                    "（约 300–800KB，透明背景会保留）",
                color = TextDim,
                fontSize = 11.sp,
            )
        }
    }
}

/** 把取景框里的画面渲染成成品位图。 */
private fun renderCrop(
    source: Bitmap,
    viewportPx: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rounded: Boolean,
): Bitmap {
    val rect = CoverCrop.sourceRect(source.width, source.height, viewportPx, scale, offsetX, offsetY)
    val left = rect.left.roundToInt().coerceIn(0, source.width - 1)
    val top = rect.top.roundToInt().coerceIn(0, source.height - 1)
    val right = (rect.left + rect.size).roundToInt().coerceIn(left + 1, source.width)
    val bottom = (rect.top + rect.size).roundToInt().coerceIn(top + 1, source.height)

    val size = CompactCoverStore.OUTPUT_SIZE
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.drawBitmap(
        source,
        Rect(left, top, right, bottom),
        Rect(0, 0, size, size),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )

    if (rounded) {
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(mask).drawRoundRect(
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            OUTPUT_CORNER_RADIUS,
            OUTPUT_CORNER_RADIUS,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK },
        )
        @Suppress("DEPRECATION")
        val xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(mask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.xfermode = xfermode })
        mask.recycle()
    }
    return output
}
