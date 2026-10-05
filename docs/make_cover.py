"""生成 2×2 插件封面图资源（圆角 + WebP 压缩）。

用法：
    python docs/make_cover.py <原图路径>

输出：
    app/src/main/res/drawable-nodpi/compact_cover.webp

为什么要把圆角「烧进」图片里：RemoteViews 里没法给 ImageView 做 clipToOutline，
所以圆角必须预先做进图片本身（图片四角透明），卡片贴到桌面才是圆角的。
半径 76px / 610px ≈ 卡片显示成 180dp 宽时的 22dp，和浅色卡片的圆角一致。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "compact_cover.webp"

SIZE = 610        # 输出边长（180dp 卡片 @3x ≈ 540px，留点余量）
RADIUS = 76       # 圆角半径（像素），对应显示时约 22dp
SUPERSAMPLE = 4   # 掩膜超采样倍数，用来做抗锯齿


def main(src: str) -> None:
    img = Image.open(src).convert("RGBA")
    if img.size != (SIZE, SIZE):
        img = img.resize((SIZE, SIZE), Image.LANCZOS)

    mask = Image.new("L", (SIZE * SUPERSAMPLE, SIZE * SUPERSAMPLE), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, SIZE * SUPERSAMPLE - 1, SIZE * SUPERSAMPLE - 1),
        radius=RADIUS * SUPERSAMPLE,
        fill=255,
    )
    mask = mask.resize((SIZE, SIZE), Image.LANCZOS)

    img.putalpha(mask)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, "WEBP", quality=92, method=6)
    print(f"已生成 {OUT.relative_to(ROOT)}  {OUT.stat().st_size / 1024:.1f} KB")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        raise SystemExit(1)
    main(sys.argv[1])
