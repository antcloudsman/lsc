# M8 贴地验收判据表（LC审核验，独立于实现方）

- 基线：`origin/develop` = `5baa44a`（`app` 树按实施提交核）
- 方案：`docs/fidelity/m7/ground-drape-migration.md` @ `blob:a4fb0e0b17becd55f09c53f9af4fe7e22ec2740b`（A1 + B1 + 800 基线，已定稿）
- 本表只定义**怎么判**，不含实现步骤；实现方不自签，四项由 LC审核验 独立跑。
- 铁律：判据报绿必须能同时回答三件事——**比对了什么、比全了没有、与时序无关**；每条都要有负向对照（第 6 节）。

---

## 0. 可核锚点（我已独立复算，非转引）

| 物件 | 值 |
|---|---|
| `assets/compass/rings.json` | `sha256:cf8b28af7f1d6a8d6af1c781ddd0eaca5a94d728a02278569c077a93fe259a7a`，33,698 B |
| `rings.json` 内 `source_sha256` | `1bde8890b2e947aa9d305f0a7bde8382eec77960260655b49b62314d5b1cbaae` = `CompassData.java` 的**内容 sha256**（不是 git blob；两者不能混比） |
| `dial-export-manifest.json` | `sha256:d9ce836c1305d1a7ce3ef8ec3ca472548a32d8354e56fbd327342635eca1a43a`，1,740 B，`total_bytes=1200656` |
| 进包 5 张 `dial_full_*.webp` | 逐张 sha256 与 manifest 全等：`ea9656a8…` / `c15aab05…` / `8f180e69…` / `882ef9a3…` / `5104aa7c…`；合计 **1,200,656 B** |

> L2 比对**只许**用这 5 张（800/q85）。1200 版仅作参考，**用它比对一律判无效**（第 3.4 节有拦截规则）。

---

## 1. 只读钩子（判据全部走这些，不读实现内部变量）

| 钩子 | 位置 | 用途 |
|---|---|---|
| `__LC3D.setGroundRenderer(1|0|'canvas'|'overlay')` | `map.html:2355` | 切档；入参已归一化（数字/引号/空白都吃） |
| `__LC3D.setGround(true|false)` | `map.html:2323` | 开/关贴地（幂等，同值不重复压机位） |
| `__LC3D.getGroundQuad()` | `map.html:3168` | 四角屏幕坐标；`overlay`/未开贴地/门控命中/基线帧返回 `null` |
| `__LC3D.getGroundDebug()` | `map.html:4106` | `renderer/ground/mode/zoom/gateHit/vw/screenW/canvasCss/canvasBacking/radiusMeters/diag/texDirty/hasQuad/hasRings/hasGL` |
| `__LC3D.setGroundBaseline(1|0)` | `map.html:2349` | 透明度基线帧（相机不动，只跳过盘面） |
| `__LC3D.getState()` | `map.html:4056` | `ground`、`groundRenderer`、`pitch`、`bearing`、`zoom`、`lat/lng`、`elev` |

**判据只读这些**；不去 grep 实现内部变量名，避免「实现一改名判据就假红」。

---

## 2. G1 四角投影（核心：必须是**地面平面四角**，不是屏幕空间正圆）

### G1.0 取图前的稳定闸（所有截图类判据共用）
```
做法  连续两次读 getGroundQuad() 与 getState().pitch，要求两次四角逐值相同（±0.5px）且 pitch 不变
理由  俯仰动画未收敛时，同一时刻两张图必然不同 —— 据此判 FAIL 是假红
不满足 ⇒ 该次取样作废（记 STALE_SAMPLE），等 1s 重取，最多 5 次；5 次仍不稳判「不可取样·FAIL」
```

