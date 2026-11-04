"""LC 数值 oracle —— 读数公式与圈层参数的唯一参照实现。

依据（只读，不改）：
- app/MECHANICS.md：`CompassReadTypeEnum.getFaceText/getBackText` 的反编译结论
  index = floor(((heading - offsetDegrees + 360) % 360) / intervalDegrees)
  back  = getFaceText((heading + 180) % 360)
  以及 14 个圈层的 (intervalDegrees, offsetDegrees) 表（MECHANICS.md:88-103）。
- app/mechanics.json：19 项机读数值（24 山 15°、三盘 0/-7.5/+7.5、120 分金 3°、
  72 龙 5°、60 龙 6°、64 卦 5.625°、八卦 45°、正兼向 9°/3°、5 盘面）。
- app/MECHANICS.md：24 山顺序、五行、阴阳、先后天八卦方位、六十甲子与纳音。

本模块只做数学与查表，不读被测源码。被测实现有两条验证路径：
- Java：test_ends_parity.py 用 javac 直接编译 `CompassData.java` 并执行（真实代码）；
- Rust：同一文件从 `app/src/main.rs` 的表达式文本解析并求值（源码驱动镜像）。
"""
from __future__ import annotations

import json
import math
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
MECHANICS_JSON = ROOT / "app" / "mechanics.json"
MAIN_RS = ROOT / "app" / "src" / "main.rs"
COMPASS_DATA_JAVA = (
    ROOT / "app" / "android" / "app" / "src" / "main" / "java" / "com" / "luopan"
    / "fengshui" / "CompassData.java"
)
MAIN_ACTIVITY_JAVA = (
    ROOT / "app" / "android" / "app" / "src" / "main" / "java" / "com" / "luopan"
    / "fengshui" / "MainActivity.java"
)

DEG = 360.0
MOUNTAIN_INTERVAL = 15.0
MOUNTAIN_HALF = 7.5

# 24 山，从正北 子 起顺时针；= mechanics.json "24 mountains order"。
MOUNTAINS = list("子癸丑艮寅甲卯乙辰巽巳丙午丁未坤申庚酉辛戌乾亥壬")
# DEX `getTextList()` 的真实内部顺序（子 在末尾）：MECHANICS.md:217-219。
DEX_MOUNTAIN_ORDER = list("癸丑艮寅甲卯乙辰巽巳丙午丁未坤申庚酉辛戌乾亥壬子")

WUXING = list("水水土土木木木木土木火火火火土土金金金金土金水水")
YINYANG = list("阴阴阴阳阳阳阴阴阴阳阳阳阴阴阴阳阳阳阴阴阴阳阳阳")
HOUTIAN = list("坎坎艮艮艮震震震巽巽巽离离离坤坤坤兑兑兑乾乾乾坎")
XIANTIAN = list("坤坤震震震离离离兑兑兑乾乾乾巽巽巽坎坎坎艮艮艮坤")

JIAZI = [    "甲子", "乙丑", "丙寅", "丁卯", "戊辰", "己巳", "庚午", "辛未", "壬申", "癸酉",
    "甲戌", "乙亥", "丙子", "丁丑", "戊寅", "己卯", "庚辰", "辛巳", "壬午", "癸未",
    "甲申", "乙酉", "丙戌", "丁亥", "戊子", "己丑", "庚寅", "辛卯", "壬辰", "癸巳",
    "甲午", "乙未", "丙申", "丁酉", "戊戌", "己亥", "庚子", "辛丑", "壬寅", "癸卯",
    "甲辰", "乙巳", "丙午", "丁未", "戊申", "己酉", "庚戌", "辛亥", "壬子", "癸丑",
    "甲寅", "乙卯", "丙辰", "丁巳", "戊午", "己未", "庚申", "辛酉", "壬戌", "癸亥",
]
NAYIN = [
    "海中金", "海中金", "炉中火", "炉中火", "大林木", "大林木", "路旁土", "路旁土", "剑锋金", "剑锋金",
    "山头火", "山头火", "涧下水", "涧下水", "城头土", "城头土", "白蜡金", "白蜡金", "杨柳木", "杨柳木",
    "泉中水", "泉中水", "屋上土", "屋上土", "霹雳火", "霹雳火", "松柏木", "松柏木", "长流水", "长流水",
    "沙中金", "沙中金", "山下火", "山下火", "平地木", "平地木", "壁上土", "壁上土", "金箔金", "金箔金",
    "佛灯火", "佛灯火", "天河水", "天河水", "大驿土", "大驿土", "钗钏金", "钗钏金", "桑柘木", "桑柘木",
    "大溪水", "大溪水", "沙中土", "沙中土", "天上火", "天上火", "石榴木", "石榴木", "大海水", "大海水",
]

