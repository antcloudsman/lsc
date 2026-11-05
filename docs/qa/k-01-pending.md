# K-01 待跑清单（设备放行后一次跑完）

> 维护：LC测试。来源：技术总监 2026-11-05 令（K-01 阶段允许 build-only 提交，截图后补）。
> 规则：每笔 K-01 提交的「待跑」块登记在此；状态只允许 **待跑 / BLOCKED-环境 / 已跑（附原始输出）/ 已过（附原始输出 + 判据）**。
> **禁止把「待跑」写成「已过」**；设备放行后按本清单顺序一次跑完，原始输出贴回本表。
> 设备：`L2E0222531014833`（当前安装被华为统一认证拦截）。

## 0 当前阻塞

| 阻塞 | 原文 | 解除方式 |
|---|---|---|
| 装包 | `Failure [INSTALL_FAILED_ABORTED: User rejected permissions]`（焦点 `com.huawei.coauthservice/...UnifiedAuthenticationDialogActivity`） | 设备上完成一次统一认证，或由用户直接安装 release/debug 两包；装完本条移出阻塞 |

## 1 待跑项

| # | 来源提交 | 条目 | 要跑的证据 | 判据 | 状态 |
|---|---|---|---|---|---|
| P-01 | `208023f`（B-02 静态+字节码） | B-02 运行态：release 无桥 / debug 对照 | `AT-P0-1` §1.1 四条命令原文；`logcat -d -s LCDEBUG LCJS` 原文；`aapt dump badging` release/debug 两段 | release 零输出；debug 出现 `bridge registered` 与两条 `LCJS … -> 2`；`aapt` release 无 `application-debuggable`、debug 有 | BLOCKED-环境 |
| P-02 | 客户端 K-01 step-1（待提交） | 删 Tab / 单页 / 左 8 右 10 | 结构断言（`bottom_bar|tab_` 0 命中、`createMapWebView()` 调用数、`setUiMode` 调用点行号）+ `k01-map.png` + `ui-map.xml` | 无底部导航；左列 8 / 右列 10 命中（脚本按 `orig-main.xml` 比例位，容差 45px） | 待跑 |
| P-03 | K-01 落地笔（客户端） | SW-006 第 4 条：面板区裁切窗重取 | 新参考图 + 新窗 `y0-y1` + 测量方法（谁量/哪张截图/怎么定面板上缘）+ 同航向同贴面 diff | 旧窗 `y1850-2500` 与旧参考 `round-d5/variant-p01` 作废；新窗判据由客户端测量后定，并写进 `round-k01/manifest.md` 的「裁切窗测量依据」 | 待跑 |
| P-04 | 地图侧 `measurements.md`（待提交） | 罗盘圆心/直径/外环/face alpha | 截图测量数据 + 采样点清单（RGB、推测背景色、alpha 上下界） | 圆心与屏幕中心偏差 ≤2% 屏宽；直径/外环占比按 `orig-06-read.png` 实测；alpha 给区间不给单值 | 待跑 |
| P-05 | 客户端 K-01 布局落地（待提交） | K-01 全量验收 + `round-k01` 基线重冻 | `freeze_android_baseline.ps1` 全量产物（`k01-map.png`/`k01-more.png`/`k01-compass.png`/`k01-map-after-hide.png`/`ui-*.xml`/`nodes-k01.csv`/`manifest.md`）+ F-01/F-02 门禁输出 | 见 `round-k01/manifest.md`：左 8/右 10/更多 16 硬项；浮层态截图；门禁 `tests=82 skipped=0 + CARGO: PASS` | 待跑 |
| P-06 | 审查遗留（本清单新增） | P2-06-vis 金色角注带 `y1400-1850` 复核 | K-01 后重截分享图；复核角注是否仍在面板上方、带窗是否需改 | `run-final.ps1` 现用固定带 `y1400-1850` 判 PASS/FAIL；该带同样按旧布局量出，K-01 后必须先复核再判，复核前不得据此判 FAIL | 待跑（复核后决定是否改代码） |

## 2 跑批顺序（设备放行后）

1. 装 debug 包（K-01 step 最新提交构建）→ P-02 结构断言 + 截图；
2. 装 release 包（同一提交）→ P-01 release 零输出；再装 debug → P-01 debug 对照；
3. 跑 F-01/F-02 门禁（`python tests/numeric/run_all.py`，要求 `tests=82 skipped=0` + `CARGO: PASS`）；
4. 用 `tests/numeric/tools/freeze_android_baseline.ps1` 重冻 `docs/qa/baseline-android/round-k01/`（P-05），同时回填 P-03 新裁切窗与依据、P-04 几何数据；
5. P-06 复核后再决定 `run-final.ps1` 的 P2-06-vis 带窗是否修改。

## 3 登记纪律

- 每笔提交的「待跑」块由提交人（客户端/地图）在提交说明中给出，测试汇总到本表，不改写原判据。
- 「已过」必须同时有：原始输出（日志/截图/dump 文件路径 + 哈希）+ 对应判据；缺一按「已跑」登记，不按「已过」。
- 旧基线 `round-d5/` 与根目录基线为 K-01 前三 Tab 基线，已作废；本清单与 `round-k01/manifest.md` 是唯一有效口径。