### G1.1 存在性
```
前置  setGroundRenderer(1) → setGround(true) → 过 G1.0 稳定闸
判据  getGroundQuad() 返回长度 4 的数组，且每个元素 {x,y} 为有限数
      getGroundDebug(): renderer='canvas'、ground=true、hasQuad=true、diag='drawn'
      canvasBacking = 2 × vw × dpr（vw=364、dpr=2 ⇒ 1456；**不是 546**）、radiusMeters > 0
等待  diag='loading:rings' 是**异步等待态，不是失败**：最多等 5s（每 500ms 重读一次），
      期间不判红；5s 后仍是 loading:rings 才判 FAIL 并记录
      **（11-09 订正，`fail:rings` 修法落地后生效）**：正常路径 `compass-rings.js` 是同步注入，
      首帧即 `drawn`，所以 `loading:rings` 持续不走只有一种解释——盘面数据加载失败。
      此时修法应已把它报成 `diag='fail:rings'`、`reason='loader-missing'` ⇒ **直接判 FAIL，不再等 5 秒**。
      「等 5s」只在修法未落地期间保留，落地后作废，避免留下一条永不触发的检查。
反例  diag 为 'skip:renderer=overlay' / 'skip:ground=off' / 'fail:*' 一律 FAIL，
      并原样记录 diag 字符串（它是区分「没接线」和「画了但错」的唯一依据）
口径  所有读数一律以 getState() / getGroundDebug() **主动拉取**的值为准；
      事件（groundstate）只当「有变化了，去拉一下」的触发信号，不采信事件内容
```

### G1.2 角序
```
判据  [NW,NE,SE,SW] 且（屏幕 y 向下）
      NW.x < NE.x、SW.x < SE.x、NW.y < SW.y、NE.y < SE.y
```

### G1.3 pitch=0 几何
```
判据  上边 |NE−NW| 与下边 |SE−SW| 之差 ÷ 下边 ≤ 0.02（近矩形）
      框宽 = 2 × vw = 728.0 ± 2 CSS（vw 取 getGroundDebug().vw；A 轮实测 sideCss=728.0164）
      |宽 − 高| ≤ 2 CSS（pitch=0 近正方形）
```

### G1.4 tilt 下透视证据（**不许只看「是个四边形」**）
```
采样  pitch ∈ {0, 30, 60}，bearing=0，其余相机参数不变
比值  r(pitch) = |NE−NW| / |SE−SW|（上边 ÷ 下边）
判据  r(0) ≥ 0.98
      r(0) > r(30) > r(60)，且相邻两档差值 ≥ 0.05（单调收紧）
      r(60) ≤ 0.95（60° 下近边必须明显更宽 ⇒ 透视真的发生）
凸性  四角叉积同号（不自交、不退化）
中线  两条对角线交点 x 落在 |vw/2 − x| ≤ 2 CSS
```
> 该条同时挡两种假货：**正圆**（不是四角，G1.1 就红）与**斜切平行四边形**（r 不随 pitch 单调变、或 r(60)≈r(0)）。

### G1.5 不随 DEM 起伏（平面四角，不跟地形）
```
做法  同一相机（zoom/pitch/bearing 固定），先在低海拔点、再在高海拔点各取 quad
      低海拔：北京中心（elev ≈ 53 m）；高海拔：珠峰附近（elev ≈ 8699 m），两处 >75° 门控不触发
判据  四角屏幕坐标逐点差 ≤ 0.1 px（A 轮同法实测 0.039 px）
      若某角差 > 1 px ⇒ 说明贴地跟着 DEM 变形，判 FAIL（与「平面四角投影」的设计相反）
```

### G1.6 bearing=90
```
判据  pitch=0、bearing 0→90：四边形整体转 90°，宽高互换（|w0−h90| ≤ 2 CSS、|h0−w90| ≤ 2 CSS）
      角序仍满足 G1.2（靠屏幕坐标判，不靠数组下标）
```

---

## 3. G2 盘面与 800 基线的像素阈值

