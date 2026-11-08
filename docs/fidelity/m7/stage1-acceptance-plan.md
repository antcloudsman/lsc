# M7 · A 轮验收方案（LC审核验）

- 适用：M7 A 轮「罗盘贴地 + 两个符号取证」
- 基线：`origin/develop`（A 轮动工前提交，待批后锁定）
- 设备：单台 `L2E0222531014833`，串行；前置照旧（动画三项 0、唤醒解锁、`rm -f /sdcard/a.xml`、`adb push + pm install -r -t`）
- 状态：**本文件是方案，不是结论**；待技术总监批后才执行

---

## 一、贴地（第 7 条）验收方案

### 1.1 先说清「原版对照」从哪来（这是本轮最容易造假的地方）

`docs/fidelity/orig/` 现有 55 张原版截图中**没有贴地相关命名**（`ground|dipan|ramp|贴地` 命中 0）。所以「与原版 1:1」不能靠现成截图，必须按下列顺序取证，取到哪一级就用到哪一级：

| 级别 | 取证方式 | 判据强度 |
|---|---|---|
| L1 | 装原版 APK 到本机（`E:\APPDetector\input\swss.apk`），进罗盘态点「贴地」截图；同机位再装我方包截同角度 | 强（可像素对照） |
| L2 | 逆向从 DEX 给出「四角投影 + `radius=半边长` + `size=floor(2*screen.width)`」的代码锚点（类/方法/地址），作为**几何判据的出处** | 中（判几何公式，不判像素） |
| L3 | 两者都拿不到 | **降级**：只判「我方自洽」，关账表写「无原版对照」，**不得写 1:1** |

> 若走 L1：原版 APK 100,655,304 B、7 个 dex、`assets/webapp/` 334 个自有文件（本机已解包验证）；能否在本机起图需实测，起不来就退 L2，**不许拿“应该一样”替代**。

### 1.2 双图规范（每组两张，共 4 张）

| 文件 | 状态 |
|---|---|
| `fig-ground-b0-pre.png` | bearing=0，贴地**关闭**（2D） |
| `fig-ground-b0-post.png` | bearing=0，贴地**开启** |
| `fig-ground-b90-pre.png` | bearing=90，贴地关闭 |
| `fig-ground-b90-post.png` | bearing=90，贴地开启 |

- 同一机位：`center`/`zoom` 由 `state` 记录，四张必须一致（`zoom` 相同、`center` 差 ≤1e-6）
- 截图统一 `exec-out screencap -p`，不做缩放、不二次编码

### 1.3 dump 的替代（已知限制，必须写在报告里）

主界面 `uiautomator dump` 长期 `could not get idle state`（地图持续重绘），**贴地态无法 dump 视图树**。替代口径：

```
adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd state
# 取 mode / bearing / groundOn / center / zoom 五个字段
```

三件齐的替代组合 = **抖音式双图 + `state` JSON + 像素量化值**；报告中注明「dump 不可得，以 state JSON 替代」，不写“已 dump”。

### 1.4 量化项与阈值（六项，全部可脚本复跑）

| # | 量 | 方法 | 阈值 |
|---|---|---|---|
| Q1 盘径 | 贴地后盘面外接矩形的宽、高 | 非底色判据（与底色差 > 24/255）扫描整屏 | 宽 = **1228 ± 4 px**（四角投影下盘面铺满视口宽），高与宽比 1.000 ± 0.01 |
| Q2 盘心 | 外接矩形中心 | 同上 | **x=614 ± 2、y=1350 ± 2**（与 M6 已测屏幕中心一致） |
| Q3 透明度 | 取盘面中心纯色区 `D`、无盘面时同点底色 `C0`、贴地后同点 `C1`，`a=(C0−C1)/(C0−D)` | 三个采样点各算一次 | 三次 `a` 互差 ≤ **0.02**；`a` 与代码里 alpha 常量差 ≤ 0.02 |
| Q4 随 bearing 转 | bearing=0 与 90 两图中红像素分布 | `std(x)` 与 `std(y)` 比较 | b0：`std(x) < std(y)`（竖线主导）；b90：`std(x) > std(y)`（变横向）；两图的线均过 (614,1350) ± 2 |
| Q5 不跟 DEM 起伏 | 平地与山地各贴地一次，比外接四边形四顶点 | 两次截图同法取四角 | 四个顶点坐标差 ≤ **2 px**（跟 DEM 会明显变形） |
| Q6 遮罩/可交互 | 贴地态下点右侧「3D」「图层」是否仍可点 | 点击后 `state.mode` 变化 | 变化即算通过（盘面不得吃掉全屏触摸） |

### 1.5 失败与回滚

