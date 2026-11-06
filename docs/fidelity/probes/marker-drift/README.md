# 实验：固定态截图漂移的归因（立向点累积 vs 渲染/缓存）

目的：钉死「同一固定态（`setView(39.9042,116.4074,12)`、heading 0°）的 PNG sha 为何会变」。
2026-11-06 20:0x 在 `L2E0222531014833` 上跑，APK = `bd0323b` 构建（sha256 `381D163E…`，`97e6b59`/`bcf7e08` 的 `app/` 与之 diff 0 行）。

## 流程（每轮相同）

```powershell
adb shell am force-stop com.luopan.fengshui
adb shell am start -n com.luopan.fengshui/.MainActivity ; sleep 8
adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value 'setView(39.9042,116.4074,12)'" ; sleep 4
adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value 'clearMarkers()'" ; sleep 3
adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 0" ; sleep 1
adb exec-out screencap -p > X.png ; adb shell uiautomator dump → X.xml
```

- **A**：上面流程冷启动后的干净态；
- **B**：A 之后点图两次 `input tap 614 1000`、`input tap 800 1600`（各生成一个 `立向点`）；
- **C**：再走一遍与 A 完全相同的冷启动流程。

## 结果

| 轮 | A sha | B sha | C sha | A/C 关系 | B 与 A |
|---|---|---|---|---|---|
| run1 | `2D741941…` | `548E91D8…` | `2D741941…` | **相同** | **不同** |
| run2 | `AC06F0B5…` | — | `AC06F0B5…` | **相同** | — |
| run3 | `AC06F0B5…` | `A8AAAF42…` | `AC06F0B5…` | **相同** | **不同** |

run3 的 dump 对照：

| 态 | 节点数 | `立向点` 节点 |
|---|---|---|
| A（干净） | 38 | **0** |
| B（点图两次） | **88** | **2**（`立向点 向 子山 · 坐 午山`） |
| C（干净） | 38 | **0** |

## 结论

1. **立向点累积是「可复现的污染源」**：同一次会话内 A 与 C 的 sha 完全相同，点两次图后 B 变（`A8AAAF42…`/`548E91D8…`）。而且 `立向点` 标记**在 uiautomator dump 里可见**（节点 38→88、出现 2 个 `立向点 向X 坐Y`），不是 canvas 里抓不到的元素。
2. **跨会话仍有基线漂移，且有正面证据**：run1 的 A/C 是 `2D741941…`，run2/run3 同样是冷启动、同样 38 节点、同样无标记，却都是 `AC06F0B5…`。两次冷启动给出两个不同的 sha，期间没有任何点图 → 漂移出在瓦片加载/渲染缓存层，不是立向点。
3. 因此「同态 ≠ 同 sha」的正确记账是**两条并列**：
   - 立向点/标记累积（可复现，用 `clearMarkers()` 或冷启动复位，dump 也会跟着变）；
   - 跨会话渲染/缓存差异（未定位到具体原因，但有 A(run1)≠A(run2) 的正面证据）。
4. 判定纪律不变：**跨批次只比 bounds/文案/颜色，不比 PNG sha**；orig↔lc 双图对照只在同批次内做。

## 产物

- dump（入库）：`r3-A.xml` / `r3-B.xml` / `r3-C.xml`（本目录）。
- 截图（不入库，6.4 MB）：`E:\APPDetector\reports\M1-marker-drift\r3-{A,B,C}.png`。
