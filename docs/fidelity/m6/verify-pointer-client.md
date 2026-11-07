# M6 第 3 条 · 指针（`pointer.png`）门控独立复验

- 复验人：LC审客户端（独立复验，按 `m6-plan` v0.4 的「待 @LC审客户端 独立复验」）
- 基线：`develop` **`a3a502a`**（`git rev-parse origin/develop` 实测）
- 对象：LC客户端 在群里的结论「按更正后的条件复核，`0e40621` 在可达状态下等价，不需要改代码」
- 方法：`git show origin/develop:.../MainActivity.java` 逐行核对，未上机、未改源码
- 结论：**映射表本身对，但「不需要改代码」不成立**——门控不存在，「等价」目前不成立；LC审地图指出的两处漏洞我独立确认，且还有第三处（切开关后不刷新）

---

## 0. 先认定对照条件（取自 `m6/reverse-stage1.md`，develop 版本）

```
show = if (controller.q() && !controller.p()) false else viewModel.M0()
```
- `q()` / `p()` 是「地图 / 实景」两个布尔，`M0()` 即 `h0` 指针开关（`m6/reverse-stage1.md:385-390`）。
- 逆向已撤回「全 DEX 零调用点」那一版：调用方 `r0()`@2212、`o0()`@1933、`b()`@1280 都在 `g.smali` 内（`m6-plan` v0.4 §1.4）。
- 我方对应状态：`compassMapMode` 三态互斥（`MainActivity.java:855-858`）、`pointerOn` 默认 true（`:860`）。

## 1. 映射表复验（LC客户端的三行表）

| 我方状态 | (q,p) | 原版结果 | 我方应有 | 判定 |
|---|---|---|---|---|
| `MAP_MODE_MAP`（地图开/实景关） | (true,false) | **隐藏**（`q && !p`） | 隐藏 | 表对 |
| `MAP_MODE_REALITY`（实景开） | p=true | `M0()` 开关值 | 跟 `pointerOn` | 表对 |
| `MAP_MODE_NONE`（两个都关） | q=false | `M0()` 开关值 | 跟 `pointerOn` | 表对 |

- 互斥模型 vs 原版两个独立布尔：原版可达 `q=true && p=true`（点「实景」不翻转 `q`），我方表达不出；但该状态在门控里走 `p=true` 分支、与 `q` 无关，**可达状态上行为等价**——这条 LC客户端 说对了，可保留。
- **但等价的前提是门控存在**。下面三处证明它不存在。

## 2. 三处不成立（均在 `a3a502a`）

### D1 门控缺 `compassMapMode`，默认 MAP 态显示 vs 原版隐藏【已证实】

```
1590: if (leftColumn != null) leftColumn.setVisibility(on ? View.GONE : View.VISIBLE);
1591: // R2-005：中心指针只在罗盘态显示；「指针开关」关掉时也一起隐（与既有 setShowPointer 同步）
1592: if (pointerView != null) pointerView.setVisibility(
1593:         on && (compassView == null || compassView.isShowPointer()) ? View.VISIBLE : View.GONE);
```
- 只有两个输入：`on`（=`compassMode`）与 `isShowPointer()`；`compassMapMode` **零参与**。
- 默认态（`compassMapMode=MAP_MODE_MAP`、`pointerOn=true`）：原版 `q&&!p` 为真 → 隐藏；我方 `on && true` → **显示**。方向正好相反。
- 所以 LC客户端 的「纯地图模式下它整个不在屏上」讲的是 `compassMode=false`；原版要隐藏的是「在罗盘浮层里、地图=开」——两个不同条件，不能互相代替。

### D2 隐藏态不隐指针，且隐藏态点开关会漏出来【已证实】

