# M6 Stage 1 · 逆向取证（第 1/3/6/7 条）

> 原版对照基线 **1.6.1**；DEX 证据取自 `.inspect/unpack/mem_smali`（Frida 内存 dump，方法体真实），Web 证据取自 `decoded/assets/webapp/js/index.946c1624e0.bundle.js`（1.6.1 解包树）。
> 本文件只写「有出处」的结论；拿不到的写「未取证」。
> 开工 01:02 / 本批落盘见文末。

---

# 第 6 条 指南针按钮 vs 指针开关 —— **两个控件**（结论已定，可放行）

## 6.1 三条证据（总监要的三件）

### ① 视图树位置

两者都在**同一个 Compose 覆盖层** `CompassToolsKt.n0(BaseActivity, MainViewModel, Function0, Function0, Composer, int, int)`（该函数收**两个** `Function0` 参数），但接在**不同 clickable** 上：

| 控件 | 合成 lambda（Compose 生成） | 位置 |
| --- | --- | --- |
| 罗盘复位 | `CompassToolsKt;->h0(Lspace/xinzhi/globe/ui/controller/g;)Lkotlin/Unit;` | `ui/tools/CompassToolsKt.smali:3238`（调用点 :3245） |
| 指针开关 | `CompassToolsKt;->i(Lspace/xinzhi/globe/viewmodel/MainViewModel;)Lkotlin/Unit;` | `ui/tools/CompassToolsKt.smali:3267`（调用点 :3274） |

### ② 点击 handler 的 DEX 地址

```
罗盘复位：
  CompassToolsKt;->I0(g)                       ui/tools/CompassToolsKt.smali:684
    ├ TAHelper.track("compass_ck", "name", "罗盘复位")      :694-:700
    └ g;->I()V                                 ui/controller/g.smali:780
        ├ MapWebApiKt;->resetCompass(controller)   map/MapWebApiKt.smali:2637  → JS 桥 "resetCompass"
        └ 复位一串盘面参数（见 6.2）

指针开关：
  CompassToolsKt;->Q0(vm)                      ui/tools/CompassToolsKt.smali:1116
    ├ MainViewModel.M0()Z  读 h0                viewmodel/MainViewModel.smali:5951
    ├ xor-int/lit8  0x1                         :1126
    └ MainViewModel.b3(Z)V 写 h0                :7693
```

### ③ 是否读写同一状态位 —— **不是**

| 项 | 罗盘复位 `I0` | 指针开关 `Q0` |
| --- | --- | --- |
| 碰 `MainViewModel.h0` | **没有**（全文无 `M0()`/`b3()`） | 只碰这一个（`M0()` 读 + `b3()` 写） |
| 碰 `controller g` | 只碰这一个（`g.I()`） | **没有** |
| 全文交叉验证 | `h0` 的读写方全仓只有 `MainViewModel` 自身 + `CompassToolsKt`，无第三处 | 同 |

**一条原版自证的旁证**：`I0` 第一句埋点就是 `track("compass_ck", "name", "罗盘复位")` —— 原版给这颗按钮起的名字就是「罗盘复位」。同一 `compass_ck` 事件下其它 `name` 是 `切换 / 旋转 / 缩放 / 透明度`（`CompassToolsKt$d/$e/$f/$g`），与复位是**并列的独立按钮**。

## 6.2 `g.I()` 到底复位什么（字段映射，全部已证）

`g.I()` 的语句序列与每个 setter 对应的字段 / getter / 语义：

| 调用 | 字段 | getter | 语义（已证） | 证据 |
| --- | --- | --- | --- | --- |
| `P(false)` | `e` | `B()` | 罗盘**锁定** = 关 | `M()` 掩码 `0x10`；web `16 & mode → setLock` |
| `R(false)` | `k` | `C()` | **自动计算磁偏角** = 关 | `setAutoComputeMagneticDeclination(g.C())` @g.smali:892 |
| `N(false)` | `l` | `A()` | **跟随传感器（朝北）= 关** | 见 6.3 |
| `L(0)` | `o` | `f()` | **缩放增量 = 0**（scale = 1.0） | `k0()` `setScaleX(1 + f()/200f)` |
| `K(100)` | `r` | `e()` | **透明度 = 100%** | `k0()` `setAlpha(e()/100f)` |
| `j0(0)` | `u` | `z()` | 天空线**角度** = 0 | `setCompassSkylineAngle(g.z())` @g.smali:880 |
| `e0(true)` | `t` | `v()` | 天空线**可见** = 开 | `setCompassSkylineVisible(g.v())` @g.smali:865 |
| `Q(0.0)` | `w` | `h()` | **磁偏角** = 0 | `k0()` rotationZ 里 `+h()`；`F(DD)` 拉 `getMagneticDeclination` 写它 |
| `W(1.0f)` | `x` | `n()` | 拖动/偏移系数 = 1.0 | `CompassToolsKt;->j0(g,F,Offset,F)` 内读 `n()` |
| `V(0.0f)` | `y` | `m()` | **固定旋转角 = 0** | `k0()` 里 `A()` 为假时 rotationZ = `m()` |
| `S(Offset.Zero)` | `z` | `j()` | **屏幕偏移量 = (0,0)** | `j0()` 读 `j()` 返回 Offset |
| `k0(false)` | `d` | `D()` | **贴地 = 关** | `M()` 掩码 `0x8`；web `8 & mode → setGround` |

