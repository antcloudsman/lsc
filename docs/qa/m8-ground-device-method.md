# M8 贴地真机取数：方法坑与可信度边界

> 本文只记「怎么取数才不会取错」，不记判据结论。判据以
> `docs/fidelity/m8/ground-drape-acceptance.md` 为准。

## 1 致命坑：`--es value` 里的单引号会被 shell 吃掉（会影响判据结论）

调试通道是 shell 命令，JS 里出现的**单引号**会与 PowerShell/shell 的外层单引号冲突。

### 1.1 注入失效（表现像「注入没实现」）

```powershell
# 错：内层 'glinit' 的引号被吃掉
adb -s $d shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '__LC3D.__forceGroundFail('glinit')'"
```
落到 JS 引擎的是 `__LC3D.__forceGroundFail(glinit)` → `ReferenceError: glinit is not defined`
→ 回调返回 `null` → **`groundFailInject` 根本没被设置**，`diag` 仍是 `drawn`。

现象与「实现不支持注入」完全一样，极易误判实现方没做。判据必须要求：
**注入后先确认 `diag` 真的变成 `fail:<kind>`；没变就报「注入未生效（工具问题）」，不许当成「注入无效（实现问题）」。**

正确写法（不含任何引号）：

```powershell
# glinit = 103,108,105,110,105,116
adb -s $d shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '__LC3D.__forceGroundFail(String.fromCharCode(103,108,105,110,105,116))'"
adb -s $d shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '__LC3D.__forceGroundFail(null)'"
```

### 1.2 读取也会被同一条坑毁掉

```powershell
# 错：'offlineBar' 的引号同样会被吃
... "var e=document.getElementById('offlineBar');"
```
此时 `e` 不一定是「元素不存在」，整个表达式可能抛错或取到意外值。
所以我 2026-11-09 那轮读到的 `bar=null` **不能当作「地图态没有提示条」的证据**——这是我方法侧的问题，不是实现结论。

正确写法：

```powershell
# offlineBar = String.fromCharCode(111,102,102,108,105,110,101,66,97,114)
... "var e=document.getElementById(String.fromCharCode(111,102,102,108,105,110,101,66,97,114));"
```
或整段 JS 用一个不含单引号的函数体（模板串/变量拼接），只要能保证「落到引擎的字符串与预期逐字相同」。

### 1.3 通用做法

1. **任何 JS 一律不用单引号**：字符串常量走 `String.fromCharCode(...)`，或从页面已有的 `__LC3D` 方法取值；
2. 每次注入/读取后，先回读一个**已知会变的字段**确认通道生效（如注入后 `diag` 必须变化）；
3. 证据里同时留：命令原文 + 返回原文。只有返回原文能证明「跑的是这条」。

## 2 `offlineBar` 是懒创建的：先触发、再读

`tileOfflineBanner()`（`map.html:524`）**第一次被调用时才 `createElement` 并挂到 `document.body`**。
触发点：`tileOfflineMiss/Hit`（瓦片缓存路径）、`groundStateSet`（状态变化且去重键变化时才刷）。

推论：
- **「元素不存在」不等于「没有提示」**：可能只是还没走到创建点；
- `groundStateSet` 只在 `(ok|diag|reason)` 去重键**变化**时刷提示，重复注入同一失败不会重建；
- 取证顺序应是：制造状态 → 等一帧 → **再读一次**（不要只读一次）→ 截图。

## 3 「两源读数不一致」是假象：同一变量 + 跨次读取

2026-11-09 我先报过「`getState()` 与 `getGroundDebug()` 同一时刻不一致」，**这个结论是错的，已撤回**。

### 3.1 代码事实：只有一个来源

```
map.html:3127  var groundCanvasOk = false;          // 定义（唯一）
map.html:3135  groundCanvasOk = ok;                 // 赋值（唯一一处）
map.html:4137  getState()       → groundCanvasOk    // 读
map.html:4195  getGroundDebug() → groundCanvasOk    // 读
map.html:544   banner 分支      → groundCanvasOk    // 读
```
**同一次 JS 求值内不可能出现两个值。** 我看到的 `false/pending` 与 `true/drawn` 是两条 adb 广播之间隔了一帧
（`setGround` 先写 `pending/false`，下一帧 `drawGroundCompass` 写成 `drawn/true`）。两个数都对，只是不在同一时刻。