### 3.1 取样口径（数值按 2026-11-09 真机实测订正）
```
（订正）画布尺寸 = canvasBacking = 2 × vw × dpr。实测 vw=364、dpr=2 ⇒ **1456**。
       早先写的「546」是「盘面占画布 0.375」的推算值，**不是画布尺寸**，用它会取错区域。
（订正）盘面在画布内的实测直径 = **dial 549 backing px**（设备日志：`LCGROUND draw … tex=1456px dial=549`）。
       判据写法：画布取 canvasBacking×canvasBacking（实测 1456），盘面取**居中 549×549** 子区，
       与 800 基线降采样到 549×549 比；两者都必须用**实测值**，不许写死常量。
基线         = assets/compass/dial_full_N.webp（800×800）降采样到与「盘面实测直径」一致再比
              —— 两边同口径，避免把「分辨率先天不同」判成「迁移错误」
降采样算法   **必须双线性（bilinear）**，与贴地纹理上传的 `gl.TEXTURE_MIN_FILTER/MAG_FILTER = gl.LINEAR`
             一致（`map.html:3070-3071`）。用 Lanczos / nearest / area 都不行：
             插值差异会直接算进平均 ΔE2000，等于把「换了个缩放算法」判成「迁移错误」。
             所用 filter 必须写进比对输出（可复核项）。脚本不接受「未声明 filter」的结果。
```

### 3.2 阈值（照方案 §5 已批口径，不放宽）
```
差异像素占比          ≤ 2%
平均 ΔE2000           ≤ 3
关键色（金环/五行字/天支线）ΔE ≤ 2
文字区（二十四山/卦名/读数）掩膜单判，阈值同上，**不接受整体放宽**
两档必须各跑一次      dialAlpha 30% 与 100%
```

### 3.3 L2b 观感（有原版才判）
```
真机贴地截图 vs 原版贴地截图；原版截不到 ⇒ 只判我方自洽，并在证据里写明「无原版对照」，
不得写成「与原版一致」。
```

### 3.4 基线用错时的拦截（**必跑，防止算出大数就判 FAIL**）
```
脚本第一步先校验基线：long_edge 必须 = 800、5 张合计 = 1,200,656 B、逐张 sha256 与 manifest 相等
、且已声明降采样 filter = bilinear
任一条不符 ⇒ 输出 BASELINE_INVALID 并作废该次比对（exit≠0），不许继续算差异
反证①：拿 res/ 原图(2048) 冒充基线跑一次 ⇒ 必须被拦成 BASELINE_INVALID
反证②：把降采样换成 nearest 跑一次 ⇒ 必须被拦成 BASELINE_INVALID（filter 未声明/不合规）
```

---

## 4. G3 B1：贴地态指针隐藏

**实现可能用 `setVisibility(GONE)`、也可能 alpha=0 或移出屏幕，所以用「差集为主、dump 取严」。**
**（11-09 订正）`dump` 在本 App 不可用**：WebView 持续重绘，`uiautomator dump` 反复报
`ERROR: could not get idle state`（M7 与 M8 共五次），`--compressed` 同样失败。
因此 G3/G5 里凡写「dump 里节点回来/消失」的，一律改为**截图掩膜**判；dump 仅作补充证据。
实现方已确认按 `View.GONE` 做（不用 alpha=0、不移出屏幕），掩膜差分本就足够。

```
预备  机位锁死：同 lat/lng/zoom/bearing/pitch、同 dialAlpha、同 base，只切 groundRenderer
      切档后各过一次 G1.0 稳定闸（四角两次读值一致）再取图
取样  A = overlay 态截图；B = canvas 态截图
判据① 指针专属像素：在 A 中定位竖针所在列区间（针是竖向细长图元，取 A 与「A 的盘面-only 帧」差集得针掩膜）
      针掩膜像素在 B 中的命中数 = 0
判据② 贴地态**截图掩膜**中不得出现指针专属像素；`dump` 仅在其可用时作补充（本 App 实测不可用）
二者取严：任一 FAIL 即 FAIL
反例  若 B 中针掩膜命中 > 0 ⇒ 指针没隐藏（B1 未实现）
      若 A 中针掩膜为空 ⇒ 取样错（指针本就没显示），该次取样作废重做，不许当 PASS
```

