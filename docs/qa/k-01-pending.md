# K-01 待跑清单（设备放行后一次跑完）

> 维护：LC测试。来源：技术总监 2026-11-05 令（K-01 阶段允许 build-only 提交，截图后补）。
> 规则：每笔 K-01 提交的「待跑」块登记在此；状态只允许 **待跑 / BLOCKED-环境 / 已跑（附原始输出）/ 已过（附原始输出 + 判据）**。
> **禁止把「待跑」写成「已过」**；设备放行后按本清单顺序一次跑完，原始输出贴回本表。
> 设备：`L2E0222531014833`（当前安装被华为统一认证拦截）。

## 0 当前阻塞

| 阻塞 | 原文 | 解除方式 |
|---|---|---|
| 装包 | **真因是安装会话被占用**：18:42 起有一条 `adb install` 挂住（stdout 停在 `Performing Streamed Install`，进程不退出），后续每次安装都排队到超时，系统报 `INSTALL_FAILED_ABORTED: User rejected permissions`；点「继续安装」无效是因为会话根本轮不到新安装。**「纯净模式增强防护」最多只能解释更早那一次，不是最近几轮的因**，报告里不得再把它写成当前根因 | `adb kill-server` + 清残留 adb 进程、重启服务后重试即 `Success`。脚本已加两道前置闸：① 采集/装包前查主机侧残留 `adb install` 进程（本机 EMUI 的 `pm list sessions` 不支持，报 `unknown list type`）；② 装完从设备 pull `base.apk` 现算 sha 与磁盘对齐（`-SkipInstall` 时以设备 sha 为报告口径）。仍只由技术总监单人装包 |

- 设备现状（installed）：**`53A962ED` / 6,564,412 B**（19:06:45，pull 回逐字节核对），对应 `2696c19`（含 K-02 顶栏四项；与 `d0b1f90` 的 app 差异仅 javadoc 注释，非注释行 0）。
- **最终 target（已装，19:06:45）**：`53A962ED` / 6,564,412 B，`BuildHead 2696c19`；磁盘与设备 pull 逐字节一致。`C4BF5550`、`13106018`、`2667C765`、`A65581FA`、`338D59F6` 全部作废。
- **首冻 `round-k01`（18:10）作废**：`pm clear` 在本机连运行时权限一起清，冷启动弹「是否允许…访问位置信息」，`ui-map.xml` 采到的是 `com.android.permissioncontroller` 弹窗（36 节点、无一个应用控件）→ 左/右列判成 0/8。旧前台闸拿整份 `dumpsys window` 做子串匹配，应用窗口在后台也照样命中，所以没拦住；`DumpUi` 当时也没有 package 校验。产物移至 `E:\APPDetector\evidence\round-k01-void-1810`，**不得引用**。
- **冒烟 smoke6（18:20）作废**：summary 记的 APK 是 `A65581FA`，而设备上装的是 `2667C765`——脚本 `-SkipInstall` 时把磁盘包 sha 写进报告，与设备实际运行包不一致；该轮 11 PASS/1 FAIL/8 BLOCKED 不采信。
- **误装窗口作废**：17:48–17:53 设备上装的是 `feature/gap-client` 构建（6,880,925 B / `B3EFD676`），不是 K-01 target；该时段任何设备结果一律作废，不得引用。
- **M4 结论（已收口）**：`smoke5`（`C4BF5550`）与 `smoke8`（`338D59F6`）、`smoke9`（`68C481A8`）三次全量序列里 M4 均 PASS（`mode=3d`、`sitNorth=True`、`base=img/vec`）；`smoke7` 的三条 FAIL 记**脚本序列污染**（M1–M3 改前台/站点后未复位），不记应用缺陷。`-OnlyM4` 已加，可在干净序列下单独复验（`m4-only-b` 10/0/0）。
- **`338D59F6`（`bedea47`）冻结作废**：装 `68C481A8`（K-02 顶栏四项）后罗盘态结构已变，该版 16/19 不再代表最终包；产物留档 `E:\APPDetector\evidence\round-k01-bedea47-1856`，只作过程证据。
- **K-02（罗盘态顶部栏）已实现并实测**：`d0b1f90` 起罗盘态 dump 含 `返回/搜索/图层/隐藏` 四项（`smoke9` 的 `M2-topbar` PASS），罗盘态「图层」能开 `#srcPanel` 12 源（`M2-compass-layer` PASS，与 `M4-layer` 同一判据）。搜索为**等价替代**（原版搜世纪空间地名/景观 POI，本实现搜盘面/圈层/知识条目），按总监口径签「等价替代」，注释已写清语义对照。
- **执行前置（首跑作废原因）**：真机脚本必须先确认 `mCurrentFocus` 是 `com.luopan.fengshui`；首跑 `ui-first.xml` 采到 `InstallStaging` 安装器界面，整批结果作废。smoke 与 freeze 脚本已加前台闸（smoke BLOCKED exit 2 / freeze throw），不再靠人记。
- **当前证据集（最终包）**：`smoke10`（`53A962ED`）**22 PASS / 0 FAIL / 0 BLOCKED / 0 DIAG**；`round-k01`@`2696c19`/`53A962ED` 左 8/右 10/更多 16/罗盘 **19**、`offTol=0`、dx **8.5** / dy **0**、门禁 PASS、`FATAL 0/0/0`、exit 0（commit `2f0c12d`）。与上一轮 `d0b1f90`/`68C481A8` 三项数字完全一致，两轮 `ui-map.xml` 逐字节相同（`19480613…`）。A1–A10 单列 P-11。
- A8 判据改为「`uiMode==='map'` 且浮层消失」，不再断言 `#controls` 恢复（map 模式下由原生壳承载控件，设计如此）。
- 口径：不得用旧包跑出的 P-04/P-10 结果当 target 的验收；换包后重跑。

