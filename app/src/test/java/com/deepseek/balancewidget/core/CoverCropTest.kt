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
        val viewport = 300f
        val images = listOf(
            1000 to 500,
            500 to 1000,
            2048 to 1536,
            300 to 300,
            4000 to 3000,
            1 to 1,
        )
        for ((w, h) in images) {
            // 下限必须用真正的「铺满」缩放：低于它图片就盖不住取景框，本来就会有透明边
            val minScale = CoverCrop.minScale(w, h, viewport)
            for (scale in listOf(minScale, minScale * 2f, minScale * 8f)) {
                for (offset in listOf(-99999f, -37f, 0f, 37f, 99999f)) {
                    val (cx, cy) = CoverCrop.clampOffset(w, h, viewport, scale, offset, offset)
                    val rect = CoverCrop.sourceRect(w, h, viewport, scale, cx, cy)
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
    fun `低于铺满缩放时会露出图片外的区域`() {
        // 这解释了为什么 App 里必须用 minScale 当下限：2048x1536 在 300 视口里，
        // 真正的铺满比例是 300/1536 ≈ 0.195，而不是 300/2048
        assertEquals(300f / 1536f, CoverCrop.minScale(2048, 1536, 300f), 0.0001f)
        val tooSmall = 300f / 2048f
        val rect = CoverCrop.sourceRect(2048, 1536, 300f, tooSmall, 0f, 0f)
        assertTrue("这个比例下高度盖不满，取景框会超出图片", rect.size > 1536f)
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