关键读取点在 `CompassToolsKt;->k0(g, vm, GraphicsLayerScope)`（`:4470`）：

```
setAlpha( g.e() / 100f )                       :4480-4495
setScaleX( 1f + g.f()/200f ) setScaleY(同)      :4498-4540
if ( g.A() ) rotationZ = -vm.q0() + g.h()      :4543-4573   ← 跟传感器
else         rotationZ = g.m()                 :4577-4583   ← 手动固定角
setRotationZ( rotationZ )                      :4584
```

## 6.3 「哪个决定复位后还跟不跟传感器」—— `A()`（字段 `l`，setter `N(Z)`）

- `A()` 为 **true**：`rotationZ = -vm.q0() + g.h()`，`q0()` 是实时方位角 → **盘面每帧跟传感器**。
- `A()` 为 **false**：`rotationZ = g.m()`，即手动固定角 → **不跟**。
- `I()` 里是 `N(false)` → **复位后不再跟随传感器**，停在 0°，直到用户再把它打开。

⇒ **复位是永久有效的**（不会被下一帧传感器改回去）——前提是我方实现里也把「跟随」这一位置成 false。
⇒ 客户端那边 `CompassView.reset()` 设 `followSensor=true` 正好相反：`true` 会让下一帧覆盖。**这是我方语义写反了，不是「只生效一帧」这种实现细节。**

`A()` 同时驱动 mode 位：`M()` 里 `A() → 0x1`，web 侧 `1 & mode → map.setBearing(r)`。所以「跟随」是**盘面与地图共用一个模式位**，不是两条独立通路。

## 6.4 原版 `resetCompass` 的 JS 侧真的会转地图 —— 用 `map.resetNorth()`

`compassUI.reset()`（`index.946c1624e0.bundle.js` @50802，逐字）：

```js
reset: () => {
    this.setMode({ value: 0 });
    this.setOpacity({ opacity: 100 });
    this.setScale({ scale: 0 });
    this.setRotation(0);
    this.map.resetNorth({});       // ← MapLibre 原生 API：bearing 归 0
}
```

⇒ **用户说的「让偏转的地图立即回到上北下南」在原版里就是 `map.resetNorth({})`**。
⇒ 我方 `map.html` 里 `resetCompass` 零命中（客户端已核），所以现在**地图完全不动**——这是「按钮只做了一半」，不是语义分歧。

### 6.4.1 复位范围（回答「zoom / center 要不要复位」）

逐字看 `reset()` 只动四项 + `resetNorth()`：**mode / opacity / scale / rotation**。
**没有** `jumpTo/setCenter/setZoom` ⇒ **原版复位不复位 `center`，也不复位 `zoom`**。

⇒ 总监「默认不复位 center」的裁定**与原版一致**；`zoom` 同理**不复位**（不是「等逆向确认」，现在可以定）。

## 6.5 web 侧 mode 位掩码（实现时要照抄）

`index.946c1624e0.bundle.js` @43827 逐字：

```js
2  & this.mode → setRotation(r); map.setBearing(-r)
4  & this.mode → map.setBearing(r); setRotation(-r)
1  & this.mode → map.setBearing(r)
8  & this.mode → setGround(8 & mode)
16 & this.mode → setLock({ value: 16 & mode })
```

原生侧 `M()` 组位：`(D() ? 8 : 0) | (B() ? 16 : 0) | (A() ? 1 : 0)`（`ui/controller/g.smali:937`）。
⇒ 原生只用 bit 1/8/16；bit 2/4 由 `setOrientation` 那条路走。

## 6.6 给实现方的四句硬话

1. **罗盘复位 ≠ 指针开关**；P1-33-C 原判据有效，第 6 条是**新增**判据。
2. 复位必须两半都做：盘面（`N(false)` + `rotation=0` + 透明度/缩放/偏移/磁偏角归位）**和**地图（等价 `map.resetNorth()`）。
3. **必须把「跟随」置 false**，否则下一帧传感器就改回去——原版就是这么做的（`N(false)`）。
4. **不要复位 `center` / `zoom`**；要改 zoom 也必须走 `setZoom()` 不裸赋值。

---

# 第 1 条 红色贯穿十字经线 —— **CSS 伪元素绘制，2px，纯红，挂在 viewport-aligned Marker 上**

## 1.1 绘制层：DOM 覆盖层（不是 Canvas，不是 GLSL）

`createCompassLine()`（`index.946c1624e0.bundle.js` @52192 附近，逐字）：

```js
createCompassLine() {
    let e = document.createElement("div"),          // .compass-line-container
        t = document.createElement("div");          // .compass-line
    t.className = "compass-line";
    e.className = "compass-line-container";
    let r = document.createElement("div");          // .compass-line-marker
    r.className = "compass-line-marker";
    t.appendChild(r); e.appendChild(t);
    this.compassLineDom = t;
    this.compassLine = new s.Marker(e, { rotationAlignment: "viewport", pitchAlignment: "map" });
    return this.compassLine;
}
```

