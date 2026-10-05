"""独立验证 PeakScheduler 的判定逻辑。

思路：用 Python 精确复刻 Kotlin 里的算法与节假日数据，做两件事：
1. 全年逐分钟扫描：检查切换时刻只出现在合法边界、档位与官方规则一致、倒计时恒为正；
2. 复算单元测试里的每一条期望值，确认 Kotlin 测试自身没有写错。

用法：python verify_peak_logic.py
"""
import json
import datetime as dt
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOLIDAYS = json.loads((ROOT / "app/src/main/assets/holidays.json").read_text(encoding="utf-8"))
CAL = {k: v for k, v in HOLIDAYS.items() if not k.startswith("_")}

PEAK = [(dt.time(9, 0), dt.time(12, 0)), (dt.time(14, 0), dt.time(18, 0))]
LEGAL = {t for pair in PEAK for t in pair}  # 合法切换点


def is_workday(d: dt.date) -> bool:
    key = d.isoformat()
    if key in CAL:
        return not CAL[key]["h"]
    return d.weekday() < 5


def is_peak(t: dt.datetime) -> bool:
    if not is_workday(t.date()):
        return False
    return any(a <= t.time() <= b for a, b in PEAK)


def next_workday_after(d: dt.date) -> dt.date:
    cur = d + dt.timedelta(days=1)
    for _ in range(400):
        if is_workday(cur):
            return cur
        cur += dt.timedelta(days=1)
    raise AssertionError("no workday found")


def next_transition(t: dt.datetime):
    d, tm = t.date(), t.time()
    if not is_workday(d):
        return dt.datetime.combine(next_workday_after(d), PEAK[0][0]), True
    if tm < PEAK[0][0]:
        return dt.datetime.combine(d, PEAK[0][0]), True
    if tm < PEAK[0][1]:
        return dt.datetime.combine(d, PEAK[0][1]), False
    if tm < PEAK[1][0]:
        return dt.datetime.combine(d, PEAK[1][0]), True
    if tm < PEAK[1][1]:
        return dt.datetime.combine(next_workday_after(d), PEAK[0][0]), True
    return dt.datetime.combine(next_workday_after(d), PEAK[0][0]), True


