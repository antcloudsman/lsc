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
| Q1 盘径 | 贴地后盘面**水平弦长**：在 `y = 1350 ± 40` 两行上各取一次左右边缘，取两次均值 | 沿行向两侧扫描，判「连续 ≥25px 不是盘面色」为边界 | **仅在 pitch=0 档判尺寸**：水平弦长 = **927 ± 4 设备 px**（= 0.7545 × 屏宽；出处见 §1.4d，**921 已作废**——那是 DOM 浮层盘面路径的数，不是 canvas 路径）。<br>**pitch>0 不得用 927 判**：透视压缩下屏幕尺寸必然变小（按 549 口径复算 pitch30 ≈758px、pitch60 ≈669px）；pitch>0 档改判「盘面中心与 quad 中心一致」+「bearing 0/90 的 top/right 互换」。<br>**不得用竖直高度判盘径**：贴地是透视平面，竖直方向天然被 pitch 压缩（约为水平 × cos(pitch)），高度偏小是正确表现，只用于确认「确实倾斜」（高/宽 < 1）。<br>**扫描线必须避开屏心 ±40**：红十线 6px 宽贯穿全屏（`y=1350` 与 `x=614` 整行/整列全红），贴线取边必然失败（上一轮即因此作废）；亦可用 `crosshair 0` 钩子临时隐线（默认必须是显示）。<br>Δy=40 处圆的弦长与真径差 `1 - sqrt(1-(40/460)²) ≈ 0.4%`（≈3.6px），落在 ±0.005 容差内，可接受 |
| Q2 盘心 | 外接矩形中心 | 同上 | **x=614 ± 2、y=1350 ± 2**（与 M6 已测屏幕中心一致） |
| Q3 透明度 | 取盘面中心纯色区 `D`、无盘面时同点底色 `C0`、贴地后同点 `C1`，`a=(C0−C1)/(C0−D)` | 三个采样点各算一次 | 三次 `a` 互差 ≤ **0.02**；`a` 与代码里 alpha 常量差 ≤ 0.02 |
| Q4 随 bearing 转 | bearing=0 与 90 两张贴地截图，量「天池→外圈」方向向量的夹角 `θ` | 取盘面中心与某一确定性图元（如内圈顶点）连线 | **`θ` 必须是 90° 或 0°，且与实现声明的目标一致**；出现 ±45° 一类中间值即失败（说明转了两遍或只抵消一半）。原版出处：`ctx.rotate(r*Math.PI/180)`、`bearing: this.compass.getRotation()`（非 `map.getBearing()`）；**mode 位 2/4 时盘面转角与地图 bearing 相抵，屏幕上看不出转**（逆向 `a12c694` 读自 `index.946c1624e0.bundle.js` @41208/@49552/@43692）<br>**执行约束（2026-11-08 补）**：必须用 `view` 钩子的第 5 参 `bearing`（`window.__LC3D.setView(lat,lng,zoom,pitch,bearing)`，`map.html:3303`）；**不得用 `setHeading(90)`**——它是传感器朝向输入，只在 `sitMode≠0` 时才写 `bearing`（`map.html:1705`），用它测会得到 θ=0，把正确实现判成 FAIL。<br>**期望值**：屏幕转角 = `−bearing`（相机 forward = `(sin b,0,−cos b)`），0→90 应得 `|θ|=90°` |
| Q7 纬度/zoom 门控 | 纬度 76° 或 zoom=5 时贴地 | 造该状态后截图 | 原版判据 `75 > |lat| && zoom >= 6 && _isGround` 才绘制；超出范围应**不绘**贴地盘面（`a12c694`）<br>**执行**：`view --ef lat 76` 与 `--ef zoom 5` 各造一次（`zoom` 会被 clamp 到 ≥2，不影响 5） |
| Q5 不跟 DEM 起伏 | 平地与山地各贴地一次，比外接四边形四顶点 | 两次截图同法取四角 | 四个顶点坐标差 ≤ **2 px**（跟 DEM 会明显变形） |
| Q6 遮罩/可交互 | 贴地态下点右侧「3D」「图层」是否仍可点 | 点击后 `state.mode` 变化 | 变化即算通过（盘面不得吃掉全屏触摸） |

### 1.4b 前置门禁 Q0：`COMPASS_RINGS` 必须真加载（不通过则 Q1–Q7 一律不判）