⇒ 层 = **MapLibre Marker 内的 DOM 元素**，由 CSS 伪元素画线。
⇒ `rotationAlignment: "viewport"` = **不随地图 bearing 旋转**；`pitchAlignment: "map"` = 随地形俯仰贴面。

## 1.2 色值与线宽（CSS，逐字）

同一 bundle 内联样式（@199923 起）：

```css
.compass-line { pointer-events: none !important; }
.compass-line { width: 150%; height: 100%; }
.compass-line::before {           /* 横线 */
    content: ''; position: absolute; top: 50%;
    width: 100%; height: 2px;     /* ← 线宽 2px */
    transform: translate(0, -50%);
    background: red;              /* ← 纯红 #FF0000 */
}
.compass-line::after {            /* 竖线 */
    content: ''; position: absolute; top: 0; left: 50%;
    width: 2px; height: 100%;     /* ← 线宽 2px */
    transform: translate(-50%, 0);
    background: red;
}
.compass-line-marker {            /* 中心红三角 */
    position: absolute; left: 50%; top: 27%;
    transform: translate(-50%, 0);
    width: 0; height: 0;
    border-width: 8px; border-style: solid;
    border-color: transparent transparent red transparent;   /* ← 红色三角，8px 边框 */
}
```

- 颜色：`red` = `#FF0000` = `rgb(255,0,0)` —— **与客户端实测的「纯红 [255,0,0]」一致**。
- 线宽 **2px（CSS px，不是 dp）**。设备侧 px = 2 × WebView dpr。
- 尺寸：`.compass-line` 宽 `150%` 高 `100%`，**相对 Marker 元素**；Marker 定位在**屏幕中心**（`updateCompassSite()`：`map.unproject([screen.width/2, screen.height/2])`），所以横线以屏幕中心为中点、宽度 150% 视觉上贯穿左右，竖线沿整高。
- **是否随朝向旋转**：`rotationAlignment: "viewport"` → **不随 bearing 转**。锁定态例外：`setLock({value})` 里 `compassLine.setRotation(...)` + `setRotationAlignment(e ? "map" : "viewport")` —— **锁定时改成 `"map"`（随地图转）**。

## 1.3 与本项目现状的差异（给客户端）

| 项 | 原版（已证） | 我方现状（客户端报） |
| --- | --- | --- |
| 颜色 | `red` `#FF0000` 全不透明 | `argb(120,180,50,50)`（半透明暗红） |
| 线宽 | **2 CSS px** | `1f` 像素且**没乘 density** |
| 长度 | 横 150% 宽 / 竖 100% 高（相对 Marker） | 只画到圆径 1212px |
| 绘制方式 | CSS 伪元素（DOM） | Canvas drawLine |

⇒ 修法建议（不改原版语义）：颜色改 `#FF0000` 不透明、线宽按 density 换算成 2dp 等效、长度覆盖全屏。**原版是 DOM 层，我方是 Canvas 层——载体不同但像素结果可以一致，不必为此换架构。**

---

# 第 3 条 两个符号 —— 屏幕中心符号**已证**（CSS 三角），当前位置符号**未取证**

## 3.1 屏幕中心符号 = `.compass-line-marker` 红色三角

见 1.2 最后一段：8px 红色三角，`left:50%; top:27%`，`translate(-50%,0)`，属 `.compass-line` 的子元素（即在 Marker 内、屏幕中心竖线上，位于 27% 高处）。
⇒ **不是 drawable，是 CSS border 三角**。我方不该去找 png。

## 3.2 「指针」= `pointer.png`，**竖向**，显示 23×94 CSS px

`index.946c1624e0.bundle.js` @52142 逐字：

```js
let r = document.createElement("img");
r.className = "pointer";
r.src = "assets/imgs/compass/pointer.png";
this.pointer = r;
e.appendChild(t); e.appendChild(r);   // 加进 compass Marker 容器
```

CSS（@201361）：

```css
.pointer {
    width: 23px; height: 94px;
    position: absolute; left: 50%; top: 50%;
    transform: translate(-50%, -50%);
    z-index: 99999;
}
```

- 源图 `assets/imgs/compass/pointer.png` 尺寸 **46×188**（我方已入库 `docs/fidelity/assets/compass/pointer.png`），显示 **23×94** = 2× 图按 dpr 2 放置。
- **竖向**（23 宽 94 高）。
- 显隐：`setPointerVisible({show})` → `this.pointer.style.opacity = show ? "1" : "0"` —— **这就是 P1-33-C 的「指针开关」落地物**，与 `h0` 一致（一个布尔 → opacity 0/1）。

### 3.2.1 两份文档矛盾的裁定

| 说法 | 出处 | 判定 |
| --- | --- | --- |
| `pointer` 竖向 81×324 | `verify-round2.md §56` | **成立**：81×324 是**设备像素**，= 23×94 CSS px × dpr≈3.5；源图 46×188 |
| 水平轴红蓝双三角 | `measurements.md` | **与 `pointer.png` 不是同一个东西**；本轮未在 `pointer` 通路上找到任何水平双三角。**该说法目前无出处支撑，标「未取证」** |