> 明知差异：B1 是「贴地态指针隐藏」，属**已批准的可见差异**，台账按已知差记，不写成与原版一致。

---

## 5. G4 十字经线不变（回归保护）

**前置（11-09 真机实测新增）：必须在罗盘态测。** `crosshairView.setVisibility(View.GONE)` 在
`MainActivity:684` 初始化即隐藏，地图态下截图里**取样不到任何红色像素**（实测 x=610..619 / y=1346..1354
全为地图底色 `(211,214,202)`）。不切罗盘态就测，会得到「十字不见了」的假红。

```
对象  原生 CompassCrosshairView（`paint.setColor(Color.RED)` @:44、`FILL_AND_STROKE` @:58、
      7px 描边、`ANTI_ALIAS_FLAG` @:40 ⇒ 两侧边缘是抗锯齿过渡）
判据① 描边中心取样：竖线取列 614、横线取行 1349，判 #FF0000（通道容差 ±2）
判据② 覆盖区间 612..616 / 1347..1351 只判「非背景」（不许按纯红卡边缘，否则合法实现会被抗锯齿判红）
判据③ overlay 态与 canvas 贴地态各测一次，三条结果**逐值相等**（±1 px）
含义  这是「canvas 不许自己再画一遍十字」的守卫；两条测的是同一视图，理应完全不变
反例  把 crosshair 平移 3 px 立刻重测 ⇒ 必须 FAIL（证明本判据抓得住位移）
```
> 取样位置写死为「描边中心」不是随意选的：`FILL_AND_STROKE` + 抗锯齿会让 `612/616` 两列呈现渐变，
> 用纯红判会把「抗锯齿正常」判成「颜色变了」。中心列稳定，边缘列只作存在性判。

---

## 6. G5 canvas 不可用时的回退（「失败要能看出来，不能静默」）

WebGL 挂掉时必须回退显示原生盘面；否则用户按「贴地」等于什么都没发生，比不实现更糟。

### G5.1 静态：实现里必须存在失败回退分支
```
判据  canvas 路径返回失败（glInit 失败 / diag 以 'fail:' 开头）时，代码必须有分支把原生盘面恢复可见
要求  交付时指出该分支的文件:行号，并说明它覆盖哪几种失败（glInit / 纹理上传 / 四角缺失）
反证  把该分支临时去掉 ⇒ 下面 G5.2 必须变红（否则说明这条根本没被执行到）
```

### G5.2 真机：失败态下原生盘面仍可见（**只在罗盘态成立**）
```
前置（11-09 真机实测订正，必守）：必须在**罗盘态**（compassMode=true）测。
      回退对象是原生 CompassView，可见条件 `MainActivity:1028 show = compassMode && !compassHidden && !groundDrapeActive()`。
      地图态（compassMode=false）本就不显示原生盘面 —— 对照组实测：贴地关 + overlay 档 + pitch60，
      截图里同样没有原生盘面。因此 G5① 在地图态**无对象可回退**，判「不适用（需罗盘态）」，
      **不记 PASS、也不记 FAIL**。
构造  __LC3D.__forceGroundFail('glinit' | 'upload' | 'rings')   ← 页面侧失败注入，可复位
      再 setGroundRenderer(1) → setGround(true)
判据  三件必须同时成立（缺一件即 FAIL）：
      ① 罗盘态下原生盘面可见：**截图盘面掩膜计数 > 0**（`dump` 在本 App 不可用，见下方说明）
      ② 页面读数不撒谎：getState().groundRenderer 仍 = 'canvas'（用户意图不被改写）、
         groundCanvasOk = false、groundDiag = 'fail:glinit'（与注入值一致）
      ③ 原生侧读数与 ② **逐字相同**（不许多一个字、不许少一个字）
反证  __forceGroundFail(null) 复位后重测 ⇒ ①原生盘面转隐、②groundCanvasOk=true、③同步一致
复位时序（**必守，否则假红**）：`__forceGroundFail(null)` 只清注入并置 texDirty，**下一帧**才回到 'drawn'；
      复位后立刻读会读到旧的 `fail:*`。正确做法：复位 → 连读两次（间隔 ≥1 帧，
      或轮询到 diag 变化，上限 3s）→ 取稳定值再判。复位的判据不含「复位瞬间的状态」
说明  ② 防「静默」（失败了却还说自己好），③ 防「两边各说各话」（原生化成一句人话就丢了原因）

### G5.5 地图态失败必须有可见提示（**改实现，不是改判据**）
```
背景  地图态（compassMode=false）在罗盘态开贴地后再切过来是**用户可达路径**；
      canvas 失败时屏幕上既没有贴地盘面也没有原生盘面 ⇒ 盘面凭空消失，用户看不出发生了什么。