2026-11-08 16:11–16:16 实测提交 `7d248aa`（本人独立构建 apk_sha `6f773a66…`，装机 `lastUpdateTime=2026-11-08 16:11:32`，`INSTALL_OK taps=2`；实现方自报的 `E509F15E…` 与我的构建哈希不同，属 APK 打包不可重现，不作差异）。

命令链（顺序固定，未通过前不测任何像素项）：

```
am broadcast -a com.luopan.fengshui.DEBUG --es cmd view --ef lat 39.9042 --ef lng 116.4074 --ef zoom 12 --ef pitch 60 --ef bearing 0
am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "toggleGround()"
am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "setGroundRenderer(1)"
```

实测（**已证实，阻塞**）：

- `getGroundDebug()` = `{"renderer":"canvas","ground":true,"mode":"3d","zoom":12,"lat":39.9042,"gateHit":false,"diag":"loading:rings","texDirty":true,"hasQuad":false,"hasRings":false,"hasGL":true}`；`getGroundQuad()` = `null`。
- Chromium 原文（`logcat -d`，tag `hwschromium-21705`）：`Fetch API cannot load file:///android_asset/map/compass-rings.json. URL scheme "file" is not supported.`；同一条 `fetch` 的两个候选路径均如此。
- 同页独立探针：`fetch('map.html')` 亦失败；同步 `XMLHttpRequest` 失败，报 `Failed to load 'file:///android_asset/map/compass-rings.json'`。
- 包内确认：`assets/map/compass-rings.json` 22,385 B **确实在 APK 里**（ZipFile 直读）；`MainActivity.java:3964` 只设 `setAllowFileAccess(true)`，**无** `setAllowFileAccessFromFileURLs(true)`。

判读：`7d248aa` 修的「fetch 相对路径少一层目录」是**真缺陷**（`map.html` 在 `assets/` 根，文件在 `assets/map/`），但不是全部原因——页面源是 `file:///android_asset/`，`fetch` 被 Fetch API 的 file 方案限制直接拒绝，`XHR` 又被 `setAllowFileAccessFromFileURLs` 默认 false 挡住，**两条路都不通**，故 `COMPASS_RINGS` 仍恒为 null，贴地 canvas 路径永远到不了 `drawn`、不产出 VP。

判据：进 Q1–Q7 之前先跑上述三条命令，**`hasRings:true` 且 `hasQuad:true` 才开工**；否则本轮整批判「阻塞」，不判 PASS、不判 FAIL。

可选出路（由实现方与总监定，验收方不代选）：

1. 原生读 assets 后经 `evaluateJavascript("__LC3D.setRings(<json>)")` 注入 —— 不动 WebView 安全设置；
2. 构建期把 `compass-rings.json` 内联进 `map.html` —— 零运行时 IO；
3. 开 `setAllowFileAccessFromFileURLs(true)` 并把 `fetch` 改成 `XMLHttpRequest`（`fetch` 是否可经设置放行**待确认**，Chromium 原文只证明当前被拒；仅改设置而不换 API，按现有证据无效）。

### 1.4c Q0 第二轮：内联数据成立，纹理上传被「画布污染」挡住（`e678419`，16:21–16:25）

本人独立构建 `e678419`（apk_sha `dcf9ec5a…`，装机 `lastUpdateTime=2026-11-08 16:21:56`，`INSTALL_OK taps=2`）。

**已通过的一半**（内联 `compass-rings.js` 方案成立）：`getGroundDebug()` = `{...,"hasRings":true,"hasGL":true,...}`，`logcat` 无 `rings-json` 失败，无 `Failed to fetch`。上一版 §1.4b 的机制性阻塞**已解除**。

**未通过的一半**（新的机制性阻塞，已证实）：

```
hwschromium-21705: [INFO:CONSOLE(3389)] "LCGROUND ERROR draw Failed to execute 'texImage2D' on
'WebGLRenderingContext': Tainted canvases may not be loaded."
```

停在 `texImage2D(groundCanvas)`：`drawGroundCompass` 已越过 renderer/ground/门控/glInit/rings 全部检查与 `groundRedraw()`，只剩把画布上传成纹理这一步抛 `SecurityError`。旁证：此时 `getGroundDebug()` 仍是 `diag:"skip:ground=off"`（上一状态），因为 `groundDiag='drawn'` 写在 `texImage2D` 之后，异常被 `map.html` 最外层 `catch` 吞掉，`hasQuad` 仍 `false`、`getGroundQuad()` 仍 `null`。