⇒ 结论：**「指针」只用 `pointer.png`（竖向 23×94）**。若 `measurements.md` 那条确有所指，需给出截图/dump 锚点另行取证；**在给出之前不许按「水平红蓝双三角」实现**。

## 3.3 当前位置符号 —— **未取证**

在 `index.946c1624e0.bundle.js` 内检索 `userLocation / locationMarker / myLocation / locate / geolocate / setPosition / location_point / gps` **全部 0 命中**；唯一 `circle-*` 命中是绘制工具的顶点样式（`{"circle-radius":3,"circle-color":"#fff",...}`），与定位无关。

⇒ 「当前位置符号」**不在原版地图 web 层绘制**。可能由原生覆盖层或另一张 drawable 承担，本轮**未定位到**。
⇒ 已排除的检索面：`decoded/res` 全量文件名（`ic_loc* / ic_my* / ic_position* / ic_center* / ic_current* / ic_gps*` 仅命中 `ic_center_cross`、`ic_gps_signal_1..3`、`svg_topic_center_cross`，均为信号强度/十字素材，**未证明**是当前位置符号）。
**结论：未取证。** 下一步可查原生覆盖层（`MainActivity` 的定位回调 → 是否有 overlay View）。**在拿到之前不许自画。**

---

# 第 7 条 罗盘贴地 —— **Canvas 2D 画布 + MapLibre canvas source + `coordinates` 贴到地面**

## 7.1 原版机制（逐字，`index.946c1624e0.bundle.js`）

初始化（@41502 起）：

```js
init: () => {
    if (!this.map.getSource("compass")) {
        this.map.addSource("compass", {
            type: "canvas",
            canvas: this.canvas,          // ← 2D 画布
            animate: false,
            coordinates: this.getCoordinates()   // ← 四角经纬度
        });
        this.map.addLayer({
            id: "compass_ground",
            type: "raster",
            source: "compass",
            layout: { visibility: "none" },
            paint: { "raster-resampling": "linear" }
        });
    }
    this.update();
}
```

画布构造（@42488 附近）：

```js
this.scale = 2;
let r = this.size = Math.floor(2 * window.screen.width),
    i = this.dpr  = Math.min(Math.floor(window.devicePixelRatio), 2),
    o = this.canvas = document.createElement("canvas");
o.width = r * i; o.height = r * i;
o.style.width = `${r}px`; o.style.height = `${r}px`;
this.ctx = o.getContext("2d");
this.ctx.scale(i, i);
this.init();
```

可见性与坐标（`setVisible`）：

```js
setVisible(e) {
    this.map.getLayer("compass_ground") &&
        (this.map.setLayoutProperty("compass_ground", "visibility", e ? "visible" : "none"),
         this.map.getSource("compass").setCoordinates(this.getCoordinates()));
}
```

地面开关（`setGround`）：

```js
setGround: e => { this._isGround !== e && (this._isGround = e, this.isLock || this.update()) }
```

`getCoordinates()` 返回四角经纬度：

```js
[ e.getNorthWest().toArray(), e.getNorthEast().toArray(),
  e.getSouthEast().toArray(), e.getSouthWest().toArray() ]
```

## 7.2 结论

原版「贴地」= **把盘面画在 2D `<canvas>` 上 → 作为 MapLibre `canvas` 类型 source → `raster` 图层 `compass_ground` 用 `coordinates`（四角经纬度）投影铺到地面**。

- **不是** GLSL 着色器
- **不是** 相机俯仰/压低机位
- **是** 贴图投影（canvas source + coordinates 四边形映射）
- 开关走 mode 位 `8`（`8 & mode → setGround`），与「锁定 16」「跟随 1」并列

## 7.3 对我方三条通道的裁决（回应审地图/客户端的分歧）

| 通道 | 原版是否用它做贴地 | 依据 |
| --- | --- | --- |
| 原生 `CompassView`（Canvas 2D，无 Matrix/Camera 透视） | **不是** | 原版贴地在 web 层 `compass_ground` 图层，不在原生 View |
| 地图 WebGL `drape`（DEM 贴图） | **不是** | 那条只作用于地形外观 |
| **MapLibre canvas source + coordinates** | **是** | 7.1 逐字 |

⇒ 所以我方现在 `toggleGround()`（推 `pitch=60°` + 机位距离压到 35%）**与原版机制毫无关系**，第 7 条判**我方未实现**（不是「不可取证」）。
⇒ 实现路径：我方 web 层若已有 MapLibre（`map.addSource({type:"canvas"})`），可直接照抄；若没有 canvas source 能力，需等价替代（例如 `image` source + `coordinates`），但**必须在文档标明「等价替代」而非 1:1**。

## 7.4 原版有没有「贴地」这颗按钮 —— **有，但名字不是「贴地」**