判据  地图态 + 注入 glinit ⇒ 屏幕上必须出现一条可见提示（可复用 offlineBar，已有 safeArea 避让，不新造浮层），
      文案须能读出「贴地不可用/已回退」语义；截图 + 文本 content 双证。
反证  去掉该分支 ⇒ 本项 FAIL（屏幕回到「什么都没有」）
实测  2026-11-09 在 50fff01 上：注入后 screen 上无任何提示（见 reports/M8/ground-1b75480/g5-injected.png），
      本项当时为 **FAIL**，已由总监判定为「改实现」，实现补完后重验。
```
```

### G5.2b 取不到失败态时的退路（仅在 __forceGroundFail 未实现时）
```
判据  diag 以 'fail:' 开头 ⇒ 截图中盘面像素必须仍存在（沿用 G3 盘面掩膜）
可构造性：真机无法稳定构造 glInit 失败 ⇒ 记「不可构造」，以 G5.1 静态断言 + 日志证据代替，
      不得写成「已验证回退」
```

### G5.3 未知档位不改行为（可构造，必跑）
```
操作  __LC3D.setGroundRenderer('bogus')
判据  renderer 仍为 'overlay'、diag 以 'skip:renderer=' 开头、原生盘面照常显示
含义  「不认识的值」不许被当成 canvas 处理；这是 setGroundRenderer 归一化逻辑的守卫
```

### G5.4 失败原因必须读得出来（「说得清为什么退」）
```
判据  getState().groundDiag **粘滞（sticky）**：一旦失败，不许被后续空值 / 成功前状态覆盖成空或 'idle'
      原生侧读到的是页面原样字符串（groundDiag），**不许在本机重写成一句人话覆盖它**
      原生回退条件只看 groundOn && !groundCanvasOk，**不许自己推断「好像没画出来」**
      fail-safe 默认值：还没收到任何页面状态之前，原生盘面保持可见
已知缺口（实现方自报，判据据此放宽到「可接受」而不是判红）：
      `loading:rings` 是等待态、**不触发 groundstate 事件**；只靠事件驱动的原生侧会停在旧 diag。
      因此允许原生保留 700ms 定时回读作为补充手段；判据只看**拉取值**，不看事件次数。
      **按后果分两半写（11-09 订正）**：
      · 加载成功时：它是瞬时等待态 ⇒ 不算缺陷，700ms 回读可接受；
      · 加载失败时：它变成「静默失败」（显示成还在加载）⇒ **判 FAIL**；
        修法落地后应报 `diag='fail:rings'`、`reason='loader-missing'`，这一半才可判。
反证  把 sticky 逻辑改坏（让 diag 可被覆盖成空）⇒ 本判据必须 FAIL
含义  G5 判的不是「有没有回退」，而是「退得对不对、说得出为什么退」；只说「回退正常」不算过
```

---

## 7. 负向对照（**最容易被跳过、但没它整张表不算数**）

把档位改回旧态，看判据是否**变红**；如果旧态也能「通过」，说明判的是别的东西。

