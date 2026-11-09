import sys
from collections import Counter

from PIL import Image

# 用法: palette_check.py <ref.png> <act.png>
# 判据（第 4 条色值三点，Δ≤3/255）：
#   BG   = 出现最多的底色
#   CARD = 出现第二多且足够亮的卡片色
#   TEXT = 前 12 色里最暗、且占比≥0.1% 的主字色
def palette(path):
    im = Image.open(path).convert('RGB')
    im = im.resize((im.width // 4, im.height // 4))
    cnt = Counter(im.getdata())
    total = sum(cnt.values())
    return [(rgb, c / total) for rgb, c in cnt.most_common(12)]

def pick(pal):
    bg = pal[0][0]
    card = None
    for rgb, s in pal[1:]:
        if min(rgb) >= 200:
            card = rgb
            break
    text = None
    for rgb, s in pal:
        if s >= 0.001 and max(rgb) <= 120:
            text = rgb
            break
    return bg, card, text

rb, rc, rt = pick(palette(sys.argv[1]))
ab, ac, at = pick(palette(sys.argv[2]))
d = lambda x, y: None if (x is None or y is None) else max(abs(x[i] - y[i]) for i in range(3))
print(f"REF bg=#{rb[0]:02X}{rb[1]:02X}{rb[2]:02X} card={rc} text={rt}")
print(f"ACT bg=#{ab[0]:02X}{ab[1]:02X}{ab[2]:02X} card={ac} text={at}")
print(f"DELTA bg={d(rb, ab)} card={d(rc, ac)} text={d(rt, at)}")
ok = all(x is not None and x <= 3 for x in (d(rb, ab), d(rc, ac), d(rt, at)))
print("COLOR_VERDICT=" + ("PASS" if ok else "FAIL"))