```
1699: private void applyCompassHiddenVisuals() {
1700:     boolean showCompass = compassMode && !compassHidden;
1701:     if (compassView != null) ...
1703:     if (crosshairView != null) ...
1704-1706: 左右列 / 底栏 ...
1711:     if (coordView != null) ...
```
- 函数体内**没有 `pointerView`**；全仓设置它可见性的只有 `:1489`（`setShowPointer` 分支）与 `:1592-1593`（`setCompassMode`）。
- 后果一：进罗盘态（`:1593` 置 VISIBLE）→ 点「隐藏」→ 盘面/十字/工具列全隐，**指针图留在屏上**。
- 后果二：隐藏态再点「指针开关」走 `:1489` `pointerView.setVisibility(on ? VISIBLE : GONE)`，可直接把指针置 VISIBLE。

### D3 切「地图/实景」不刷新指针【已证实，本轮新增】

```
875: private void toggleSwitchState(String label) {
877-882: compassMapMode = ...（改三态）
892-900: 只重刷四枚开态 glyph
901: Log.d("LCSWITCH", ...)
903: }
```
- 函数内只改状态与图标，**没有任何可见性刷新**，也不调 `setCompassMode`。
- 后果：即便按 D1 在 `setCompassMode` 里补了门控，用户在罗盘态点「地图→关」或「实景→开」后，指针可见性仍是旧值——门控会**过期**。所以修复点必须含 `toggleSwitchState`，不能只补 `setCompassMode`。

## 3. 独立修法（与 LC审地图 的建议一致，补第三处）

抽一个唯一入口，四处调用：

```java
private void refreshPointerVisibility() {
    boolean visible = compassMode && !compassHidden
            && compassMapMode != MAP_MODE_MAP   // 原版 q && !p → 隐藏
            && pointerOn;                       // 否则取 M0()
    if (pointerView != null) pointerView.setVisibility(visible ? View.VISIBLE : View.GONE);
}
```
调用点：
1. `setCompassMode()` —— 删掉 1592-1593 的内联三元，改调 `refreshPointerVisibility()`；
2. `applyCompassHiddenVisuals()` —— 末尾加调用（修 D2）；
3. `setShowPointer` 分支（1482-1490）—— 不再直接 `setVisibility`，改调 refresh（修 D2 的第二条）；
4. `toggleSwitchState()` —— 末尾加调用（修 D3）。

## 4. 顺带一条前置风险（不属本条判据，但会变成死分支）

```
1491: } else {
1492:     js("toggleNeedle()");
1493: }
```
- 非罗盘态时「指针开关」控的是 `map.html` 的 `#needle`；而 `#needle` 是**我方自加、原版零对应**，`LC审地图` 已判定「补完两个符号后删 `#needle`」。
- 删掉 `#needle` 后，这条 `else` 分支会变成**点了没反应的死控件**。删 `#needle` 时必须同时决定：`指针开关` 在非罗盘态是否保留（若不保留，`else` 分支与按钮要一起去掉；若保留，需要新的载体与取证）。
- 本条**不进第 3 条判据**，只作删 `#needle` 的连带项登记。

## 5. 复验结论

1. `0e40621` **不必回退**——它做的是「指针开关 → 竖向指针图」的绑定，方向对；问题是**门控与刷新没做**，属未完成，不是做错。
2. 「不需要改代码」**不成立**：D1、D2、D3 三处都要改，且必须是同一个 refresh 函数四处调用，否则改一处仍会漏。
3. 「等价」在**修完之后**成立（可达状态层面）；互斥三态 vs 两独立布尔的建模差异按 LC客户端 已记录的台账保留，不影响本条。
4. 两个符号（当前位置 / 屏幕中心）仍是最高的未取证项，`#needle` 的删除顺序不变：先补符号 → 再删。

---

证据：`git show origin/develop:app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java`（`a3a502a`，3741 行）逐行核对，命中 `:855-858`、`:860`、`:864-872`、`:875-903`、`:1482-1493`、`:1592-1593`、`:1699-1713`；对照条件取自 `docs/fidelity/m6/reverse-stage1.md:385-390` 与 `m6-plan` v0.4 §1.4。未上机、未改源码。