| 对照 | 操作 | 期望 |
|---|---|---|
| N1 未接线 | 只 `setGroundRenderer(1)`，产品按钮仍走 overlay（或干脆不切档） | G1 FAIL：`getGroundDebug().renderer='overlay'`、`diag='skip:renderer=overlay'`、quad=null |
| N2 全旧态 | 默认档（overlay）下按「贴地」按钮 | G1/G2 FAIL（G2 无 canvas 取样区，判「不可取样·FAIL」，**不许跳过当 PASS**）；G3 FAIL（指针仍可见） |
| N3 基线误用 | 用 2048 原图当 L2 基线 | `BASELINE_INVALID`，比对作废 |
| N4 十字位移 | crosshair 平移 3 px | G4 FAIL |
| N5 斜切冒充透视 | pitch 0→60 但 r 不变（或 r 反向） | G1.4 FAIL |
| N6 失败注入 | `__forceGroundFail('glinit')` 后按贴地 | G5.2 三件同时成立才算过；任何一件不成立 → FAIL |
| N7 sticky 被改坏 | 让 diag 可被覆盖成空 | G5.4 FAIL（这是「防静默」的核心反证） |

**N1、N2 必须在设备窗口里真跑一遍**，输出与原值一起贴；只写「应该会红」不算。

---

## 8. 执行命令（可复制；`-s` 指定设备）

```powershell
$d='L2E0222531014833'
function JS($v){ adb -s $d logcat -c; adb -s $d shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$v'" | Out-Null; Start-Sleep -Milliseconds 1100; (adb -s $d logcat -d -s LCJS | Select-String 'map ->' | Select-Object -Last 1).Line }

# G1：切档 + 开贴地 + 读四角与诊断
JS "__LC3D.setGroundRenderer(1)"; JS "__LC3D.setGround(true)"
JS "JSON.stringify(__LC3D.getGroundDebug())"
JS "JSON.stringify(__LC3D.getGroundQuad())"
JS "JSON.stringify(__LC3D.getState())"

# G1.4：三档 pitch（用产品既有视角接口，bearing 固定）
JS "__LC3D.setView(39.9042,116.4074,12)"; adb -s $d exec-out screencap -p > pitch0.png
# pitch 由 HUD/手势调整到 30 / 60，逐档重复读 quad 并截图

# G3：同机位 overlay 与 canvas 两帧
JS "__LC3D.setGroundRenderer(0)"; adb -s $d exec-out screencap -p > A-overlay.png
JS "__LC3D.setGroundRenderer(1)"; adb -s $d exec-out screencap -p > B-canvas.png
```

> 常量说明：`com.luopan.fengshui.DEBUG` 的 `cmd js` 通道在 `MainActivity` 里实现；JS 参数含括号时用单引号包整串（`sh` 元字符），否则 `unexpected '('`。

---

## 9. 判定与签名

| 项 | 判 | 证据（每项三件：读数 + 截图/png + JS 原文；**dump 仅在其可用时作补充**） |
|---|---|---|
| G1 四角投影 |  | `getGroundDebug()` / `getGroundQuad()` 原文 ×3 档 + 截图 |
| G2 像素阈值 |  | diff 图 + 数值表（两档 dialAlpha） |
| G3 指针隐藏 |  | A/B 两帧 + 针掩膜命中数 |
| G4 十字经线 |  | 两态红线列/行区间 |
| N1–N7 负向对照 |  | 每条的原始输出 |

- 跑在哪个 sha、`app` 树、装机包 `sha256:` 必须写在证据头
- 证据落 `docs/fidelity/m8/ground/`（图与数值分开存）
- **实现方不自签**；本表由 LC审核验 执行并签字

---

## 10. 不覆盖范围（写清，避免被当成已验）

- 原版贴地截图：**未见**。L2b 若拿不到，只判我方自洽并注明。
- 逐帧动画（贴地过渡）：设备无 `screenrecord`，本表不判动画，只判稳态四角与像素。
- 触控命中（贴地盘面是否可点/拖动）：本表不覆盖，另立判据。
- 文字可读性主观评价：只按掩膜 ΔE 判，不做人工偏好判断。