def fmt_countdown(ms: int) -> str:
    total = max(0, ms // 1000)
    d, rem = divmod(total, 86400)
    h, rem = divmod(rem, 3600)
    m, s = divmod(rem, 60)
    if d > 0:
        return f"{d}天{h:02d}小时{m:02d}分"
    if h > 0:
        return f"{h:02d}:{m:02d}:{s:02d}"
    return f"{m:02d}:{s:02d}"


failures = []


def check(cond, msg):
    if not cond:
        failures.append(msg)


# ---------------------------------------------------------------- 1) 全年扫描
start = dt.datetime(2026, 1, 1, 0, 0)
end = dt.datetime(2027, 1, 1, 0, 0)
t = start
scanned = 0
switches = 0
max_gap = dt.timedelta(0)
while t < end:
    nb, next_peak = next_transition(t)
    check(nb > t, f"{t} 切换时刻必须严格在未来，得到 {nb}")
    check(is_peak(nb + dt.timedelta(seconds=1)) == next_peak,
          f"{t} 切换后档位不一致：期望 peak={next_peak}")
    check(is_peak(nb - dt.timedelta(seconds=1)) != next_peak,
          f"{t} 切换前档位应与切换后相反")
    # 切换时刻只可能是 9:00 / 12:00 / 14:00 / 18:00
    check(nb.time() in LEGAL, f"{t} 切换时刻 {nb.time()} 不在合法边界集合内")

    gap = nb - t
    max_gap = max(max_gap, gap)
    if t.time() in LEGAL and t.minute == 0:
        switches += 1

    scanned += 1
    t += dt.timedelta(minutes=1)

check(max_gap <= dt.timedelta(days=10), f"最长倒计时 {max_gap} 超过 10 天，可能漏算工作日")
print(f"[1] 全年逐分钟扫描 {scanned} 个时刻，合法边界 {switches} 个，最长倒计时 {max_gap}")

# ---------------------------------------------------------------- 2) 复算单测期望
def at(date: str, time: str) -> dt.datetime:
    return dt.datetime.fromisoformat(f"{date}T{time}")


CASES = [
    # (描述, 时刻, 期望是否高峰)
    ("法定节假日 10-05 10:00", at("2026-10-05", "10:00"), False),
    ("工作日 08:59", at("2026-10-12", "08:59"), False),
    ("工作日 09:00", at("2026-10-12", "09:00"), True),
    ("工作日 11:59", at("2026-10-12", "11:59"), True),
    ("工作日 12:00", at("2026-10-12", "12:00"), True),
    ("工作日 12:01", at("2026-10-12", "12:01"), False),
    ("工作日 13:59", at("2026-10-12", "13:59"), False),
    ("工作日 14:00", at("2026-10-12", "14:00"), True),
    ("工作日 18:00", at("2026-10-12", "18:00"), True),
    ("工作日 18:01", at("2026-10-12", "18:01"), False),
    ("工作日 03:00", at("2026-10-12", "03:00"), False),
    ("普通周六 10-17", at("2026-10-17", "10:00"), False),
    ("普通周日 10-18", at("2026-10-18", "15:00"), False),
    ("补班周六 10-10 10:00", at("2026-10-10", "10:00"), True),
    ("补班周六 10-10 13:00", at("2026-10-10", "13:00"), False),
]
for desc, moment, expected in CASES:
    actual = is_peak(moment)
    check(actual == expected, f"档位判定不符：{desc} 期望 {expected} 实际 {actual}")

BOUNDARY_CASES = [
    ("工作日早八点", at("2026-10-12", "08:00"), at("2026-10-12", "09:00"), True),
    ("上午高峰中", at("2026-10-12", "10:00"), at("2026-10-12", "12:00"), False),
    ("午间谷中", at("2026-10-12", "12:30"), at("2026-10-12", "14:00"), True),
    ("下午高峰中", at("2026-10-12", "16:00"), at("2026-10-13", "09:00"), True),
    ("周五晚", at("2026-10-16", "18:30"), at("2026-10-19", "09:00"), True),
    # 注意：这里用的是内置完整日历，2026-10-08/09 是正常工作日，
    # 只有「仅注入 10-01~10-07 + 10-10」的精简测试日历才会跳到 10-12。
    ("国庆假期中（完整日历）", at("2026-10-05", "10:00"), at("2026-10-08", "09:00"), True),
]

for desc, moment, exp_time, exp_peak in BOUNDARY_CASES:
    nb, np_ = next_transition(moment)
    check(nb == exp_time, f"切换时刻不符：{desc} 期望 {exp_time} 实际 {nb}")
    check(np_ == exp_peak, f"切换后档位不符：{desc} 期望 {exp_peak} 实际 {np_}")

FORMAT_CASES = [
    (5_000, "00:05"),
    (90_000, "01:30"),
    (3_600_000, "01:00:00"),
    ((3 * 3600 + 25 * 60 + 45) * 1000, "03:25:45"),
    (90_061_000, "1天01小时01分"),
    (0, "00:00"),
    (-500, "00:00"),
]
for ms, expected in FORMAT_CASES:
    actual = fmt_countdown(ms)
    check(actual == expected, f"倒计时格式不符：{ms}ms 期望 {expected} 实际 {actual}")

# 节假日日历数据自检
for k, v in CAL.items():
    d = dt.date.fromisoformat(k)
    if v["h"]:  # 放假日不应落在工作日（否则数据冗余但不致命）
        pass
    else:       # 补班日通常是周末
        if d.weekday() < 5:
            failures.append(f"补班日 {k} 落在周一至周五，请核对数据")
print(f"[2] 复算 {len(CASES)} 条档位判定、{len(BOUNDARY_CASES)} 条切换期望、{len(FORMAT_CASES)} 条格式期望，"
      f"节假日日历 {len(CAL)} 条")

if failures:
    print(f"\n❌ 发现 {len(failures)} 个问题：")
    for f in failures[:40]:
        print("  -", f)
    raise SystemExit(1)
print("\n✅ 全部通过：判定逻辑、切换边界、倒计时格式、节假日数据一致")
