"""生成 2×2 插件封面图资源（保留透明背景 + 防黑边 + WebP 压缩）。

用法：
    python docs/make_cover.py <原图路径>

输出：
    app/src/main/res/drawable-nodpi/compact_cover.webp

三个要点：
1. **保留原图的 alpha**。原图已经是抠好的透明底 PNG，透明像素的 RGB 是 (0,0,0)。
   曾经这里用 putalpha(圆角蒙版) 直接覆盖 alpha，把透明像素写成了「不透明黑」，
   桌面上就变成一张黑底图 —— 所以现在只做乘法/原样保留，绝不覆盖。
2. **防黑边**：透明区 RGB 是黑色，缩放插值时黑色会渗到人物边缘形成黑边。
   这里先把 RGB 向透明区做一次扩散填充（alpha 保持原样），边缘就不会发黑。
3. 裁到人物外接框（留一点边距），让角色在卡片里尽量占满。
"""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "compact_cover.webp"

MAX_SIZE = 610     # 输出最长边上限（180dp 卡片 @3x ≈ 540px，留点余量）
PAD_RATIO = 0.015  # 外接框外留的边距比例
ALPHA_CUT = 8      # 判「不透明」的 alpha 阈值
BLUR = 10          # 扩散填充用的模糊半径


def main(src: str) -> None:
    img = Image.open(src).convert("RGBA")

    # 1) 裁到人物外接框
    solid = img.getchannel("A").point(lambda a: 255 if a > ALPHA_CUT else 0)
    box = solid.getbbox()
    if box:
        pad = int(max(img.size) * PAD_RATIO)
        box = (
            max(0, box[0] - pad),
            max(0, box[1] - pad),
            min(img.width, box[2] + pad),
            min(img.height, box[3] + pad),
        )
        img = img.crop(box)

    # 2) 缩放（不放大）
    longest = max(img.size)
    if longest > MAX_SIZE:
        scale = MAX_SIZE / longest
        img = img.resize(
            (max(1, round(img.width * scale)), max(1, round(img.height * scale))),
            Image.LANCZOS,
        )

    # 3) RGB 向透明区扩散，消掉缩放时的黑边；alpha 原样保留
    alpha = img.getchannel("A")
    hard = alpha.point(lambda a: 255 if a > ALPHA_CUT else 0)
    filled = Image.composite(img.convert("RGB"), img.convert("RGB").filter(ImageFilter.GaussianBlur(BLUR)), hard)
    out = filled.convert("RGBA")
    out.putalpha(alpha)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT, "WEBP", quality=92, method=6)

    transparent = sum(alpha.histogram()[:ALPHA_CUT]) / (alpha.width * alpha.height)
    print(
        f"已生成 {OUT.relative_to(ROOT)}  {OUT.stat().st_size / 1024:.1f} KB  "
        f"{out.width}x{out.height}  透明像素占比 {transparent:.1%}"
    )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        raise SystemExit(1)
    main(sys.argv[1])
