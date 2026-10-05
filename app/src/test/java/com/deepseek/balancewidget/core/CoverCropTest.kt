package com.deepseek.balancewidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 封面裁剪几何单测（纯 JVM）。
 *
 * 覆盖：铺满所需最小缩放、位移钳制、取景框对应的原图区域、以及各种图片/缩放下
 * 取景框一定落在图片内部（否则裁剪出来会有透明边）。
 */
class CoverCropTest {

    @Test
    fun `最小缩放让图片铺满正方形取景框`() {
        // 宽图（1000x500）按高度铺满
        assertEquals(0.6f, CoverCrop.minScale(1000, 500, 300f), 0.0001f)
        // 高图（500x1000）按宽度铺满
        assertEquals(0.6f, CoverCrop.minScale(500, 1000, 300f), 0.0001f)
        // 正方形就是 1:1
        assertEquals(1f, CoverCrop.minScale(300, 300, 300f), 0.0001f)
        // 异常输入不崩
        assertEquals(1f, CoverCrop.minScale(0, 100, 300f), 0.0001f)
    }

    @Test
    fun `位移被钳制在图片边缘之内`() {
        // 1000x500 按 0.6 缩放后是 600x300：水平可移 ±150，垂直刚好铺满不可移
        val (x, y) = CoverCrop.clampOffset(1000, 500, 300f, 0.6f, 999f, 999f)
        assertEquals(150f, x, 0.001f)
        assertEquals(0f, y, 0.001f)

        val (negX, negY) = CoverCrop.clampOffset(1000, 500, 300f, 0.6f, -999f, -999f)
        assertEquals(-150f, negX, 0.001f)
        assertEquals(0f, negY, 0.001f)
    }

    @Test
    fun `居中时取景框正好是图片正中间那块`() {
        val rect = CoverCrop.sourceRect(1000, 500, 300f, 0.6f, 0f, 0f)
        assertEquals(500f, rect.size, 0.001f)          // 300 / 0.6
        assertEquals(250f, rect.left, 0.001f)          // (1000 - 500) / 2
        assertEquals(0f, rect.top, 0.001f)             // (500 - 500) / 2
    }

    @Test
    fun `任何缩放与位移下取景框都在图片内部`() {
        val images = listOf(
            Triple(1000, 500, 0.6f),
            Triple(500, 1000, 0.6f),
            Triple(2048, 1536, 300f / 2048f),
            Triple(300, 300, 1f),
            Triple(4000, 3000, 300f / 4000f),
        )
        for ((w, h, minScale) in images) {
            for (scale in listOf(minScale, minScale * 2f, minScale * 8f)) {
                for (offset in listOf(-99999f, -37f, 0f, 37f, 99999f)) {
                    val (cx, cy) = CoverCrop.clampOffset(w, h, 300f, scale, offset, offset)
                    val rect = CoverCrop.sourceRect(w, h, 300f, scale, cx, cy)
                    assertTrue("left=${rect.left} ($w x $h @$scale)", rect.left >= -0.01f)
                    assertTrue("top=${rect.top} ($w x $h @$scale)", rect.top >= -0.01f)
                    assertTrue(
                        "right=${rect.left + rect.size} ($w x $h @$scale)",
                        rect.left + rect.size <= w + 0.01f,
                    )
                    assertTrue(
                        "bottom=${rect.top + rect.size} ($w x $h @$scale)",
                        rect.top + rect.size <= h + 0.01f,
                    )
                }
            }
        }
    }

    @Test
    fun `预览绘制位置与取景框一致`() {
        // 画布位置 × 缩放 = -取景框左上角，两边必须对得上
        val (dx, dy) = CoverCrop.drawOffset(1000, 500, 300f, 0.6f, 0f, 0f)
        assertEquals(-150f, dx, 0.001f)   // (300 - 600) / 2
        assertEquals(0f, dy, 0.001f)      // (300 - 300) / 2
        val rect = CoverCrop.sourceRect(1000, 500, 300f, 0.6f, 0f, 0f)
        assertEquals(rect.left * 0.6f, -dx, 0.01f)
        assertEquals(rect.top * 0.6f, -dy, 0.01f)
    }
}
