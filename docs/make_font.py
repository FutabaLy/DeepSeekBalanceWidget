"""从可变字体生成 App 里打包用的 Nunito Black（静态 + 子集化）。

用法：python docs/make_font.py <可变的 Nunito[wght].ttf 路径>
输出：app/src/main/res/font/nunito_black.ttf

为什么要这一步：
1. 官方仓库只有可变字体，它的默认实例是 **ExtraLight** —— 直接打包会渲染成极细的字，
   所以必须先把 wght 轴固定到 900（Black）；
2. 顺便子集化：中文本来就不在这个字体里（会回落到系统字体），
   只留拉丁字母、数字、货币符号与常用标点，体积从 270KB 降到几十 KB。

依赖：pip install fonttools
"""
import sys
import tempfile
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "font" / "nunito_black.ttf"

# 拉丁 + 拉丁补充 + 常用符号；U+00A5 是 ¥，U+FFE5 是全角 ￥，两个都留
UNICODES = "U+0020-007E,U+00A0-00FF,U+20AC,U+FFE5,U+2018-201D,U+2026,U+00B7,U+2212"


def main(src: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        static = Path(tmp) / "Nunito-Black.ttf"

        font = TTFont(src)
        instancer.instantiateVariableFont(font, {"wght": 900}, inplace=True)
        # instancer 只改轮廓与 OS/2 权重，name 表还留着 ExtraLight，这里一并改掉，
        # 免得以后看字体信息以为是细体
        for name_id, value in (
            (1, "Nunito Black"),
            (2, "Black"),
            (4, "Nunito Black"),
            (6, "Nunito-Black"),
            (16, "Nunito Black"),
            (17, "Black"),
        ):
            font["name"].setName(value, name_id, 3, 1, 0x409)
            font["name"].setName(value, name_id, 1, 0, 0)
        font.save(static)
        print(f"已固定 wght=900：{static.stat().st_size / 1024:.1f} KB")

        OUT.parent.mkdir(parents=True, exist_ok=True)
        subset.main([
            str(static),
            f"--unicodes={UNICODES}",
            "--layout-features=*",
            "--name-IDs=*",
            f"--output-file={OUT}",
        ])

    check = TTFont(OUT)
    cmap = check.getBestCmap()
    print(
        f"已生成 {OUT.relative_to(ROOT)}  {OUT.stat().st_size / 1024:.1f} KB  "
        f"usWeightClass={check['OS/2'].usWeightClass}  name={check['name'].getDebugName(4)}"
    )
    print(
        "  含 U+00A5(¥)：{}   含 0-9：{}".format(
            0x00A5 in cmap and 0xFFE5 in cmap,
            all(ord(d) in cmap for d in "0123456789"),
        )
    )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        raise SystemExit(1)
    main(sys.argv[1])