# 十二地支 / 阳干 / 阴干（与两端 BRANCHES、YANG_STEMS、YIN_STEMS 同表）。
BRANCHES = list("子丑寅卯辰巳午未申酉戌亥")
YANG_STEMS = list("甲丙戊庚壬")
YIN_STEMS = list("乙丁己辛癸")

# 三盘：盘面相对地盘的旋转角（知识库口径：人盘逆时针 -7.5°，天盘顺时针 +7.5°）。
PLATES = {"earth": 0.0, "human": -7.5, "heaven": 7.5}
PLATE_LABELS = {"earth": "地盘正针", "human": "人盘中针", "heaven": "天盘缝针"}

# 14 圈层：MECHANICS.md:88-103（intervalDegrees, offsetDegrees，均来自 DEX）。
RINGS = [
    {"type": 1, "name": "先天八卦_卦位", "interval": 45.0, "offset": 22.5},
    {"type": 2, "name": "后天八卦_卦位", "interval": 45.0, "offset": 22.5},
    {"type": 3, "name": "先天八卦洛书数", "interval": 45.0, "offset": 22.5},
    {"type": 4, "name": "地盘正针二十四山", "interval": 15.0, "offset": 7.5},
    {"type": 5, "name": "穿山七十二龙", "interval": 5.0, "offset": 2.5},
    {"type": 6, "name": "地盘正针百二十分金", "interval": 3.0, "offset": 1.5},
    {"type": 7, "name": "透地平分六十龙", "interval": 6.0, "offset": 1.5},
    {"type": 8, "name": "人盘中针二十四山", "interval": 15.0, "offset": 0.0},
    {"type": 9, "name": "天盘缝针二十四山", "interval": 15.0, "offset": 0.0},
    {"type": 10, "name": "天盘缝针百二十分金", "interval": 3.0, "offset": 0.0},
    {"type": 11, "name": "先天方图六十四卦内卦", "interval": 5.625, "offset": 0.0},
    {"type": 12, "name": "先天圆图六十四卦外卦", "interval": 5.625, "offset": 0.0},
    {"type": 13, "name": "六十甲配六十四卦五行", "interval": 5.625, "offset": 0.0},
    {"type": 14, "name": "正兼向度数指标", "interval": 3.0, "offset": 0.0},
]
RING_BY_TYPE = {r["type"]: r for r in RINGS}

# 八卦：45° 一格，边界在 22.5 + 45k。方位取自 MECHANICS.md:202-205。
# index 0 覆盖 [22.5, 67.5) → 中心 45°。
HOUTIAN_CLOCKWISE = ["艮", "震", "巽", "离", "坤", "兑", "乾", "坎"]
XIANTIAN_CLOCKWISE = ["震", "离", "兑", "乾", "巽", "坎", "艮", "坤"]

# 五行 / 八卦五行（MECHANICS.md:193-195, 205）。
GUA_WUXING = {"乾": "金", "兑": "金", "坤": "土", "艮": "土",
              "震": "木", "巽": "木", "坎": "水", "离": "火"}

# 走查用的航向语料（含 0°/边界/跨盘/负数/多圈）。
CORPUS = [
    0.0, 0.001, 3.0, 4.499, 4.5, 4.501, 5.624, 5.625, 7.499, 7.5, 7.501,
    10.0, 14.999, 15.0, 15.001, 22.499, 22.5, 22.501, 30.0, 45.0, 90.0,
    135.0, 180.0, 225.0, 270.0, 315.0, 330.0, 345.0, 352.499, 352.5,
    359.99, 359.999, 360.0, 360.5, 720.5, 1080.25, -0.01, -7.5, -15.0, -359.99,
]

# 编码前自检：表长与 mechanics.json 对齐，防止手误。
assert len(MOUNTAINS) == 24 and len(set(MOUNTAINS)) == 24
assert sorted(MOUNTAINS) == sorted(DEX_MOUNTAIN_ORDER)
assert len(WUXING) == 24 and len(YINYANG) == 24
assert len(HOUTIAN) == 24 and len(XIANTIAN) == 24
assert len(JIAZI) == 60 and len(NAYIN) == 60


def normalize(d: float) -> float:
    """与 CompassToolsKt.B1(D)D 等价：v %= 360; if v < 0: v += 360。"""
    x = math.fmod(d, DEG)
    if x < 0:
        x += DEG
    return x


def ring_count(ring: dict) -> int:
    count = DEG / ring["interval"]
    assert abs(count - round(count)) < 1e-9, ring
    return int(round(count))


