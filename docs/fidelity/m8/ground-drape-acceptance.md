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
      canvasBacking = 546（=273 CSS × dpr2）、radiusMeters > 0
反例  diag 为 'skip:renderer=overlay' / 'skip:ground=off' / 'loading:rings' / 'fail:*' 一律 FAIL，
      并原样记录 diag 字符串（它是区分「没接线」和「画了但错」的唯一依据）
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

### 3.1 取样口径
```
canvas 渲染区 = canvasBacking × canvasBacking = 546 × 546 backing px
基线         = assets/compass/dial_full_N.webp（800×800）先降采样到 546×546 再比
              —— 两边同口径，避免把「分辨率先天不同」判成「迁移错误」
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
任一条不符 ⇒ 输出 BASELINE_INVALID 并作废该次比对（exit≠0），不许继续算差异
反证：拿 res/ 原图(2048) 冒充基线跑一次 ⇒ 必须被拦成 BASELINE_INVALID
```

---

## 4. G3 B1：贴地态指针隐藏

**实现可能用 `setVisibility(GONE)`、也可能 alpha=0 或移出屏幕，所以用「差集为主、dump 取严」。**
实现方已确认按 `View.GONE` 做（不用 alpha=0、不移出屏幕），因此 dump 与差集两条**都该成立**；仍按取严判，防以后改成 alpha 方案时判据悄悄失效。

```
预备  机位锁死：同 lat/lng/zoom/bearing/pitch、同 dialAlpha、同 base，只切 groundRenderer
      切档后各过一次 G1.0 稳定闸（四角两次读值一致）再取图
取样  A = overlay 态截图；B = canvas 态截图
判据① 指针专属像素：在 A 中定位竖针所在列区间（针是竖向细长图元，取 A 与「A 的盘面-only 帧」差集得针掩膜）
      针掩膜像素在 B 中的命中数 = 0
判据② （若 dump 能 idle）贴地态 dump 中不得出现指针节点；出现即 FAIL
二者取严：任一 FAIL 即 FAIL
反例  若 B 中针掩膜命中 > 0 ⇒ 指针没隐藏（B1 未实现）
      若 A 中针掩膜为空 ⇒ 取样错（指针本就没显示），该次取样作废重做，不许当 PASS
```

> 明知差异：B1 是「贴地态指针隐藏」，属**已批准的可见差异**，台账按已知差记，不写成与原版一致。

---

## 5. G4 十字经线不变（回归保护）

```
对象  原生 CompassCrosshairView（竖向 cx、横向 cy，CSS_PX=2）
判据  纯红 #FF0000（通道容差 ±2）
      竖线列 612..616、横线行 1347..1351（设备 px）
      overlay 态与 canvas 贴地态各测一次，两组**逐值相等**（±1 px）
含义  这是「canvas 不许自己再画一遍十字」的守卫；两条测的是同一视图，理应完全不变
反例  把 crosshair 平移 3 px 立刻重测 ⇒ 必须 FAIL（证明本判据抓得住位移）
```

---

## 6. 负向对照（**最容易被跳过、但没它整张表不算数**）

把档位改回旧态，看判据是否**变红**；如果旧态也能「通过」，说明判的是别的东西。

| 对照 | 操作 | 期望 |
|---|---|---|
| N1 未接线 | 只 `setGroundRenderer(1)`，产品按钮仍走 overlay（或干脆不切档） | G1 FAIL：`getGroundDebug().renderer='overlay'`、`diag='skip:renderer=overlay'`、quad=null |
| N2 全旧态 | 默认档（overlay）下按「贴地」按钮 | G1/G2 FAIL（G2 无 canvas 取样区，判「不可取样·FAIL」，**不许跳过当 PASS**）；G3 FAIL（指针仍可见） |
| N3 基线误用 | 用 2048 原图当 L2 基线 | `BASELINE_INVALID`，比对作废 |
| N4 十字位移 | crosshair 平移 3 px | G4 FAIL |
| N5 斜切冒充透视 | pitch 0→60 但 r 不变（或 r 反向） | G1.4 FAIL |

**N1、N2 必须在设备窗口里真跑一遍**，输出与原值一起贴；只写「应该会红」不算。

---

## 7. 执行命令（可复制；`-s` 指定设备）

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

## 8. 判定与签名

| 项 | 判 | 证据（每项三件：读数 + 截图/png + dump 或 JS 原文） |
|---|---|---|
| G1 四角投影 |  | `getGroundDebug()` / `getGroundQuad()` 原文 ×3 档 + 截图 |
| G2 像素阈值 |  | diff 图 + 数值表（两档 dialAlpha） |
| G3 指针隐藏 |  | A/B 两帧 + 针掩膜命中数 |
| G4 十字经线 |  | 两态红线列/行区间 |
| N1–N5 负向对照 |  | 每条的原始输出 |

- 跑在哪个 sha、`app` 树、装机包 `sha256:` 必须写在证据头
- 证据落 `docs/fidelity/m8/ground/`（图与数值分开存）
- **实现方不自签**；本表由 LC审核验 执行并签字

---

## 9. 不覆盖范围（写清，避免被当成已验）

- 原版贴地截图：**未见**。L2b 若拿不到，只判我方自洽并注明。
- 逐帧动画（贴地过渡）：设备无 `screenrecord`，本表不判动画，只判稳态四角与像素。
- 触控命中（贴地盘面是否可点/拖动）：本表不覆盖，另立判据。
- 文字可读性主观评价：只按掩膜 ΔE 判，不做人工偏好判断。
