import re
import sys
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image

# 用法: check_red_button.py <act.xml> <act.png>
# 判据（总监裁定）：
#   几何 = dump 父节点 [39,2512][1189,2578]（1150×66），Δ≤2，按父节点不按文本节点
#   颜色 = 像素红底 #E93B4D（dump 无颜色）
EXPECT = (39, 2512, 1189, 2636)
COLOR = (233, 59, 77)
TOL = 20

xml_path, png_path = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
best = None
for n in tree.getroot().iter('node'):
    b = n.get('bounds') or ''
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
    if not m:
        continue
    box = tuple(int(m.group(i)) for i in range(1, 5))
    d = max(abs(box[i] - EXPECT[i]) for i in range(4))
    if best is None or d < best[0]:
        best = (d, box, n.get('class') or '', n.get('text') or '')
if best is None:
    print("RED_BUTTON=none(xml)")
    sys.exit(1)
d, box, cls, txt = best
print(f"RED_BUTTON_CONTAINER={box[0]},{box[1]},{box[2]},{box[3]} size={box[2]-box[0]}x{box[3]-box[1]} max_delta={d} class={cls} text='{txt}'")

im = np.asarray(Image.open(png_path).convert('RGB'), dtype=int)
x0, y0, x1, y1 = EXPECT
region = im[max(0, y0 - 10):y1, max(0, x0 - 10):x1 + 10]
mask = (np.abs(region[:, :, 0] - COLOR[0]) <= TOL) & (np.abs(region[:, :, 1] - COLOR[1]) <= TOL) & (np.abs(region[:, :, 2] - COLOR[2]) <= TOL)
total = mask.size
cov = mask.sum() / total * 100 if total else 0
if mask.sum() < 1000:
    print(f"RED_FILL=none(box_pixels={mask.sum()} coverage={cov:.1f}%)")
else:
    ys, xs = np.where(mask)
    print(f"RED_BBOX=[{x0-10+xs.min()},{max(0,y0-10)+ys.min()}][{x0-10+xs.max()},{max(0,y0-10)+ys.max()}] SIZE={xs.max()-xs.min()+1}x{ys.max()-ys.min()+1} COVERAGE={cov:.1f}% TARGET=#E93B4D TOL={TOL}")