- 原生左列 6 项（`MainActivity.java:591`）：`截屏 / 等高线 / 罗盘复位 / 罗盘锁定 / 读盘 / 贴地` —— 「贴地」是我方**自拟文案**。
- 原版对应的开关是 **`setGround`（mode 位 8）**，在 CompassTools 覆盖层（`CompassToolsKt`）里，**不在左列**。
- ⇒ 「原版有无这颗按钮」的答案是：**原版有「地面/贴地」这个功能开关，但不在我方所放的位置，且不在左列 6 项里**。我方左列那颗「贴地」是自造入口。
- ⇒ 处理建议：**不要删**（功能原版有），**但要改成原版语义**（走 `setGround`/mode 位 8 + canvas source 投影），而不是现在的「推 pitch + 压机位」。

---

## 6.7 补证：「重新打开跟随」是哪个控件（总监追问的第二句）

| 项 | 结论 | 证据 |
| --- | --- | --- |
| 跟随的 setter | `N(Z)`（字段 `l`，getter `A()`） | `g.smali:996` |
| 跟随的 toggle 方法 | `g.p0()` = `N(!A())` + `M()`（重推 mode 位） | `g.smali:2118-2137` |
| **用户可见控件 = Compose 左列的「跟随」** | `CompassToolsKt;->o1(g)`：先 `g.p0()`，再 `track("compass_rotate", …)` | `CompassToolsKt.smali:14725-14750` |
| 「跟随」文案与状态 | 同一 handler 的埋点构造器 `p1(g, JSONObject)` 里 `put("type","跟随")`、`put("state", g.A())` | `CompassToolsKt.smali:14776-14798` |

⇒ **复位关掉跟随之后，用户用「跟随」开关重新打开**（不是「罗盘锁定」）。

对照：「罗盘锁定」是**另一个**控件，toggle 不同：

| 控件 | handler | toggle | 字段 | mode 位 | web |
| --- | --- | --- | --- | --- | --- |
| 罗盘复位 | `CompassToolsKt;->I0(g)` @:684 | `g.I()` @:780 | 一串（见 6.2） | 重算 | `resetCompass` + `resetNorth` |
| **罗盘锁定** | `CompassToolsKt;->J0(g)` @:725（埋点 `name="罗盘锁定"`） | `g.q0()` @:2165 = `P(!B())` + `M()` | `e` / `B()` | `0x10` | `setLock` |
| **跟随** | `CompassToolsKt;->o1(g)` @:14725（埋点 `type="跟随"`） | `g.p0()` @:2118 = `N(!A())` + `M()` | `l` / `A()` | `0x1` | `map.setBearing(r)` |
| 指针开关 | `CompassToolsKt;->Q0(vm)` @:1116 | `b3(!M0())` | `MainViewModel.h0` | — | `setPointerVisible` |

⇒ 四个控件各自独立，**没有一个是「复位」的替身**。`I()` 里两句 `P(false)` + `N(false)` 表示复位会把**锁定与跟随同时关掉**；重开走各自开关。

## 6.8 补证：顶部 8px 红三角与「指针开关」**不是同一件事**

| 对象 | 是什么 | 由谁控制 | 证据 |
| --- | --- | --- | --- |
| 8px 红三角 | `.compass-line-marker`，**十字线的一部分**（`.compass-line` 的子元素） | 随十字线一起显示，**没有独立开关** | `index.946c1624e0.bundle.js` @200694 CSS |
| 指针 | `<img class="pointer" src="pointer.png">`，竖向 23×94 | `setPointerVisible({show})` → `pointer.style.opacity` | JS @52142 / @49733 |

指针的显隐逻辑（`CompassToolsKt$CompassTools$3$1` = `CompassToolsKt$a`，`LaunchedEffect`）：

```kotlin
val show = if (controller.q() && !controller.p()) false else viewModel.M0()
MapWebApiKt.setPointerVisible(controller, show)
```

逐字：`g.q()` 为真且 `g.p()` 为假 → **强制隐藏**；否则 `show = MainViewModel.M0()`（即 `h0`）。
（`g.q()` / `g.p()` 即 P1-33-C 里三态开关「地图 / 实景」的两个布尔。）

⇒ **我方把「顶部红三角」当成 `showPointer` 的唯一可见效果，是载体搞错了**：红三角属于十字线（第 1 条），指针开关应当控 `pointer.png` 那张竖向指针图。
⇒ 所以「删掉红三角 → 指针开关变空操作」这个担心，正确解法**不是**留着红三角，而是**把指针开关改成控竖向指针图**（原版载体）。删红三角与接指针图应同批做。

## 7.5 `getCoordinates()` 四角到底怎么算（A 路线第 4 步前置，已取证）

逐字（`index.946c1624e0.bundle.js` @41153，赋值式 `p(this,"getCoordinates", …)`）：

```js
p(this, "getCoordinates", () => {
    let e = this.map
        .unproject([window.screen.width / 2, window.screen.height / 2])
        .toBounds(Math.floor(window.screen.width / this.map.transform.pixelsPerMeter));
    return [ e.getNorthWest().toArray(), e.getNorthEast().toArray(),
             e.getSouthEast().toArray(), e.getSouthWest().toArray() ];
});
```

拆开：