## 1 待跑项

> 状态口径：最终包 `53A962ED`（`2696c19`）已装，`smoke10` 22/0/0 与 `round-k01` 19/19 均在该包上取得；`smoke9`（`68C481A8`）只作 K-02 行为证据。17:48–17:53 误装 `gap-client` 窗口的结果作废。

| # | 来源提交 | 条目 | 要跑的证据 | 判据 | 状态 |
|---|---|---|---|---|---|
| P-01 | `208023f`（B-02 静态+字节码） | B-02 运行态：release 无桥 / debug 对照 | `AT-P0-1` §1.1 四条命令原文；`logcat -d -s LCDEBUG LCJS` 原文；`aapt dump badging` release/debug 两段 | release 零输出；debug 出现 `bridge registered` 与两条 `LCJS … -> 2`；`aapt` release 无 `application-debuggable`、debug 有 | BLOCKED-纯模式（同 §0） |
| P-02 | 客户端 K-01 step-1 `42f5cb7` + 布局修正 `ffb0938` | 删 Tab / 单页 / 左 8 右 10 | 静态：`BUILD SUCCESSFUL`、`activity_main.xml` 的 `bottom_bar|tab_` 0 命中、`createMapWebView` 1 定义+1 调用、XML well-formed；真机：`k01-map.png` + `ui-map.xml` | 无底部导航；左列 8 / 右列 10 命中，且 **18 个按钮中心与 `orig-main.xml` 参考坐标偏差 ≤45px**（分母用内容帧 2578，不用 2700；见 `96ea11b`） | **已过**（`round-k01`@`2696c19`/`53A962ED`：18 钮 `offTol=0`、dx 8.5 / dy 0；commit `2f0c12d`） |
| P-03 | K-01 落地笔（客户端） | SW-006 第 4 条：面板区裁切窗重取 | 新参考图 + 新窗 `y0-y1` + 测量方法（谁量/哪张截图/怎么定面板上缘）+ 同航向同贴面 diff | 旧窗 `y1850-2500` 与旧参考 `round-d5/variant-p01` 作废；新窗判据由客户端测量后定，并写进 `round-k01/manifest.md` 的「裁切窗测量依据」 | BLOCKED-纯模式 |
| P-04 | 地图侧 `measurements.md`（`64f8abc`）+ 构建树 `k01-int@8bb2ad4`（L296 直径） | 罗盘圆心/直径/外环/face alpha | 截图测量数据 + 采样点清单（RGB、推测背景色、alpha 上下界）；代码断言 `MainActivity` L296 `0.7378f * W + 16`（1228→906） | 圆心与屏幕中心偏差 ≤2% 屏宽；外径 **906 px**（判据 900–910）；圆内可见地图纹理（无 0.82 暗盘）；alpha 上界 0.15 为模型估计、线稿 0.90–1.00 | BLOCKED-纯模式 |
| P-05 | 构建树 `k01/integration` @ `53327d1`（默认 dial 0 + A10 + B-05 预留 API） | K-01 全量验收 + `round-k01` 基线重冻 | `k01-int` 版 `freeze_android_baseline.ps1` 全量产物（`k01-map.png`/`k01-more.png`/`k01-compass.png`/`k01-map-after-hide.png`/`ui-*.xml`/`nodes-k01.csv`/`manifest.md`）+ F-01/F-02 门禁输出；APK `…\k01-int\…\app-debug.apk` 6,560,730 B / sha256 `C4BF5550…` | 见 `round-k01/manifest.md`：左 8/右 10/更多 16 硬项；浮层态截图；门禁 `tests=85 skipped=0 + CARGO: PASS`；manifest 写 `BuildHead=53327d1` + APK sha/bytes | **已过**（`round-k01`@`2696c19`/`53A962ED`，exit 0，commit `2f0c12d`；门禁 `tests=85 skipped=0 + CARGO PASS`） |
| P-06 | 审查遗留（本清单新增） | P2-06-vis 金色角注带 `y1400-1850` 复核 | K-01 后重截分享图；复核角注是否仍在面板上方、带窗是否需改 | `run-final.ps1` 旧固定带 `y1400-1850` 已停用（只记 `DIAGNOSTIC`，不判 PASS/FAIL）；复核新带后再决定恢复判据 | 已暂停（旧带停用，待复核新带） |
| P-07 | 客户端 `70f6b1d`（step-3）+ `map-bridge.md` L262（`64f8abc`） | 指针开关两模式判定 | 地图模式：点右列「指针开关」，取 `__LC3D.getState().needle` 与 `#needle` 的 display；罗盘模式：`k01-compass-needle-on/off.png` 截图比对 | 地图模式两处状态同步翻转；罗盘模式 `#needle` 恒 none、北针由 `CompassView.setShowPointer`（L35）控制且只能靠截图判；不得用 `getState().needle` 代判罗盘北针 | BLOCKED-纯模式 |
| P-08 | 客户端 `f3006f2`（step-4，`CompassView` L135；坐标经 z 序校正） | 圆盘外触摸透传（K-01 判据 4） | **垂直方向**：`input swipe 614 800 614 1100`（圆顶 y=900 之外 100px），前后取 `__LC3D.getState().lng/lat`；同点单击确认 `heading` 不变。**不再用水平 50px**：x≈114/1114 落在左右工具列矩形内（列宽 58dp≈174px），必然假失败 | `getState().lng/lat` 变化（地图被拖动）；圆外点击不被 `CompassView` 消费；圆内仍由罗盘处理 | BLOCKED-纯模式 |
| P-09 | 客户端 `53327d1`（默认 dial 0）+ `f3006f2`（`drawText` 描边） | 线稿可读性分两档 | (a) 默认路径：`smoke5/k01-compass-overlay.png`（dial 0 压亮矢量地形）对比度实测；(b) 矢量回退：`artwork=null` 时 `CompassView.drawText` 的 2px `argb(200,0,0,0)` 描边 | (a) **目标侧亮地形可读性不足（已证实）**：中位 1.68:1、`<1.5:1` 占 40.8%、`<3:1` 占 73.2%（含地图自带白色注记，p90 20.96 不作反推）；原版矢量底基线**未取证**；(b) 描边只在回退路径生效，**不得宣称「默认盘面已加描边」**；客户端 18:2x 另举「原版同 PNG + α=1 + 无暗底」主张 P-09 应记「与原版一致」，**待总监签字**，签字前不写结论 | 待签字（结论措辞未定；实测数据已齐） |
| P-10 | 客户端 `18415ae`（B-05，已含在 target 包内；`CompassView` L34/42/53/143/196；`MainActivity` L498/502/858-859） | B-05：点按不再冻结传感器 / 锁定独立 / 偏移持久；**⑤ 翻坐向 N/A** | ① 单击罗盘中心后转动手机：`度数` 行继续变、状态条无「 · 锁定」；② 圆内拖动 <slop 不转盘，≥slop 只转盘且状态条出现「 · 锁定」；③ 点「罗盘锁定」冻结、再点恢复；④ 点「罗盘复位」：heading=0、offset=0、恢复跟随。⑤ 翻坐向 UI 已随 K-01 删除，**N/A 不可验**。证据：`ui-b05-*.xml` + 截图 | ① heading 变化且无「锁定」；② 抖动不转盘、过 slop 才转盘；③ 锁定状态与 `isFollowingSensor()` 一致；④ reset 后 `getHeadingOffset()==0` 且恢复跟随；⑤ 记 N/A，不写已验证；JVM 断言已落 `05528c3`（`applyOffset(350,180)=170`、`applyOffset(0,180)=180` + `CompassView` 源码不变式：两方法均不得改 `followSensor`） | N/A(⑤)；①–④ 待重跑（target 已装） |
| P-11 | 总监 K-01 冒烟/A1–A10（最终包 `53A962ED`） | A8 判据修订后复跑 | 隐藏后取 `__LC3D.getState().uiMode` + 截图确认浮层消失 | `uiMode==='map'` 且浮层消失；**不再断言 `#controls` 恢复**（map 模式由原生壳承载控件，设计如此） | 待跑（A1–A10 单列；`smoke9` 不覆盖 A8 的 `uiMode` 断言，需按契约 A8 单独取 `__LC3D.state().uiMode`） |
| P-12 | 总监盘面裁定 + 客户端 `53327d1` | 盘面分档：dial 0 透出 vs dial 1–4 不透明 | dial 0：`smoke5/k01-compass-overlay.png`（默认 dial 0）盘内像素落入盘外地图调色板比例；dial 1–4：同一脚本对照 | dial 0：**99.4%**（r/R 0.45–0.65，中位距离 3.0）落入盘外调色板 → 盘内透出；dial 1–4：16.7–25.7%、中位距离 47–52 → 整盘不透明皮肤，原版对照**未取证**、不得写「已对齐」 | **已过**（总监 18:0x 复跑脚本数字一致并签字：「P-12 通过」；脚本 `E:\APPDetector\dial0-alpha-check.py`） |

