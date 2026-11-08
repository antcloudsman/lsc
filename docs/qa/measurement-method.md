# 测量方法笔记（尺寸 / bbox / 颜色 / 连通域）

> 维护：LC审成文　｜　建立：2026-11-08（技术总监裁定「这两条比定位点数值本身更值钱」）
> 适用：一切「按像素量尺寸/位置/颜色」的判据。**写判据的人与实现的人必须用同一段代码口径**，否则数值不可比。
> 配套：`docs/TEAM_CHARTER.md` §10 T-05/T-06 是纪律条款，本文是展开与复现方法。

## M1 量形状尺寸时，用「形状自己的掩膜」，不要用整体 bbox

**事故两次，同一个错**：

| 轮次 | 错误数值 | 错在哪 |
|---|---|---|
| M6 | 蓝填充 `47×35` | 把定位三角算进了椭圆主体 |
| M7 | 定位点 `45×34` | 又一次用「含三角的并集 bbox」当椭圆尺寸 |

真相：原版椭圆主体 **44×22**、三角 **13×7**、两域分离。整体外框是「所有东西的外框」，不是「椭圆多大」。
正确做法：**按颜色/类别分别取连通域，各自量 bbox**；跨类别合并只在需要「整体占位」时用，且必须写明是并集。

## M2 画法会改变判据结果：描边内/外必须写进判据

`Stroke` 以路径为中心绘制时，会盖掉内测约半个笔宽的主色：

```
描边居中的实现：主色 bbox 宽 44 → 41（判据 44±2 直接被判 FAIL，但几何本体是对的）
描边外扩半个笔宽：主色 bbox 回到 44 ✓
```

⇒ 凡「按精确主色 bbox 判尺寸」的条目，判据里必须同时规定：
1. 颜色与容差（例：`#0077FF`，三通道 ±2）；
2. 描边是**外扩半个笔宽**还是内缩（不写则不可比）；
3. 抗锯齿是否允许吃掉 1px。

## M3 颜色容差不能取 0

原版定位三角在 `tol=0` 时只剩 <20 px，连通域会退化成 1 个，从而得出「原版也只有一块」的错误结论。
**固定口径（M7 定位点，已写进方案 §1.4l/§1.4m）**：

```
掩膜   精确 #0077FF，三通道容差 ±2
邻域   8 邻域连通域，最小面积 8 px
主体   bbox 44×22（±2）   三角 bbox 13×7（±2）
间隙   ≥2 px（参考 3 px）  连通域数 = 2
```

## M4 复现方式（任何人可重跑）

```python
# 依赖 Pillow；对原版与我方同一张尺寸的截图各跑一次，输出才可比
from PIL import Image
from collections import deque
TARGET=(0x00,0x77,0xFF); TOL=2; MIN_AREA=8

def mask(img):
    px=img.convert("RGB").load(); w,h=img.size
    m=[[False]*w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            r,g,b=px[x,y]
            m[y][x]=abs(r-TARGET[0])<=TOL and abs(g-TARGET[1])<=TOL and abs(b-TARGET[2])<=TOL
    return m

def comps(m):
    h=len(m); w=len(m[0]); seen=[[False]*w for _ in range(h)]; out=[]
    for y0 in range(h):
        for x0 in range(w):
            if not m[y0][x0] or seen[y0][x0]: continue
            q=deque([(x0,y0)]); seen[y0][x0]=True; xs=[]; ys=[]
            while q:
                x,y=q.popleft(); xs.append(x); ys.append(y)
                for dx in (-1,0,1):
                    for dy in (-1,0,1):
                        nx,ny=x+dx,y+dy
                        if 0<=nx<w and 0<=ny<h and m[ny][nx] and not seen[ny][nx]:
                            seen[ny][nx]=True; q.append((nx,ny))
            if len(xs)>=MIN_AREA:
                out.append((len(xs), min(xs),min(ys),max(xs)-min(xs)+1,max(ys)-min(ys)+1))
    return sorted(out, reverse=True)
```

> 判读要点：**先看域数，再看 bbox**。域数不对（1 个 vs 2 个）说明容差/掩膜有问题，此时比 bbox 没有意义。

## M5 出处（尺寸/颜色口径）

| 结论 | 出处 |
|---|---|
| 56 号旧口径 `58–68` / `54×41` 作废 | `docs/fidelity/m7/stage1-acceptance-plan.md` §1.4l/§1.4m（`aa0b80a`） |
| 原版主体 728 px/44×22、三角 46 px/13×7 | LC审核验 M7 实测（同一段代码、同一阈值） |
| 我方修复后两域/44×22/间隙 4 px | `a85dd72`，证据 `reports/M7-pointer-fix/fix-03.png` |
| `tol=0` 漏三角 | LC审核验 2026-11-08 提醒（已进判据） |

## M6 APK 体积必须由「删除旧产物后的重建」测得

**事故**（2026-11-08，几乎误判 C 轮门禁）：

```
app-debug.apk 33,980 KB：中央目录 556 条、压缩合计 13.1 MB、文件却 33.2 MB
local header 892 个 vs 中央目录 556 条 → 336 条孤儿数据 ≈ 21.6 MB
删掉 app-debug.apk 重建：13,790,086 B（13.15 MB），556 条目，压缩合计 13.06 MB ✅
```

**渐进构建不会截断旧产物**：新内容写进去了，旧字节留在文件里。此时看到的体积是「新 + 旧」的混合体，
拿它去判「压缩没达标」就会白改一轮（真值 13.2MB 本来就 PASS）。

**判据（可执行，不靠肉眼）**：

```
文件大小 ≈ 本地头(30B × 条数) + 压缩合计 + 中央目录 + EOCD
① local header 数 == 中央目录条目数
② 未归属字节（文件大小 − 上式合计）≤ 1 MB
两条任一不满足 = 读到脏产物，体积数字作废
```

工具：`tools/device/apk-integrity.py`（新增）

```bash
python tools/device/apk-integrity.py app/android/app/build/outputs/apk/debug/app-debug.apk
# 干净：size 13790086 B、central 556 / eocd 556 / local 556、orphan 0.03MB → VERDICT: CLEAN (exit 0)
# 脏：  local 557 vs central 556 → FAIL orphan local headers: 1 → VERDICT: DIRTY (exit 1)
```

**操作口径**：量包体前 `rm -f app-debug.apk`（或 `./gradlew clean assembleDebug`）；`SMOKE` 报的体积在判门禁前先过一遍本工具。

## M6.1 反证样例（证明门禁不是空转）

| 用例 | 构造 | 结果 |
|---|---|---|
| 干净包 | `develop-int` 重建的 `app-debug.apk` | `CLEAN`，556/556/556，orphan 0.03 MB |
| 注入残留头 | 在数据区中部写入 `PK\x03\x04` | `FAIL orphan local headers: 1 (local 557 vs central 556)`，exit=1 |

## 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-08 | 建 M1–M5：并集 bbox 两次事故、描边内外影响判据、容差不得为 0、可复跑连通域代码 |
| 2026-11-08 | 增 M6/M6.1：APK 体积须删旧产物后重建测得；`apk-integrity.py` 两例反证（干净 556/556/556、注入残留头 557 vs 556 FAIL） |