**污染源（代码级，已定位）**：`map.html:2436-2437` `groundImg.src = 'compass/dial_full_' + dialType + '.webp'` —— `file://` 源的图片画进 2D 画布会使画布 tainted；Chromium 对 `file://` 页面按不透明源处理，同目录图片同样污染。

**关门级探针（16:24，本机同一 WebView）**：把 1×1 PNG 做成 `data:image/png;base64,…` 画进 canvas 后 `texImage2D` —— 结果 `TAINT_DATA ok upload-succeeded`。即 `data:` 图片**不污染**画布。

**出路（不改 WebView 安全设置，与 §1.4b 同思路）**：把盘面位图也做成生成产物——`dial_full_<n>.webp` 转 base64 输出成 `dial-…js`（`window.__LC_DIAL[n] = 'data:image/webp;base64,…'`），`groundImg.src` 从该常量取；JSON/JS 双份校验沿用 `gen-compass-rings.py --check` 的做法。
另一条（跨模块，需客户端配合）：`MainActivity` 开 `setAllowUniversalAccessFromFileURLs(true)` 解除 `file://` 图片污染——本轮不选，避免放松 WebView 安全。

**判据不变**：`hasRings:true` **且** `hasQuad:true` 才进 Q1–Q7；本轮仍判「阻塞」，不判 PASS/FAIL。

### 1.4d 盘径数裁定（2026-11-08 16:3x，三人独立复算后定稿）

原版两条路径本来就差 0.6%，不是实现误差：

| 路径 | 来源 | CSS | 设备 px（×3.375） |
|---|---|---|---|
| DOM 浮层盘面 | `compassImg.style.width = c` | 273.0 | 921.4 |
| **贴地 canvas（本次判据）** | `drawImage(..., e, e)`，`e = getCompassParams().size` | **274.55** | **926.6 ≈ 927** |

我按原版 bundle 自己复算的证据（`assets/webapp/js/index.946c1624e0.bundle.js` 单行压缩，偏移量取自 `ReadAllText` + `IndexOf`）：

```
@39353  let c=window.screen.width/2*1.5, u=1.2*window.screen.width
@49593  getCompassParams:()=>({size:(u-c)*this._compassScale/100+c, bearing:…
@51057  this._compassScale=1        ← 构造器初值，默认态不是 0
```

⇒ `size = (u−c)/100 + c = 0.0045W + 0.75W = 0.7545W`；本机 `W = 1228/3.375 = 363.85 CSS` ⇒ **274.55 CSS = 926.6 设备 px**。

**定稿**：判据 **927 ± 4**；实现取 **`dialPx = 549`**（`274.638 × 2 = 549.28`）或 `GROUND_DIAL_FRAC = 0.37725`。**546（0.375，scale=0 近似）不再接受为默认值**——若实现坚持 546，台账必须写「按 scale=0 近似，非默认态」，且判据仍按 927 判会差 5.5 px、落在 ±4 之外，故不接受。

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

### 2.6 修正记录（2026-11-08，本人独立实测后订正）

**① 「当前位置符号：原版不可得（检索面内）」作废。** 我按 2.3 的归因规则判它「素材存在但主图未引用」，随后真机实测（装原版 `space.xinzhi.globe` v1.6.3 同机同屏 → `pm grant` 定位权限 → 等 60 s，`dumpsys location` 已有 network/gps fix）在定位成功后截到该符号，**结论改为「原版有」**。

**② 归因规则新增第 4 类：动态拼接引用。** `locationPointer` 在主图 bundle 里字符串 0 命中，但截图里出现该素材的**精确色 `#0077FF`**（精确等于该值的像素 726 个）——说明引用是运行时拼接的，**字符串检索不到 ≠ 未使用**。今后「检索零命中」只能支撑到「未在源码字面量中出现」，不能支撑「未使用」；要断言「原版没有」，必须同时满足：静态全量零命中 **且** 55 张原版截图像素检测零命中。

**③ 两个符号的交叉验证（本人独立测量，与逆向互不复用脚本）**