### P-10 ⑤ N/A 依据（审核验裁定）

- 原「坐向（转180°）」按钮已随 K-01 删除；原版 `README` §4/§5 控件清单也没有它 → 按审核验裁定判 **N/A**，不做 (b) 的新增反盘入口。
- `git -C E:\APPDetector\wt\k01-int grep -n 'setHeadingOffset' HEAD -- app/android/app/src/main/java` 输出只有两行：
  - `CompassView.java:42: public void setHeadingOffset(float deg) {`（定义）
  - `CompassView.java:163: // 手动修正走 setHeadingOffset()（保持跟随）`（注释）
  → **0 调用者**；保留为预留 API，定义处注明「UI 未接入，仅供 JVM 断言」。
- `getHeadingOffset` 调用者仅 `MainActivity.java:888`（状态条「 · 偏移」）。
- `setSensorHeading` 合成式在 `CompassView.java:145-150`：`heading = normalize(azimuth + headingOffset)`。
- **JVM 断言落点待定**：`CompassView` 依赖 `android.view.View`，纯 JVM 无法直接实例化；需把 `normalize(az+offset)` 抽到纯类（`CompassData`/`SensorMath`）后由 `tests/numeric` 断言，涉及实现方改代码，待总监派单。

## 2 跑批顺序（设备放行后）

