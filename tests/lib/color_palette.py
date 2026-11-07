import sys
from collections import Counter
from PIL import Image

def top_colors(path, n=8):
    im = Image.open(path).convert('RGB')
    im = im.resize((im.width // 2, im.height // 2))
    c = Counter(im.getdata())
    total = sum(c.values())
    return [(rgb, cnt / total) for rgb, cnt in c.most_common(n)]

ref, act = sys.argv[1], sys.argv[2]
rc, ac = top_colors(ref), top_colors(act)
print(f"REF={ref}")
for rgb, s in rc:
    best, bd = None, 10 ** 9
    for a, _ in ac:
        d = max(abs(rgb[i] - a[i]) for i in range(3))
        if d < bd:
            bd, best = d, a
    print(f"  ref#{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X} share={s*100:.1f}% nearest_act=#{best[0]:02X}{best[1]:02X}{best[2]:02X} dmax={bd}")
print(f"ACT={act}")
for rgb, s in ac[:5]:
    print(f"  act#{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X} share={s*100:.1f}%")
