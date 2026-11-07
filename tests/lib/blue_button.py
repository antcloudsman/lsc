import sys
import numpy as np
from PIL import Image

# 用法: blue_button.py <png> <x0> <y0> <x1> <y1> [r g b tol]
# 在指定区域内测量「蓝色填充」：先按行找成片命中行（行命中 ≥ 区域宽*0.25），
# 再用这些行的像素高度定列阈值（列命中 ≥ 填充高*0.6），输出填充 bbox。
# 用途：联系我们蓝按钮「可点区 157（dump）/ 蓝填充 111（像素）」里的后者。
path = sys.argv[1]
x0, y0, x1, y1 = (int(v) for v in sys.argv[2:6])
r, g, b = (int(sys.argv[6]), int(sys.argv[7]), int(sys.argv[8])) if len(sys.argv) > 8 else (75, 115, 203)
tol = int(sys.argv[9]) if len(sys.argv) > 9 else 30
im = np.asarray(Image.open(path).convert('RGB'), dtype=int)[y0:y1, x0:x1]
mask = (np.abs(im[:, :, 0] - r) <= tol) & (np.abs(im[:, :, 1] - g) <= tol) & (np.abs(im[:, :, 2] - b) <= tol)
h, w = mask.shape
rows = mask.sum(axis=1)
row_th = max(20, int(w * 0.25))
ry = np.where(rows >= row_th)[0]
if len(ry) == 0:
    print(f"BLUE=none region=[{x0},{y0}]-[{x1},{y1}] row_max={rows.max()} row_th={row_th}")
    sys.exit(0)
fill_h = ry.max() - ry.min() + 1
sub = mask[ry.min():ry.max() + 1]
xs = np.where(sub.any(axis=0))[0]
cx = xs  # 填充宽度取成片行的左右极值，避免文字列断开
if len(cx) == 0:
    print(f"BLUE=none(col) fill_h={fill_h} col_max={cols.max()} col_th={col_th}")
    sys.exit(0)
print(f"BLUE_FILL=[{x0 + cx.min()},{y0 + ry.min()}][{x0 + cx.max()},{y0 + ry.max()}] "
      f"SIZE={cx.max() - cx.min() + 1}x{fill_h} TARGET=#{r:02X}{g:02X}{b:02X} TOL={tol}")