| 量 | 我测 | 逆向测 | 判定 |
|---|---|---|---|
| 中心红点 | 13×13，bbox `[608,1344][620,1356]`，圆心 (614,1350)，`rgb(255,81,99)`，2D/3D 两图同值 | 13×13、`#FF5163`、(614,1350) | 一致 |
| 蓝十字 | bbox 115×115，同中心 | 跨度 114×114，四段约 57×7 | 一致（±1px 阈值差） |
| 定位点蓝填充 | bbox `(139,1465)-(185,1499)`，47×35，主色 `rgb(0,119,255)`，精确色 726 px | bbox `(140,1467)-(183,1498)`，精确色 726 px | **完全一致（726 像素数相同）** |
| 定位点白描边 | 54×41 | 58×29 | 差异因「白描边/三角」重叠归属不同，不影响形状结论 |
| 是否随 bearing 转 | 未测（仅 1 张图） | 判「会转」（素材三角 12 点 → 截图 10–11 点） | **高度可疑**：单图对单素材，缺第二个数据点 |

**④ 待取证（不进实现判据）**：`bearing=X° → 三角指向 Y°` 的定量映射；三角语义（设备朝向 / 地图方位）；移动时是否脉冲/拖尾。取不到就按「旋转映射未取证、默认朝上」实现，台账不许写方向语义已确认。

### 2.7 第二次修正：我把「水滴」判错了（方法缺陷，不是数据差异）

我先前用「按 bbox 拟合椭圆、统计椭圆外蓝像素」得出「外部像素 0 ⇒ 尖端与主体连通（水滴）」。**该结论作废**，逆向用连通域标记推翻（`5243418`）：

```
精确 #0077FF 掩膜、8 邻域标记：856 点 → 2 个连通域
  #0 面积 789  bbox (140,1477)-(184,1499)  45×23  主体
  #1 面积  67  bbox (147,1466)-(160,1473)  14× 8  三角，**位于主体上方、不连通**
三角质心 − 主体质心 = (−9.2, −17.7) ⇒ 相对主体「向上偏左 27.3°」
```

**我的方法为什么必然错**：拟合椭圆用的 bbox（44×32）**本身就包含了三角**，等于「用一个包住两块的椭圆去检验有没有块在外面」，结构上只能得 0。**连通域不受此影响**——不连通就是两棵树。

**方法层教训（写进规范用）**：判断「图形是一块还是两块」用**连通域标记**，不要用「拟合形状后看残差」；后者对「残差被拟合物自身覆盖」这类错误免疫不了。

**最终形状口径**（取代此前所有版本）：主体 = 蓝色椭圆 45×23 设备 px + 纯白描边；三角 = **描边外侧独立一枚** 14×8，同色 `#0077FF`；三角方位本次观测「向上偏左 27.3°」；旋转映射未取证。

### 3 屏心符号验收结果（`c66fc28`，apk_sha `582012a6…`，装机 15:15:09）

三条判据 + 面板显隐，全部实测（`reports/M7-center-verify/`）：

| 状态 | 中心 (614,1350) | 红点紧测 bbox | 蓝臂跨度 | 判 |
|---|---|---|---|---|
| 地图态 | `(255,81,99)` = `#FF5163` | `[608,1344][619,1355]` = 12×12，中心 (613.5,1349.5) | **114×114** | **PASS** |
| 更多面板打开 | `(24,20,16)`，红 0 px、蓝 0 px | — | — | **PASS（符号隐藏）** |
| 面板关闭后 | `(255,81,99)` 恢复 | 12×12 | 114×114 | **PASS（恢复）** |

- 原版红点 13×13（`[608,1344][620,1356]`），我方 12×12，**左上角完全重合、右下角差 1px**，属 12.99px 绘制与抗锯齿阈值差，可接受；
- 蓝臂跨度 114 与原版**同值**，中心断开（红点覆盖）成立；
- 面板显隐：原版 `more-sheet-01` 中心为 `(255,255,255)`，我方打开面板后中心 `(24,20,16)`（遮罩色），**均为“无符号”**，判通过。

---

## 三、不进判据的东西

- 「看起来像」「和别的 app 差不多」不判；
- 原版 APK 起不来时的截图（L1 失败）不臆造，退 L2 或 L3；
- 第三方库内部字符串不作证据；
- 贴地若只有 L3，关账表写「无原版对照」，不写 1:1、不写“已修复”。
