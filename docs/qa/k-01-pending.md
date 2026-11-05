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
| P-02 | 客户端 K-01 step-1 `42f5cb7` | 删 Tab / 单页 / 左 8 右 10 | 静态：`BUILD SUCCESSFUL`、`activity_main.xml` 的 `bottom_bar|tab_` 0 命中、`createMapWebView` 1 定义+1 调用、XML well-formed；真机：`k01-map.png` + `ui-map.xml` | 无底部导航；左列 8 / 右列 10 命中（脚本按 `orig-main.xml` 比例位，容差 45px） | 待跑（静态已给，真机 dump/截图待跑） |
| P-03 | K-01 落地笔（客户端） | SW-006 第 4 条：面板区裁切窗重取 | 新参考图 + 新窗 `y0-y1` + 测量方法（谁量/哪张截图/怎么定面板上缘）+ 同航向同贴面 diff | 旧窗 `y1850-2500` 与旧参考 `round-d5/variant-p01` 作废；新窗判据由客户端测量后定，并写进 `round-k01/manifest.md` 的「裁切窗测量依据」 | 待跑 |
| P-04 | 地图侧 `measurements.md`（`64f8abc`）+ 客户端 `74348d1`（L270 直径） | 罗盘圆心/直径/外环/face alpha | 截图测量数据 + 采样点清单（RGB、推测背景色、alpha 上下界）；代码断言 `MainActivity` L270 `0.733f * width + 16` | 圆心与屏幕中心偏差 ≤2% 屏宽；外径 0.733W（@1228 验收 900–910 px，实测 900–906）；圆内可见地图纹理（无 0.82 暗盘）；alpha 上界 0.15 为模型估计、线稿 0.90–1.00 | 待跑 |
| P-05 | 客户端 K-01 step-4 `f3006f2` | K-01 全量验收 + `round-k01` 基线重冻 | `freeze_android_baseline.ps1` 全量产物（`k01-map.png`/`k01-more.png`/`k01-compass.png`/`k01-map-after-hide.png`/`ui-*.xml`/`nodes-k01.csv`/`manifest.md`）+ F-01/F-02 门禁输出；APK `app-debug.apk` 6,579,816 B / sha256 `39DBD20F…` | 见 `round-k01/manifest.md`：左 8/右 10/更多 16 硬项；浮层态截图；门禁 `tests=82 skipped=0 + CARGO: PASS` | 待跑 |
| P-06 | 审查遗留（本清单新增） | P2-06-vis 金色角注带 `y1400-1850` 复核 | K-01 后重截分享图；复核角注是否仍在面板上方、带窗是否需改 | `run-final.ps1` 旧固定带 `y1400-1850` 已停用（只记 `DIAGNOSTIC`，不判 PASS/FAIL）；复核新带后再决定恢复判据 | 已暂停（旧带停用，待复核新带） |
| P-07 | 客户端 `70f6b1d`（step-3）+ `map-bridge.md` L262（`64f8abc`） | 指针开关两模式判定 | 地图模式：点右列「指针开关」，取 `__LC3D.getState().needle` 与 `#needle` 的 display；罗盘模式：`k01-compass-needle-on/off.png` 截图比对 | 地图模式两处状态同步翻转；罗盘模式 `#needle` 恒 none、北针由 `CompassView.setShowPointer`（L35）控制且只能靠截图判；不得用 `getState().needle` 代判罗盘北针 | 待跑 |
| P-08 | 客户端 `f3006f2`（step-4，`CompassView` L135；坐标经 z 序校正） | 圆盘外触摸透传（K-01 判据 4） | **垂直方向**：`input swipe 614 800 614 1100`（圆顶 y=900 之外 100px），前后取 `__LC3D.getState().lng/lat`；同点单击确认 `heading` 不变。**不再用水平 50px**：x≈114/1114 落在左右工具列矩形内（列宽 58dp≈174px），必然假失败 | `getState().lng/lat` 变化（地图被拖动）；圆外点击不被 `CompassView` 消费；圆内仍由罗盘处理 | 待跑 |
| P-09 | 客户端 `f3006f2`（step-4，`CompassView.drawText` L346-360） | 线稿可读性（删整盘填充后的描边） | `k01-compass.png` 取亮绿/亮白地形处，检查山名/度数是否可辨；静态断言 2px `argb(200,0,0,0)` 描边 + FILL | 白字/灰线带 2px 深色描边（按 `measurements.md`「黑描边」还原）；无整盘暗底（不涉及 α≤0.15）；亮地形上文字不糊 | 待跑 |

## 2 跑批顺序（设备放行后）

1. 装 debug 包（K-01 step 最新提交构建）→ P-02 结构断言 + 截图；
2. 装 release 包（同一提交）→ P-01 release 零输出；再装 debug → P-01 debug 对照；
3. 跑 F-01/F-02 门禁（`python tests/numeric/run_all.py`，要求 `tests=82 skipped=0` + `CARGO: PASS`）；
4. 用 `tests/numeric/tools/freeze_android_baseline.ps1` 重冻 `docs/qa/baseline-android/round-k01/`（P-05），同时回填 P-03 新裁切窗与依据、P-04 几何数据；
5. P-07 指针开关两模式：地图模式取 `getState().needle` 与 `#needle` 的 display，罗盘模式用 `k01-compass-needle-on/off.png` 截图比对；
6. P-08 圆外触摸透传：`input swipe 614 800 614 1100`（垂直，避开左右工具列），前后取 `getState().lng/lat`，同点单击确认 `heading` 不变；
7. P-09 线稿可读性：`k01-compass.png` 亮地形处检查 2px 黑描边效果；
8. P-06 新带复核后，再决定 `run-final.ps1` 的 P2-06-vis 是否恢复 PASS/FAIL 判据。

## 3 登记纪律

- 每笔提交的「待跑」块由提交人（客户端/地图）在提交说明中给出，测试汇总到本表，不改写原判据。
- 「已过」必须同时有：原始输出（日志/截图/dump 文件路径 + 哈希）+ 对应判据；缺一按「已跑」登记，不按「已过」。
- 旧基线 `round-d5/` 与根目录基线为 K-01 前三 Tab 基线，已作废；本清单与 `round-k01/manifest.md` 是唯一有效口径。

## 4 交接物（当前，已由 LC测试 核对）

| 项 | 值 |
|---|---|
| `fix-p0` HEAD | `f3006f2`（step-4；step-3 = `70f6b1d`，step-2 = `74348d1`，step-1 = `42f5cb7`） |
| debug APK | `app\android\app\build\outputs\apk\debug\app-debug.apk`，6,579,816 B |
| APK sha256 | `39DBD20F6E96FF4615366346749E7ADB912F130CF6E72FBCAEC9CD24CD966991`（已实算，与客户端交接一致） |
| 静态断言 | `bottom_bar|tab_` 0 命中；`createMapWebView` 1 定义+1 调用；`CompassView` 触摸守卫 L135、`argb(210` 仅 L175 注释、`showPointer` L32/`setShowPointer` L35/`isShowPointer` L40、`drawText` 描边 L346-360；`MainActivity` 直径 L270、`statusView.setClickable(false)` L301、`case "指针开关"` L485/`setShowPointer` L489 |
| 待补 | SW-006 新裁切窗（P-03）；step-3/4 已完成（`70f6b1d`/`f3006f2`） |