### 3.2 判法：必须在同一次求值里一起读，并用 `seq` 区分

地图侧为此加了 `groundSeq`（状态真变化时 +1）与 `groundChangedAt`，两个读函数都暴露：

```js
JSON.stringify({
  s:{ok:getState().groundCanvasOk, diag:getState().groundDiag, seq:getState().groundSeq},
  d:(function(){var g=__LC3D.getGroundDebug();return {ok:g.canvasOk, diag:g.diag, seq:g.seq};})()
})
```
判读规则（**写进判据**）：
```
两边 seq 相同、ok/diag 不同  ⇒ 真 bug（同一状态两个说法）
两边 seq 不同                ⇒ 只是跨次读取，不许判实现矛盾
```
**通用教训**：凡「两个读函数都返回同一事实」，判据要么在一次求值里取全部值，要么带版本号/序号；
靠两次调用各自取一个值再比较，得到的差异没有意义。这与「取样间隔」导致的假红是同一族。

### 3.3 顺带纠正一条我自己的误判

我曾据一张截图断言「贴地成功时画面仍是正圆」。复核后**不成立**：

| 截图 | 当帧状态 | 画面 |
|---|---|---|
| `compass-ground-on.png` | `diag=pending`、`canvasOk=false` | 屏幕空间正圆（原生 fail-safe，正确） |
| `g5-compass-reset.png` | `diag=drawn`、`canvasOk=true` | **透视椭圆铺在地形上**（刻度环 + 二十四山 + 红色十字均正确） |

**贴地图层是通的**；我那张「正圆」是回退帧。教训：截图必须与**同一时刻的读数**配对，
不能拿 A 时刻的图配 B 时刻的读数——这次就是我拿 `pending` 的图去讲「成功态」。


## 4 复现一次的最小命令序列（全部无引号）

```powershell
$d='L2E0222531014833'
function JS($v){ adb -s $d logcat -c; adb -s $d shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$v'" | Out-Null; Start-Sleep -Milliseconds 900; (adb -s $d logcat -d -s LCJS | Select-String 'map ->' | Select-Object -Last 1).Line }

# 1) 确认通道（返回 "canvas"）
JS "__LC3D.setGroundRenderer(1)"
# 2) 读状态（无引号：对象键名本身不需要引号，值全部来自页面变量）
JS "JSON.stringify((function(){var s=getState();var g=__LC3D.getGroundDebug();return {ui:s.uiMode,ground:s.ground,stateOk:s.groundCanvasOk,stateDiag:s.groundDiag,dbgDiag:g.diag,dbgOk:g.canvasOk,sab:s.tileOffline.safeAreaBottom};})())"
# 3) 注入失败（fromCharCode，见 §1.1）
JS "__LC3D.__forceGroundFail(String.fromCharCode(103,108,105,110,105,116))"
# 4) 回读确认注入真的生效（diag 必须变 fail:glinit）
JS "JSON.stringify((function(){var s=getState();return {ok:s.groundCanvasOk,diag:s.groundDiag};})())"
# 5) 复位后跨帧读两次
JS "__LC3D.__forceGroundFail(null)"; Start-Sleep -Milliseconds 700
JS "JSON.stringify((function(){var s=getState();return {ok:s.groundCanvasOk,diag:s.groundDiag};})())"
```

## 5 我这轮读数的可信度边界（如实标注）

| 结论 | 可信度 | 依据 |
|---|---|---|
| 罗盘态注入后原生盘面可见（正圆 + 30.0°） | **可信** | 截图目视 + 状态读数一致 |
| 罗盘态 `safeAreaBottom=32`（工具条可见） | **可信** | 纯数值，无引号参与 |
| 罗盘态提示条被工具条压住 | **可信** | 截图目视（字叠字） |
| 地图态 `bar=null` | **不可信** | 读取语句含单引号，受 §1.2 影响；且元素懒创建 |
| 「贴地成功时仍是正圆」 | **已撤回** | 那是 `pending` 回退帧，见 §3.3 |
| 「两源读数不一致」 | **已撤回** | 同一变量 + 跨次读取，见 §3 |
| 贴地图层确实上屏（透视椭圆） | **可信** | `g5-compass-reset.png`，与 `drawn/true` 同帧 |
| G2 三区分治 | **未测** | 时间不足；canvas 已确认上屏，下轮可测 |