1. 装 debug 包（K-01 step 最新提交构建）→ P-02 结构断言 + 截图；
2. 装 release 包（同一提交）→ P-01 release 零输出；再装 debug → P-01 debug 对照；
3. 跑 F-01/F-02 门禁（`python tests/numeric/run_all.py`，要求 `tests=85 skipped=0` + `CARGO: PASS`）；
4. 用 `tests/numeric/tools/freeze_android_baseline.ps1` 重冻 `docs/qa/baseline-android/round-k01/`（P-05），同时回填 P-03 新裁切窗与依据、P-04 几何数据；
5. P-07 指针开关两模式：地图模式取 `getState().needle` 与 `#needle` 的 display，罗盘模式用 `k01-compass-needle-on/off.png` 截图比对；
6. P-08 圆外触摸透传：`input swipe 614 800 614 1100`（垂直，避开左右工具列），前后取 `getState().lng/lat`，同点单击确认 `heading` 不变；
7. P-09 线稿可读性：`k01-compass.png` 亮地形处检查 2px 黑描边效果；
8. P-10 B-05 行为：单击后转手机仍跟随；圆内拖动 <slop 不转、≥slop 转且「锁定」；锁定/恢复；复位后 offset=0；
9. P-06 新带复核后，再决定 `run-final.ps1` 的 P2-06-vis 是否恢复 PASS/FAIL 判据。

