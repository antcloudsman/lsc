# P1-33-C 取证：右列 4 个开关的状态语义（原版 1.6.1）

来源：`mem_smali`（方法体真实）＋ 原版 APK `swss.apk`（sha256 `2fd1fc15…`）。

## 1. 形态：是开关（checked 由状态驱动），不是「点一下执行一次」

4 枚都用同一个开关组件：

```
Lspace/xinzhi/globe/ui/tools/CompassToolsKt.smali
  L10326  地图       I3;->c(String, boolean, Modifier, Function0, ...)
  L10480  实景       同上
  L10542  坐南朝北   同上（另读 controller.g().x()）
  L10791  指针开关   同上
```

`I3;->c` 的第二个参数是 **checked 布尔**，取自 controller 的 Compose 状态：

| 开关 | 状态来源 | 锚点 |
|---|---|---|
| 地图 / 实景 | `controller.g().p()`、`controller.g().q()` | `ui/controller/g.smali:2093`（p 读字段 `g`）、`:2140`（q 读字段 `f`） |
| 坐南朝北 | `controller.g().x()` | `ui/controller/g.smali:2433`（x 读字段 `h`） |
| 指针开关 | 同类状态位 | 点击 lambda `J` → `CompassToolsKt;->i(...)` |

结论：点击只改状态，渲染时按 checked 出两态配色 → **切换型开关**，不是一次性动作。

## 2. 地图与实景是「模式互斥」，不是两个独立开关

埋点给出三态（`manager/TAHelper;->track`，事件名 `compass_mode_switch`，参数名 `mode`）：

- `ui/controller/g.smali:1330` → `"地图模式"`
- `ui/controller/g.smali:2007` → `"地图模式"` / `"实景模式"`
- `ui/controller/g.smali:2286` → `"地图模式"` / `"无地图模式"`

即：地图开 = **地图模式**，实景开 = **实景模式**，都不开 = **无地图模式**。
所以这两枚是同一枚举的三个取值，**实现时必须互斥**（开一个自动关另一个），不能各存一个 boolean 各自翻转。

## 3. 默认态：代码常量只确认「有且只有一个为 TRUE」

`ui/controller/g.smali` 构造函数里这些 `MutableState` 的初值：

```
sget-object v0, Ljava/lang/Boolean;->FALSE;   ← 多数状态
...
sget-object v3, Ljava/lang/Boolean;->TRUE;    ← 仅一个状态初值 TRUE
mutableStateOf(v3) → iput-object ...->f
iput-object v4 ...->g   (FALSE)
iput-object v4 ...->h   (FALSE)
```

也就是说初始只有一个状态为真；**具体哪一枚在屏幕上显示「开」，字段 f/g/h 与四个标签的对应关系还需真机确认**，暂记「未确认」。

## 4. 持久化：未确认（但代码里没有存储调用）

- `ui/controller/g.smali` 内**没有任何** `SharedPreferences` / `MMKV` / `putBoolean` / `getBoolean` 调用（全文件检索 0 命中）
- `compass_mode_switch` 是 **TA 埋点事件名**，不是存储键
- 其余命名空间里也没有 `south`/`north`/`pointer`/`reality` 之类偏好键

所以状态看起来是**内存态**（重启回默认）。「重启后是否保持」需真机验证；当前原版卡在引导页（`GuideActivity` 不接受合成输入），**暂不可得**。

## 5. 给实现的落点（下一轮）

1. 三态枚举 `MAP | REALITY | NONE`，地图/实景两枚映射到枚举切换，互斥；
2. 坐南朝北、指针开关各一个布尔，点击切换；
3. 两态配色按 `LC审核验 §11.1`：开 = 蓝底 `(50,90,182)` + 白轨 + 蓝钮靠右；关 = 深色底 + 灰轨 `(142,142,142)` + 白钮靠左；
4. 持久化先按「不持久」实现（与代码现状一致），若产品要求持久化需另立条目；
5. 「点一下是切状态还是执行一次」——**切状态**，本条已由 `I3;->c` 的 checked 参数与状态来源证实。

## 6. `f/g/h` ↔ 标签映射：已解开（2026-11-07T04:52 复核）

之前未解的矛盾是「controller `g.<init>` 只有一个字段初值 TRUE，但截图默认两枚开」。
原因是**指针开关的状态根本不在 controller 里**，而在 `MainViewModel`。

| 开关 | checked 表达式 | 状态字段/初值 | 点击行为 | 锚点 |
|---|---|---|---|---|
| 地图 | `controller.g().q()` | `g.f` = **TRUE** → 默认开 | 模式切换（见 §2） | `CompassToolsKt.smali` L10264（`:cond_d34` 分支 L10342 同） |
| 实景 | `controller.g().p()` | `g.g` = **FALSE** → 默认关 | 模式切换（见 §2） | L10375（`move-result v9` 前一条 `invoke p()Z`） |
| 坐南朝北 | `controller.g().x()` | `g.h` = **FALSE** → 默认关 | 切换（x 由字段 h 驱动） | L10522 |
| 指针开关 | `MainViewModel.M0()` | `MainViewModel.h0` = **TRUE** → 默认开 | **纯切换**：`Q0` 里 `xor-int/lit8 v0, v0, 0x1` → `b3(Z)` 写回 | L10770（`invoke M0()Z`）；`MainViewModel.smali:5951`（M0 读 h0）、`:1219`（h0 初始化 TRUE）；`CompassToolsKt.smali:3270`（`i(vm)` → `Q0`） |

四枚全部与截图默认态一致（地图=开、指针开关=开、实景=关、坐南朝北=关），**映射不再是未确认项**。

补充：指针开关的切换是显式取反（`xor 1`）后写回，**不是「存反义字段」**——原文担心「可能存的是指针隐藏」，实测字节码是标准 toggle。