1. **中心点** = `map.unproject([screen.width/2, screen.height/2])` —— **屏幕中心**映射回经纬度（`window.screen.width/height`，物理屏幕 CSS 尺寸，不是 `innerWidth`）。
2. **半径（米）** = `Math.floor(window.screen.width / map.transform.pixelsPerMeter)`
   - `pixelsPerMeter` 是 MapLibre 内部量（当前 zoom + 纬度下的「每米多少像素」），除完把**像素换成米**。
   - ⇒ 半径 = **屏幕宽度换算成的米数**（不是一个固定米数，**随缩放变**）。
3. **bounds** = 中心 `.toBounds(该米数)` —— 以中心为准的正方形；**`toBounds` 的参数是「半边长」**，所以 **边长 = 2 × 该米数**（`radius` 语义见 §7.5.4，已用原版 `mapbox-gl.js` 定死）。
4. 输出顺序 **[NW, NE, SE, SW]**（左上→右上→右下→左下），正是 MapLibre `canvas`/`image` source 要求的 `coordinates` 顺序。

### 7.5.1 结论（回答审地图的「未取证」）

- **不是「屏幕四角 `unproject`」**（那会得到梯形，随 pitch 变形且四角不等距）；
- **是「屏幕中心 `unproject` + 半径 = 屏宽换算米数的正方形」**（轴对齐、正方形、随 zoom 缩放）；
- 效果：**地面边长 = 2 × 屏宽米数 ⇒ 屏幕上约两个屏宽见方**（本机 728 CSS px），盘面实物尺寸随缩放变化、屏幕视觉大小基本恒定。这与「罗盘浮层不该随地图缩放变大变小」的直觉一致。
- 依赖 MapLibre 内部 API `map.transform.pixelsPerMeter`；我方若用 MapLibre 同版本可直接照抄，**若版本不同该字段可能不可用，需等价换算**（`pixelsPerMeter` 可由 `transform.worldSize / (2π·R·cos(lat)) · 512` 一类关系推出，属实现细节，我方落地时核对一次）。

### 7.5.2 给 A 路线第 4 步的验收口径

`coordinates` 的正确性用两条可判的量验：
1. **贴地四边形边长（铺回屏幕的 CSS px 当量）== 2 × `floor(screen.width / pixelsPerMeter)` 对应屏幕像素 == canvas 元素 CSS 宽**（本机 728）。三者不等，说明 `radius` 的半边长语义用错了一倍；
2. **pitch 0→60° 时该边长对应关系基本不变**（轴对齐正方形在透视下仍是四边形，但边长对应稳定）。若得到梯形 / 四角不等距，说明误用了「屏幕四角 `unproject`」。

### 7.5.3 单位口径：`window.screen.width` 是 **CSS px**，不是设备 px（直接影响画布尺寸与贴图大小）

原版 webapp `index.html` 的 viewport 逐字：

```html
<meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no">
```

`width=device-width` + `initial-scale=1` ⇒ CSS 视口宽 = 设备宽 ÷ dpr。Android WebView 里 `window.screen.width` 返回的是**CSS px**。

本机（1228×2700，density 3.375）：

| 量 | 按 CSS px 口径（**正确**） | 若误把 1228 当 CSS px（**错误**） |
| --- | --- | --- |
| `window.screen.width` | **364**（1228/3.375） | 1228 |
| `size = floor(2 × screen.width)` | **728** CSS px | 2456 |
| `dpr = min(floor(devicePixelRatio), 2)` | `floor(3.375)=3 → min(3,2)=` **2** | 同 2 |
| canvas backing = `size × dpr` | **1456 × 1456** px，约 **8.5 MB**（4 B/px） | 4912 × 4912，约 96 MB |
| canvas CSS 显示尺寸 | **728 px**（= 2 × 屏宽） | 2456 px |
| 地面贴图边长（`radius` 见 §7.5 / §7.5.4） | 半径 `floor(364 / pixelsPerMeter)` 米 ⇒ **边长 = 2 × 364 = 728 CSS px 当量** | 同样按 364 算半径 |

⇒ **修正一处待办数值**：`docs/fidelity/m6/map-canvas-migration.md` 里按「`screen.width=1228` ⇒ canvas 2456×2456、24.1 MB」推算的那组数**偏大**。按 CSS px 口径应为 **728 CSS px / backing 1456 / 约 8.5 MB**。
（2456 是「把 `size` 当设备 px」得出的，不是原版值；原版 `size` 是 CSS px。）

⇒ **贴地范围口径（最终，已按 §7.5.4 的半边长语义改正）**：
- 半径 = **屏宽那一截像素 ÷ pixelsPerMeter**（米），不是盘面半径；
- **地面贴图边长 = 2 × 屏宽 = 728 CSS px 当量**；对比盘面直径 922 设备 px = 273 CSS px，约 **2.67 倍**；
- 它**既不等于**「屏幕四角 unproject」得到的视野四边形（那个随 pitch 变形且形状不对），**也不等于**盘面半径换算（那只有约 272 CSS px 当量，偏小 2.7 倍）；
- **也不等于「一个屏宽」**——那是漏掉 §7.5.4 那个 ×2 的错值（364），面积会小 4 倍。

### 7.5.4 `toBounds(radius)` 的 radius 是**半边长**（修订 §7.5 的措辞）

原版自带 `decoded/assets/webapp/lib/mapbox-gl.js` @155844 逐字（`LngLat.prototype.toBounds`）：