## 3 登记纪律

- 每笔提交的「待跑」块由提交人（客户端/地图）在提交说明中给出，测试汇总到本表，不改写原判据。
- 「已过」必须同时有：原始输出（日志/截图/dump 文件路径 + 哈希）+ 对应判据；缺一按「已跑」登记，不按「已过」。
- 旧基线 `round-d5/` 与根目录基线为 K-01 前三 Tab 基线，已作废；本清单与 `round-k01/manifest.md` 是唯一有效口径。

## 4 交接物（当前，已由 LC测试 核对）

| 项 | 值 |
|---|---|
| 装包点（唯一） | `E:\APPDetector\wt\k01-int\app\android\app\build\outputs\apk\debug\app-debug.apk`，6,562,025 B，sha256 `A65581FAAE6CD21E50E3FB0D7D6775B6F8D61BC2BD8102A7ACB1C632903B90EF`，构建时间 18:20:19（含 `ffb0938`） |
| 构建树 | `k01/integration` @ `8bb2ad4`（含 B-05 `18415ae`：`git merge-base --is-ancestor 18415ae 8bb2ad4` 为真；`git diff 18415ae 8bb2ad4 -- app` 仅 `CompassView` +7、`MainActivity` +33/−4，几何三项，无 B-05 回退） |
| 当前树 HEAD（fix-p0） | `5f577ab`（含 `36f5260`/`440260f`/`5f577ab`；不是旧记的 `b04606c`） |
| 旧包（作废） | `fix-p0` 的 6,580,242 B / `D15BE329…`（11:51 构建，对应旧树，仅历史物证）；`6722698C`/6,514,106 更早，作废；均不得再当装包点 |
| 静态断言 | `bottom_bar|tab_` 0 命中；`createMapWebView` 1 定义+1 调用；`CompassView` 触摸守卫 L135、`argb(210` 仅 L175 注释、`showPointer` L32/`setShowPointer` L35/`isShowPointer` L40、`drawText` 描边 L346-360、`headingOffset` L34/`setHeadingOffset` L42/`setFollowSensor` L53/`setSensorHeading` L143、`followSensor=false` 仅 L196（MOVE 分支）；`MainActivity` 直径 L270、`statusView.setClickable(false)` L301、`case "指针开关"` L485/`setShowPointer` L489、`case "罗盘锁定"` L498/`setFollowSensor(!lock)` L502、状态条「 · 锁定/· 偏移」L858-859 |
| 待补 | SW-006 新裁切窗（P-03）；`setHeadingManually` 仅剩注释 L157，无调用（已实核） |
| AT 文档同步 | `094f790`：SW-006 第 4 条在 `AT-P0-0` L82 已标**暂不生效**，恢复条件绑定 `round-k01/manifest.md` 新窗+测量依据，执行器 `SW-006-4: SUSPENDED` 不计 PASS/FAIL；与新窗恢复口径一致 |

## 5 重冻命令（设备放行、总监统一装包后执行）

> 前置：target **`A65581FA` / 6,562,025 B** 已装（pull 回逐字节核对）；冒烟通过后执行，且加 `-SkipInstall`（不重复装包）。pm clear 会清掉运行时权限，脚本清完即重授权并核实，不靠人记。

```powershell
pwsh -File tests\numeric\tools\freeze_android_baseline.ps1 `
  -Apk E:\APPDetector\wt\k01-int\app\android\app\build\outputs\apk\debug\app-debug.apk `
  -OutDir docs\qa\baseline-android\round-k01 -Round round-k01 `
  -Branch k01/integration -BuildHead 53327d1 -SkipInstall
```

- 总监统一装好该 APK 后执行，**加 `-SkipInstall`**：不重复装包，避免再触发安装弹窗；脚本其余流程照跑。
- `-PanelCropY`/`-PanelCropBasis` 等客户端在 K-01 落地笔给出新窗后追加；不传则 manifest 写「待实测」，禁止沿用旧窗。
- **注意**：`k01-int` 版脚本 `.EXAMPLE` 里的 `-PanelCropY 1400-1850` 是旧窗示例，**禁止照抄**；执行时按上面命令，不传该参数。
- 门禁在脚本内先跑（F-01/F-02：`tests=85 failures=0 skipped=0` + `CARGO: PASS`），不通过直接 exit 1、不碰设备。