- 任一项超差 → 本条判**不通过**，退回实现方，附超差项与实测值；
- 实现方需在方案里给**回滚点**（贴地分支开关或代码回退到当前 2D 实现），我验收前先确认回滚点存在，否则不开始。

---

## 二、两个符号（第 3 条）复核方案

### 2.1 复核目标

对逆向的结论（有/无）做独立复核：**命中要能落到锚点，零命中要能证明检索范围完整**。

### 2.2 复核命令（已在本机跑通，均二进制安全，不经过文本管道）

```powershell
# ① 列包内条目（不解包，避免重名文件冲突）
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[System.IO.Compression.ZipFile]::OpenRead('E:\APPDetector\input\swss.apk'); $z.Entries.FullName; $z.Dispose()

# ② 字符串检索（python 直读 zip 内字节，不经文本管道）
python - <<'PY'
import zipfile
z=zipfile.ZipFile(r"E:\APPDetector\input\swss.apk")
cands=[b"userLocation",b"locationMarker",b"geolocate",b"locationDot",b"myLocation",b"MyLocation",b"locationPointer",b"cross.png"]
for n in z.namelist():
    if not n.endswith((".dex",".js",".html",".json",".css")): continue
    d=z.read(n)
    for c in cands:
        k=d.count(c)
        if k: print(n, c.decode(), k)
PY

# ③ 资源图片清单（看有没有“定位点/十字/中心”类图形）
python - <<'PY'
import zipfile
z=zipfile.ZipFile(r"E:\APPDetector\input\swss.apk")
for n in z.namelist():
    if n.startswith(("res/","assets/")) and n.lower().endswith((".png",".webp",".jpg")):
        print(n, z.getinfo(n).file_size)
PY
```

### 2.3 归因规则（不看归因就下结论是这一条最容易错的地方）

命中必须分成三类，**只有第 1 类能作为“原版有该符号”的证据**：

| 类 | 判据 | 处理 |
|---|---|---|
| 1 项目自有代码 | 命中在 `assets/webapp/js/*.bundle.js`（应用打包 chunk，非 `lib/`）、`assets/webapp/*.html`、或 dex 里的应用包名类 | 可作为证据，需给到 bundle 文件 + 偏移 |
| 2 第三方库 | 命中在 `assets/webapp/lib/mapbox-gl.js`、`assets/webapp/css/mapbox-gl.css`、HMS/Firebase/AMap 等 | **不算证据**，只记“库内部实现” |
| 3 资源文件存在但未被主图引用 | 例如图片在 `assets/webapp/assets/imgs/` 下，但主 bundle 无引用 | **不算符号存在**，只能记“素材在包里” |

### 2.4 结论两种形式

- **有** → 锚点三件：bundle 文件 + 字节偏移/上下文片段 + 资源路径（如图片），并说明形状/颜色/尺寸/是否随 bearing 转；
- **无** → 写「**原版不可得**」，附检索命令原文、检索范围（dex 7 个 + `assets/webapp/` 334 个自有文件 + 全部图片资源）、以及零命中的原始输出。

### 2.5 我预跑得到的中间结果（供逆向核对，**不作为我的结论**）

按 2.2 命令实跑（2026-11-08）：

```
主地图 bundle assets/webapp/js/index.946c1624e0.bundle.js：
  geolocate=0  GeolocateControl=0  geolocation=0  userLocation=0
  cross=3（全部是 crossOrigin，不是图片）   pointer=24   center=48

全包（dex+web）：
  userLocation=9 / MyLocation=2 / geolocate=96 —— **全部落在 assets/webapp/lib/mapbox-gl.js(.map) 与 css**
  项目自有 web 代码（排除 lib/）中：这三项 0 命中

资源图片中存在：assets/webapp/assets/imgs/locationPointer.png(1978B)、cross.png(621B)、
  gmba/gmba_location.png、street_view_point.png、compass/pointer.png
引用情况：locationPointer 仅被 Airport/Harbor/Scenic/global-*/special/973 等子图 bundle 引用（各 1 处），
  **主图 index bundle 0 引用**；cross.png 仅 global-population 引用 1 处。
```

→ 按 2.3 归因：**主地图（index bundle）没有当前位置符号、没有屏幕中心符号的引用**；`locationPointer.png`/`cross.png` 属第 3 类（素材在包里、主图不用）。这与 M6 的「web 层零命中」结论一致，但**范围要写清**：零命中是「项目自有代码」，第三方库里有 `geolocate` 字样不算。

请逆向在此基础上给终结论（有锚点 / 无 + 检索范围）。

---

## 三、不进判据的东西

- 「看起来像」「和别的 app 差不多」不判；
- 原版 APK 起不来时的截图（L1 失败）不臆造，退 L2 或 L3；
- 第三方库内部字符串不作证据；
- 贴地若只有 L3，关账表写「无原版对照」，不写 1:1、不写“已修复”。
