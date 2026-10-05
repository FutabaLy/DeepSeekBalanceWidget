"""生成桌面插件外观预览图（与 widget_balance.xml 的尺寸/配色 1:1 对应）。

用法：python make_preview.py
输出：widget-preview.png
"""
from PIL import Image, ImageDraw, ImageFont

S = 2  # 超采样倍数，输出 2 倍图更清晰

# ---------------------------------------------------------------- 配色（与 WidgetRenderer.kt 一致）
CARD_OFF = (10, 26, 36)
CARD_PEAK = (28, 18, 6)
OFF_PEAK = (18, 214, 160)
PEAK = (255, 176, 32)
TITLE = (143, 166, 192)
SYNC = (124, 144, 168)
WHITE = (255, 255, 255)
SUB = (198, 214, 232)
BG = (10, 16, 23)

W = 340          # 插件宽度 dp（4 列网格）
PAD_L, PAD_R, PAD_T, PAD_B = 14, 12, 12, 12
CARD_H = 118

FONTS = r"C:\Windows\Fonts"


def font(name, size):
    return ImageFont.truetype(f"{FONTS}\\{name}", size * S)


F_TITLE = font("msyhbd.ttc", 11)
F_BADGE = font("msyhbd.ttc", 11)
F_SYNC = font("msyh.ttc", 10)
F_BAL = font("msyhbd.ttc", 27)
F_CD = font("msyh.ttc", 13)
F_MONO = font("consola.ttf", 13)
F_DAY = font("msyh.ttc", 10)
F_HINT = font("msyh.ttc", 12)
F_CAP = font("msyh.ttc", 11)


def rounded(draw, box, radius, fill):
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def widget_card(off_peak: bool, balance, countdown, day_badge, last_sync, detail):
    """画一张插件卡片，返回 RGBA 图像。"""
    accent = OFF_PEAK if off_peak else PEAK
    bg = CARD_OFF if off_peak else CARD_PEAK
    w, h, r = W * S, CARD_H * S, 18 * S

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 卡片底 + 顶部微亮渐变（用两层半透明叠出层次）
    rounded(d, (0, 0, w - 1, h - 1), r, bg)
    rounded(d, (0, 0, w - 1, h // 3), r, tuple(min(255, c + 12) for c in bg))
    rounded(d, (0, h // 4, w - 1, h - 1), r, bg)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=r, outline=(31, 48, 70), width=1 * S)

    x = PAD_L * S
    y = PAD_T * S

    # ---- 第一行：标题 + 徽章 + 同步时间
    d.text((x, y), "DeepSeek 余额", font=F_TITLE, fill=TITLE)
    tw = d.textlength("DeepSeek 余额", font=F_TITLE)

    badge_text = "谷" if off_peak else "峰"
    bw = d.textlength(badge_text, font=F_BADGE)
    pad_x, pad_y = 10 * S, 3 * S
    bx0 = x + tw + 10 * S
    by0 = y - 1 * S
    rounded(
        d,
        (bx0, by0, bx0 + bw + 2 * pad_x, by0 + 16 * S + 2 * pad_y),
        100 * S,
        (int(accent[0] * 0.22 + bg[0] * 0.78), int(accent[1] * 0.22 + bg[1] * 0.78), int(accent[2] * 0.22 + bg[2] * 0.78)),
    )
    d.text((bx0 + pad_x, by0 + pad_y), badge_text, font=F_BADGE, fill=accent)
    d.text((bx0 + bw + 2 * pad_x + 7 * S, y), last_sync, font=F_SYNC, fill=SYNC)

    y += 17 * S

    # ---- 第二行：余额大字
    d.text((x, y), balance, font=F_BAL, fill=WHITE)
    y += 34 * S

    # ---- 第三行：倒计时 + 休/班标记 + 刷新按钮
    label, number = countdown
    d.text((x, y + 2 * S), label, font=F_CD, fill=SUB)
    lw = d.textlength(label, font=F_CD)
    d.text((x + lw + 4 * S, y), number, font=F_MONO, fill=SUB)

    btn = 30 * S
    bx = (W - PAD_R) * S - btn
    by = y - 5 * S
    rounded(d, (bx, by, bx + btn, by + btn), 12 * S, (255, 255, 255, 31))
    # 刷新图标（圆环箭头，简化绘制）
    cx, cy, rr = bx + btn / 2, by + btn / 2, 7.5 * S
    d.arc((cx - rr, cy - rr, cx + rr, cy + rr), start=40, end=320, fill=WHITE, width=int(1.9 * S))
    d.polygon(
        [(cx + rr * 0.55, cy - rr * 1.15), (cx + rr * 1.25, cy - rr * 0.25), (cx + rr * 0.15, cy - rr * 0.15)],
        fill=WHITE,
    )

    if day_badge:
        dw = d.textlength(day_badge, font=F_DAY)
        d.text((bx - dw - 7 * S, y + 4 * S), day_badge, font=F_DAY, fill=TITLE)

    y += 26 * S

    # ---- 第四行：明细
    d.text((x, y), detail, font=F_DAY, fill=TITLE)

    return img


def build():
    gap = 16
    cap_h = 22
    pad = 24
    canvas_w = W + pad * 2
    row_h = CARD_H + cap_h
    canvas_h = pad + 24 + row_h * 2 + gap + pad

    canvas = Image.new("RGB", (canvas_w * S, canvas_h * S), BG)
    d = ImageDraw.Draw(canvas)

    d.text((pad * S, 18 * S), "桌面插件「DeepSeek 余额与峰谷」· 4x2 · 配色随峰谷自动切换",
           font=F_HINT, fill=(124, 144, 168))

    cards = [
        (
            True,
            "¥109.29",
            ("距高峰", "11:42:07"),
            "国庆节 休",
            "刚刚",
            "赠金 ¥9.29 · 充值 ¥100.00",
            "空闲时段（谷）：青绿配色，单价为高峰的 5 折，倒计时指向下一个高峰",
            OFF_PEAK,
        ),
        (
            False,
            "¥109.29",
            ("距谷时", "00:47:12"),
            "春节后补班",
            "3秒前",
            "赠金 ¥9.29 · 充值 ¥100.00",
            "高峰时段（峰）：琥珀配色，工作日 9:00-12:00 / 14:00-18:00",
            PEAK,
        ),
    ]

    top = pad + 20
    for off_peak, balance, countdown, day, sync, detail, caption, accent in cards:
        card = widget_card(off_peak, balance, countdown, day, sync, detail)
        canvas.paste(card, (pad * S, top * S), card)

        cap_y = (top + CARD_H + 6) * S
        # 标题部分用强调色，其余灰色
        head = caption.split("：")[0] + "："
        d.text((pad * S + 2 * S, cap_y), head, font=F_CAP, fill=accent)
        hw = d.textlength(head, font=F_CAP)
        d.text((pad * S + 2 * S + hw, cap_y), caption.split("：", 1)[1], font=F_CAP, fill=(124, 144, 168))

        top += row_h + gap

    # 直接输出 2 倍图（S=2 已按超采样绘制），清晰度足够放进 README
    out = canvas
    out.save("widget-preview.png", "PNG")
    print(f"已生成 widget-preview.png  {out.size[0]}x{out.size[1]}")


if __name__ == "__main__":
    build()