```js
toBounds() {
    var r, i = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : 0,   // bearing
        a = 360 * (arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : 0) / 0x2637f09,
        n = a / Math.cos(Math.PI / 180 * this.lat),
        s = [{dx:-n, dy:-a}, {dx:n, dy:-a}, {dx:n, dy:a}, {dx:-n, dy:a}],
        ...
}
```

- `0x2637f09` = **40075017** = 地球周长（米）⇒ `a = 360·radius/40075017` 度 ≈ `radius/111319.5` 度 ≈ **radius 米**（纬度向）。
- 四角 = 中心 **±** `n`（经度）/ **±** `a`（纬度）—— **是加减，不是乘**。

⇒ **`radius` 是半边长**。地面四边形 **边长 = 2 × radius = 2 × 屏宽米数**。
⇒ **§7.5 第 3 条已按本条改正**（现文为「`toBounds` 参数是半边长，边长 = 2×该米数」），§7.5.3 表格同步改为 728 / 2×。此处保留判定出处：原始版把 `radius` 当整边长的写法**是错的**，错值会让贴地面积小 4 倍。

### 7.5.5 一个自洽性验证（这条能证明整套口径对）

本机（CSS px 口径，见 §7.5.3）：

| 量 | 值 |
| --- | --- |
| `window.screen.width` | 364 CSS px |
| 地面半径 = `floor(364 / pixelsPerMeter)` 米 | 364 CSS px 当量 |
| 地面**边长** = 2 × 364 | **728 CSS px 当量** |
| 原版 canvas `size = floor(2 × screen.width)` | **728 CSS px** |
| canvas CSS 显示尺寸 = `size` | **728 CSS px** |

⇒ **地面边长（728 CSS px 当量）== canvas CSS 尺寸（728 CSS px）⇒ 画布与地面足迹 1:1，纹理不需要缩放。**
这不是巧合：`size = 2 × screen.width` 正是因为足迹边长是 `2 × 屏宽`；若漏掉 §7.5.4 那个 ×2，画布会被拉伸一倍（或压缩一半）。

**给实现方的两条判据**（比逐像素更容易先跑）：
1. `canvas 元素 CSS 宽 == 2 × floor(screen.width / pixelsPerMeter) 对应到屏幕的像素数`（即 728 CSS px）—— 不等就说明半径/边长用错了一倍；
2. 盘面绘制框屏宽 ÷ 728 ≈ 0.375（盘面 273 CSS px / 728）—— 偏离说明 canvas 内绘制尺度不对。

## 6.9 `q()` / `p()` 是什么（第 3 条指针三态的前置，已定）

> **本节曾出过一版错误结论并已改正**：上一版称「两个 setter 全 DEX 没有调用方 ⇒ `q()/p()` 恒定 ⇒ 指针恒隐藏」。错因是**检索时把 `ui/controller/g.smali` 整个文件排除了**，而调用方恰好就在这个文件里（`b()` @1309/1315、`o0()` @1959、`r0()` @2238）。下面是改正后的结论。

### 6.9.1 字段与初始值（已证）

| getter | 字段 | setter | **构造器初始值** | 证据 |
| --- | --- | --- | --- | --- |
| `q()` | `f` | `Z(Z)` @1204 | **TRUE** | `g.smali:245` `sget-object v3, Ljava/lang/Boolean;->TRUE` → `:248` `mutableStateOf` → `:254` `iput ... ->f:` |
| `p()` | `g` | `Y(Z)` @1185 | **FALSE** | `g.smali:197` `sget-object v0, Ljava/lang/Boolean;->FALSE` → `:257` `mutableStateOf(v0)` → `:263` `iput ... ->g:` |

### 6.9.2 谁会改它们：三个方法（全部已证）

| 方法 | 行为 | 埋点 | 证据 |
| --- | --- | --- | --- |
| `g.r0()` | **切换「地图模式」**：`Z(!q())` | `compass_mode_switch` / `mode` = **`地图模式`** | `g.smali:2212`，`Z(...)` @2238 |
| `g.o0()` | **切换「实景模式」**：`Y(!p())` | `compass_mode_switch` / `mode` = **`实景模式`**（`p()` 真时）/`地图模式`（假时） | `g.smali:1933`，`Y(...)` @1959 |
| `g.b()` | **归位到「地图模式」**：若 `!p() && q()` 直接返回；否则 `Y(false)` + `Z(true)` | `compass_mode_switch` / `mode` = **`地图模式`** | `g.smali:1280`，`Y` @1309 / `Z` @1315 |

⇒ **`q()` = 地图模式开关，`p()` = 实景模式开关**——P1-33-C 原来的映射（地图 `g.q()`、实景 `g.p()`）**是对的**，本轮不作废。

### 6.9.3 三种状态下的取值（总监要的那几个值）

| 状态 | `q()` | `p()` |
| --- | --- | --- |
| 启动默认（构造器） | **true** | **false** |
| **地图模式**（`b()` 归位后 / `r0()` 打开地图） | **true** | **false** |
| **实景模式**（`o0()` 打开实景） | 保持用户上次（`o0` 只翻 `p()`） | **true** |
| 地图模式关闭（`r0()` 再点一次） | **false** | 不变 |

