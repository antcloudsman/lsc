# M9 索引（新会话从这里开始）

> 只做导航与状态索引，不复制别的文件里的数字；版本/计数以
> `docs/product/m7-eight-final-states.md` 为唯一权威，本文件只引用。

- 顶点：**`d7bd568`**（`app` 树 `63ca60a`）；`map.html` `blob:1a2dbdfb` / `sha256:eb3cb6f9…`（213,187 B）
- 本阶段五道门禁 + 七门 smoke 的退出码与参数行：`E:\APPDetector\reports\m9-stage4-d7bd568\`
  （`gate1..4.log`、`gate3b.log`、`gate3-def.log`、smoke 摘要见 `E:\APPDetector\reports\m9-stage4-smoke.log`）
- 证据目录（按事项）：A `reports/m9-a-100ba25\`、`reports/m9-a-verify-541431a\`｜B `reports/m9-l2a-bare-d7bd568\`｜
  C `reports/m9-c-conflict-marker\`｜门禁 `reports/m9-stage4-d7bd568\`
- 签名评估：`docs/fidelity/m9/release-signing-eval.md`（结论：**本轮不配 release 签名**；代价=包 sha 全变、必须卸载重装、
  削弱 debuggable 验收入口；触发条件=给他人安装/上架/过厂商安装器）
- 快照口径：本文件所列顶点/blob/读数均为 **2026-11-10 快照**；版本与计数以
  `docs/product/m7-eight-final-states.md` 为唯一权威，本文件只引用、不另立数字


## 三件事的状态

| 项 | 状态 | 证据 |
|---|---|---|
| A 贴地盘面跟随 `dialType`/`dialAlpha` | 已修并真机验收（`matrix=10 fails=0`，屏幕合成 k=0.502；坏包对照 k=0.253） | `reports/m9-a-100ba25/M9-A-EVIDENCE.md`；分支 `feature/lc-test-m9-a-verify` |
| C 冲突标记检查（工具债） | 已入库：三标记各自 FAIL，两处反证变红；`CONFLICT: scanned=N hits=0` 进报告 | `tools/fidelity/evidence-lint.py` + `tests/e2e/verify-conflict-marker-guard.ps1`（8 case） |
| B L2a（原生 `CompassView` vs canvas） | 判据已按 M9-D2 定稿并实现；`bare` 方法两侧已落地；**本轮出数与判读见下节，独立复验由 LC审核验 做** | `tests/e2e/m9-l2a-compare.py`（主判缺陷块）、`m9-l2a-capture.ps1`（四步链 + 时序守卫） |

## L2a 现状（本轮实测，参数随读数一起给）

- 方法：甲方案（两侧 `bare=1` 只画位图）；比较空间 1456、`σ=1.5` 绝对像素、`union(alpha>250)` 掩膜、中心 `0.30·r_dial` 排除
- 主判（M9-D2）：缺陷块 **面积 ≥30px 且块内 max≥32** 即 FAIL；占比/mean/max 仅报告
- 已量出的底座：`B_noise=0.000%`；`B_BLOCKS n=5 maxArea=3px`（Android 解码 vs PIL 解码，同尺度）；二次重采样模拟 max 9 / 块 1px
- 未解：两侧绘制分辨率不同（canvas 原生 549 vs native 原生 906→导出缩 549），bare 比对残余 4.79%、5 个约 650px 块（max 93–109）；三条常见解释已被上述底座数据排除，**两条候选处理**（906 空间比 / 原生按 549 布局出图）待裁定
- 负向对照：真实 bare 图四变异 4/4 全红（平移 1px / 抹内容 / 多画 10° / 同图不变）

## 未结与提请注意

- 终态表在树内为 **v1.19**（已知差 2 项，仍含「贴地盘面不跟随」）；产品侧 `2a1c36b`（v1.20）已备好、
  合入后即 v1.20（已知差 1 项，仅剩 B1）。**合并前不得按 1 项关账，也不得按 2 项结项**。
- ④⑤（逐环存在性/半径）在真实图上**测不到环**，按裁定记「不可测」，不得据此签「画得像」。
- 已知缺陷 M9-D1：`groundCanvasMeta().dialPx` 输出画布边长、`off` 未取整；取数一律用 `w×0.37725`，修法进 M10。
- 帮助中心图数 29、尺寸规则 7 条（覆盖 22+6+1），门禁② 全绿。
- 关账备注（范围限定）：`挂起/待核对/未验证` 在**终态表与本 INDEX 为 0**；仓库历史文献
  （`m3-plan.md`、`00-FINAL.md`、`checksheet.md` 规则原文等）仍有这些字样，属历史记录与规则引用，
  **不得宣称「全仓命中 0」**——清零需改冻结文献，违反纪律。