def ring_center(ring: dict, k: int) -> float:
    """第 k 格中心角：offset + interval * (k + 0.5)。"""
    return ring["offset"] + ring["interval"] * (k + 0.5)


def ring_lower(ring: dict, k: int) -> float:
    """第 k 格下边界角：offset + interval * k。"""
    return ring["offset"] + ring["interval"] * k


def dex_index(heading: float, offset: float, interval: float) -> int:
    """DEX getFaceText 的 index 计算：floor(((h - off + 360) % 360) / interval)。"""
    return int(math.floor(((heading - offset + DEG) % DEG) / interval))


def dex_text(heading: float, ring: dict, texts: list) -> str:
    return texts[dex_index(heading, ring["offset"], ring["interval"])]


def dex_back_text(heading: float, ring: dict, texts: list) -> str:
    """坐 = getFaceText((向 + 180) % 360)。"""
    return dex_text((heading + 180.0) % DEG, ring, texts)


def plate_index(heading: float, plate: str) -> int:
    """盘面读数：标签 m 的中心在 m*15 + offset，格界在 offset + 7.5 + 15k。

    m = floor((h - offset + 7.5) / 15) mod 24
    与「标签 m 画在 m*15 + offset」的绘制口径严格一致。
    """
    return int(math.floor(normalize(heading - PLATES[plate] + MOUNTAIN_HALF) / MOUNTAIN_INTERVAL)) % 24


def plate_name(heading: float, plate: str) -> str:
    return MOUNTAINS[plate_index(heading, plate)]


def earth_reading(heading: float) -> dict:
    """地盘读数（供两端对拍）：mountain / offset / fenjin_slot / dragon72_slot /
    dragon60 / sitting / jian。"""
    h = normalize(heading)
    m = plate_index(h, "earth")
    center = m * MOUNTAIN_INTERVAL
    offset = normalize(h - center + 180.0) - 180.0
    from_start = normalize(h - (center - MOUNTAIN_HALF))
    fenjin_slot = min(int(math.floor(from_start / 3.0)), 4)
    dragon72_slot = min(int(math.floor(from_start / 5.0)), 2)
    dragon60 = int(math.floor(((h - 1.5) % DEG) / 6.0)) % 60
    sitting = (m + 12) % 24
    jian = jian_label(offset, m)
    return {
        "h": h, "m": m, "off": offset, "fs": fenjin_slot, "d72": dragon72_slot,
        "d60": dragon60, "sit": sitting, "jian": jian,
        "fenjin": wangxiang(borrow_branch(m), fenjin_slot),
        "d72name": wangxiang(borrow_branch(m), dragon72_slot),
        "d60name": JIAZI[dragon60],
    }


def jian_label(offset: float, mountain: int) -> str:
    """正兼向：正向 = 中央 9°（|offset| <= 4.5），其余为兼向邻山（MECHANICS.md:184-188）。"""
    if abs(offset) * 2.0 <= 9.0:
        return "正向"
    n = (mountain - 1) % 24 if offset < 0 else (mountain + 1) % 24
    return "兼向" + MOUNTAINS[n]


def borrow_branch(m: int) -> int:
    """分金/龙命名用「寄支」（三合盘惯例，与两端 borrowBranch 同表）。"""
    name = MOUNTAINS[m]
    table = {"子": 0, "癸": 0, "丑": 1, "艮": 1, "寅": 2, "甲": 2, "卯": 3,
             "辰": 4, "乙": 4, "巽": 4, "巳": 5, "丙": 5, "午": 6, "未": 7,
             "丁": 7, "坤": 7, "申": 8, "庚": 8, "酉": 9, "戌": 10, "辛": 10,
             "乾": 10, "亥": 11, "壬": 11}
    return table[name]


def wangxiang(branch: int, slot: int) -> str:
    stock = YANG_STEMS if branch % 2 == 0 else YIN_STEMS
    target = stock[slot % 5] + BRANCHES[branch]
    return target


def bagua_index(heading: float) -> int:
    return dex_index(heading, 22.5, 45.0)


def bagua_name(heading: float, mode: str = "houtian") -> str:
    order = HOUTIAN_CLOCKWISE if mode == "houtian" else XIANTIAN_CLOCKWISE
    return order[bagua_index(heading)]


def dragon60_center(k: int) -> float:
    """60 龙第 k 格中心：offset 1.5 + 3 + 6k。"""
    return 1.5 + 3.0 + 6.0 * k


def load_mechanics_doc() -> dict:
    with open(MECHANICS_JSON, encoding="utf-8") as f:
        return json.load(f)


def load_mechanics() -> list:
    return load_mechanics_doc()["values"]