### 6.9.4 指针显隐条件（按改正后的取值重算）

```kotlin
show = if (g.q() && !g.p()) false else viewModel.M0()
```

- **地图模式开、实景关**（`q=true, p=false`，即启动默认/纯地图）→ 条件成立 ⇒ **`pointer` 强制隐藏**；
- **地图模式关**（`q=false`）→ 条件不成立 ⇒ `pointer` 取 `M0()`（用户「指针开关」说了算）；
- **实景模式开**（`p=true`）→ 条件不成立 ⇒ 同上取 `M0()`。

⇒ **语义自洽**：`pointer.png` 是罗盘盘面上的指针，**纯地图模式下不该出现**；只有在「非地图模式」（罗盘叠加态）或「实景态」下才由用户的「指针开关」控制。
⇒ 因此客户端先前担心的「套上去永久隐藏」**不成立**——它只在纯地图模式隐藏，这正是原版设计。

### 6.9.5 给实现方的口径

1. 我方「地图」开关必须与 `q()` 同义：**「地图模式 / 罗盘模式」切换**，不是「底图显隐」。若我方那颗开关语义是底图显隐，**不能直接映射**，需先对齐语义（这条要写进台账）。
2. 指针显隐照抄：`show = !(mapMode && !arMode) ? userPointerSwitch : false`。
3. `pointer` 载体是竖向 `pointer.png`（23×94 CSS），不是红三角（红三角属十字线，见 §1.2）。

## 6.10 旁证：`q()`/`p()` 在其它两处的用法与「模式开关」解释一致

| 位置 | 代码 | 与「q=地图模式、p=实景模式」是否自洽 |
| --- | --- | --- |
| `CompassToolsKt;->E0(...)` @:318（截屏） | `if (p()) 走相机截图（vm.z3）; else if (!q()) 走 layer 截图; else 走 WebView 截图` | **自洽**：p 真→拍相机；p 假 & q 真（地图态）→ 截 WebView；p 假 & q 假→截 Compose 层 |
| `CompassToolsKt.smali` @:4670-4696（设点/取坐标） | `if (!p() && !q()) heading += 180` | **自洽**：只有「既无底图也非相机」时才做 180° 翻转；正常地图态不翻 |

⇒ 两处旁证与 §6.9.2 的恒定值一致，未见与「用户开关」相符的行为。

# 未取证清单（逐条给原因）

| 项 | 状态 | 原因 |
| --- | --- | --- |
| 第 3 条「当前位置符号」的 drawable / 绘制位置 | **未取证** | web 层全区检索 0 命中；res 内候选素材未证明是该符号。需查原生覆盖层，本轮未做 |
| 第 3 条「水平红蓝双三角」指针 | **未取证** | 在 `pointer` 通路上无任何水平双三角证据；`pointer.png` 明确是竖向 23×94 |
| 第 8 条 3D 按钮点击后的预期行为（原始需求） | 部分 | 见下方交接：原生 `case "3D"` → `js("toggle3D()")` 链路已由审地图核实；原版点击前后的视角参数本轮未取 |
| 第 7 条 canvas source 在我方 web 层是否可用 | 未取证 | 属实现环境问题，需 @LC地图 确认我方是否引入 MapLibre |

---

# 交接（给实现方，全部有出处）

1. **第 6 条**：新增判据，不推翻 P1-33-C。复位两半：盘面（`N(false)`+rotation/透明度/缩放/偏移/磁偏角归位）+ 地图（等价 `map.resetNorth()`）。**不复位 center/zoom**。**必须把跟随置 false**。
2. **第 1 条**：色 `#FF0000` 不透明、线宽 2 CSS px（按 dpr 换算）、横 150%/竖 100% 覆盖全屏、不随 bearing 转（锁定态才随）。中心另有 8px 红色三角。
3. **第 3 条**：屏幕中心符号 = CSS 8px 红三角（非 drawable）；指针 = `pointer.png` 46×188 源图 / 23×94 显示，竖向；**当前位置符号未取证，不许自画**。
4. **第 7 条**：贴地 = canvas source + `coordinates`；我方现状（推 pitch + 压机位）与原版无关，判**未实现**；左列「贴地」按钮不要删，改语义。

---

# 取证进度

| 步骤 | 结果 | 时间 |
| --- | --- | --- |
| 第 6 条 DEX（视图树/handler/状态位） | 完成 | 01:02–01:10 |
| 第 6 条 web（`reset()` / `resetNorth()` / mode 位） | 完成 | 01:20–01:30 |
| 第 1 条 CSS（色值/线宽/层/旋转） | 完成 | 01:22–01:26 |
| 第 3 条 指针（`pointer.png` / CSS 尺寸） | 完成 | 01:24–01:28 |
| 第 3 条 当前位置符号 | 未取证（web 层 0 命中） | 01:28–01:33 |
| 第 7 条 贴地（canvas source + coordinates） | 完成 | 01:29–01:34 |
| 文档落盘 | 完成 | 01:35 |

时间戳：开工 `01:02` / 收工 `01:35` / 返工 0。
