"""生成「余额字体候选」对比图，方便挑一款打进 APK。

用法：python docs/font_candidates.py
输出：docs/font-candidates.png

样式与插件一致：浅色卡片 + 左深蓝→右天蓝的渐变字。
候选字体均为可自由分发的开源字体（SIL OFL / Apache），可随 APK 一起打包。
"""
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / ".font-cache"
OUT = ROOT / "font-candidates.png"

GRADIENT_START = (0x1B, 0x3A, 0x8C)
GRADIENT_END = (0x5A, 0xA8, 0xFF)
CARD_BG = (0xF1, 0xF5, 0xFC)
TEXT = "¥ 26.85"
TITLE = "DeepSeek 余额"
USED = "今日已用 ¥ 1.44"
CN_FONT = r"C:\Windows\Fonts\msyh.ttc"

GOOGLE = "https://raw.githubusercontent.com/google/fonts/main/ofl/"

CANDIDATES = [
    # 本机字体，只作为「参考图大概是什么样」的对照
    {"label": "微软雅黑 Bold（参考图很可能就是它）", "local": r"C:\Windows\Fonts\msyhbd.ttc"},
    {"label": "Arial Black（对照）", "local": r"C:\Windows\Fonts\ariblk.ttf"},
    # 下面这些是开源字体，挑中就直接打进 APK
    {"label": "Nunito Black", "file": "Nunito.ttf",
     "url": GOOGLE + "nunito/Nunito%5Bwght%5D.ttf", "axes": [900]},
    {"label": "Baloo 2 ExtraBold", "file": "Baloo2.ttf",
     "url": GOOGLE + "baloo2/Baloo2%5Bwght%5D.ttf", "axes": [800]},
    {"label": "Rubik Black", "file": "Rubik.ttf",
     "url": GOOGLE + "rubik/Rubik%5Bwght%5D.ttf", "axes": [900]},
    {"label": "Manrope ExtraBold", "file": "Manrope.ttf",
     "url": GOOGLE + "manrope/Manrope%5Bwght%5D.ttf", "axes": [800]},
    {"label": "Archivo Black", "file": "ArchivoBlack.ttf",
     "url": GOOGLE + "archivoblack/ArchivoBlack-Regular.ttf", "axes": None},
]


def fetch(name: str, url: str) -> Path | None:
    CACHE.mkdir(exist_ok=True)
    target = CACHE / name
    if target.exists() and target.stat().st_size > 1000:
        return target
    try:
        with urllib.request.urlopen(url, timeout=40) as r:
            target.write_bytes(r.read())
        print(f"  已下载 {name} ({target.stat().st_size // 1024} KB)")
        return target
    except Exception as e:  # noqa: BLE001
        print(f"  下载失败 {name}: {e}")
        return None


def load(path, size: int, axes=None):
    try:
        font = ImageFont.truetype(str(path), size)
    except Exception as e:  # noqa: BLE001
        print(f"  字体打不开 {path}: {e}")
        return None
    if axes:
        try:
            font.set_variation_by_axes(axes)
        except Exception as e:  # noqa: BLE001
            print(f"  变量轴设置失败 {axes}: {e}")
    return font


def gradient_text(img: Image.Image, xy, text, font, start, end) -> None:
    """横向渐变文字：先用文字做蒙版，再按蒙版贴上渐变。"""
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).text(xy, text, font=font, fill=255)
    bbox = mask.getbbox()
    if not bbox:
        return
    grad = Image.new("RGB", img.size)
    gd = ImageDraw.Draw(grad)
    x0, x1 = bbox[0], bbox[2]
    for x in range(x0, x1):
        t = (x - x0) / max(1, x1 - x0)
        gd.line(
            [(x, 0), (x, img.size[1])],
            fill=tuple(round(start[i] + (end[i] - start[i]) * t) for i in range(3)),
        )
    img.paste(grad, (0, 0), mask)


def main() -> None:
    pad, row_h, card_w, card_h = 22, 148, 430, 112
    rows = []
    for c in CANDIDATES:
        if "local" in c:
            path = Path(c["local"])
            if not path.exists():
                print(f"跳过 {c['label']}（本机没有这个字体）")
                continue
        else:
            path = fetch(c["file"], c["url"])
            if path is None:
                print(f"跳过 {c['label']}")
                continue
        rows.append((c["label"], path, c.get("axes")))

    print(f"共 {len(rows)} 款参与对比")
    canvas = Image.new("RGB", (card_w + pad * 2, pad + len(rows) * row_h + pad), (11, 20, 32))
    d = ImageDraw.Draw(canvas)
    label_font = load(CN_FONT, 15)
    used_font = load(CN_FONT, 13)
    title_font = load(CN_FONT, 16)

    y = pad
    for label, path, axes in rows:
        d.text((pad, y), label, font=label_font, fill=(143, 166, 192))
        card = Image.new("RGB", (card_w, card_h), CARD_BG)
        cd = ImageDraw.Draw(card)
        bal_font = load(path, 48, axes)
        if bal_font is None:
            y += row_h
            continue
        cd.text((16, 6), TITLE, font=title_font, fill=(0x3A, 0x55, 0xA8))
        gradient_text(card, (16, 26), TEXT, bal_font, GRADIENT_START, GRADIENT_END)
        cd.text((16, 86), USED, font=used_font, fill=(0x8A, 0x93, 0xA6))
        canvas.paste(card, (pad, y + 22))
        y += row_h

    canvas.save(OUT)
    print(f"已生成 {OUT}")


if __name__ == "__main__":
    main()
