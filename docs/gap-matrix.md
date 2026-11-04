# 山水罗盘 LC · 1:1 差距矩阵 v1（M0 · P0 部分）

- 生成：2026-11-05（LC逆向）
- 对照片：`E:\APPDetector\input\swss.apk`（`space.xinzhi.globe` v1.6.1）
- 证据根：`.inspect\unpack\mem_smali\`（本轮新产出，见 §0）、`.inspect\unpack\decoded\`、`.inspect\derived\`、`app\MECHANICS.md`
- 「当前实现」一列指向 **当前工作树**（分支 `feature/lc-server-infra`，含各成员本轮未提交改动），行号为写入本矩阵时的实测行号；合入后如有漂移以代码为准。
- 状态只取四种：**已1:1 / 等价替代 / 缺失 / 有意省略**。查不到原版行为的写「未确认」，不写状态估计值。

## 0. 本轮证据链（新增，可复现）

原版被爱加密加壳，磁盘版方法体被 nop；本轮对 **Frida 内存 dump**（`mem_dump/classes06.dex`，7.4 MB，含 `space.xinzhi.globe` 全部 UI/读数类）做 baksmali，得到真实方法体。

```powershell
# apktool.jar 内自带 shaded dexlib2/baksmali（无 main，用 20 行 launcher 调 API）
javac -cp .inspect\unpack\apktool.jar -d .inspect\tools .inspect\tools\BaksmaliLauncher.java
java -cp ".inspect\unpack\apktool.jar;.inspect\tools" BaksmaliLauncher `
  .inspect\unpack\mem_dump\classes06.dex .inspect\unpack\mem_smali
# -> dex classes=7698
```

派生件（中间产物，不入库）：

| 文件 | 内容 | 生成脚本 |
|---|---|---|
| `.inspect\derived\ring_tables.json` | 14 圈层全部文字表（780 条） | `.inspect\tools\extract_rings.py` |
| `.inspect\derived\ring_tables.txt` | 同上，人工可读（`[ZWNJ]`=U+200C） | 同上 |
| `.inspect\derived\readings_check.txt` | 14 圈层在 10°/170°/250° 的向/坐读数 + 三盘 0.1° 扫频 | `.inspect\tools\check_readings.py` |
| `app\mechanics.json` | `rings[].labels` 已填入并标 `confirmed_dex` | `.inspect\tools\update_mechanics.py` |

---

## A. 罗盘圈层与读数

原版读数合同（**已逐指令核实**）：`space.xinzhi.globe.common.CompassReadTypeEnum`
- `getFaceText(F)`（smali 7136-7223）：`i = (int)(((h - offsetDegrees + 360.0f) % 360.0f) / intervalDegrees)`，`0<=i<len` 取值，否则 `"未知"`。
- `getBackText(F)`：`getFaceText((h + 180.0f) % 360.0f)`。
- 构造器 `<init>(String,int,String,FF,String,Z)`：**第4参→offsetDegrees，第5参→intervalDegrees**（`iput v4/v5` 顺序）。

| # | 模块 | 原版行为（证据） | 当前实现 | 状态 | 差距说明 | 建议任务 |
|---|---|---|---|---|---|---|
| A1 | 24 山几何 | TYPE_4 interval=15.0f(0x41700000)、offset=7.5f(0x40f00000)；labels 顺序 `癸山…子山`（子山中心 0°，范围 −7.5..+7.5） | `app/src/main.rs:181`、`CompassData.java:143` 同为 15°/7.5° | 已1:1 | 数值一致；但原版 MOUNTAINS 内部序为 `癸,丑,艮,…,子`（子居末），重建为 `子,癸,…`，靠 offset 等价 | 无（由 v2 生成器统一） |
| A2 | 120 分金几何 | TYPE_6 interval=3.0f、offset=1.5f，labels 120 条 `"{山名} {甲子}"`（含原版错字「已山」=乙山） | `CompassData.java:148` 3°/1.5° 几何一致；**文字按教材式 `wangxiang()` 重算**（:107-114,153） | 缺失 | 文字不是原版表：原版 120 条顺序/命名/错字均已取证（`ring_tables.json` TYPE_6），当前 `wangxiang()` 生成的与之一致率未验证，且「八干四维借支」规则是教材推导而非原版数据 | 客户端轮 B：把 TYPE_6 120 条作为常量表落码，删 `wangxiang()` 推导 |
| A3 | 72 龙几何+文字 | TYPE_5 interval=5.0f、offset=2.5f；labels 72 条结构为 **12 组 × (5 龙 + 1「正X」)**，例：`庚子 壬子 正癸 乙丑 丁丑 己丑 辛丑 癸丑 正艮 …`（含原版错字「戌寅」「戌午」「巳未」「壬坤」等） | `CompassData.java:149` 几何 5°；文字 = 3 龙/山（`dragon72Slot` 上限 2） | 缺失 | 语义级不同：原版每山 5 龙 + 每干维 1「正」，重建是 3 龙/山。`mechanics.json` 旧注释「每山3龙」已被 DEX 否定 | 客户端轮 B：换 TYPE_5 表；桌面/Android 圈层与读数同步改 |
| A4 | 60 龙 | TYPE_7 interval=6.0f、offset=1.5f；labels 60 条为 **12 双山 × 5**，如 `壬子 壬子 / 癸丑 乙丑 … 癸丑 癸丑` | `CompassData.java:150` 几何 6°/1.5°；文字 `JIAZI[dragon60]`（单干支） | 缺失 | 原版是「双山名 + 干支」两段文字，重建只有干支 | 客户端轮 B：换 TYPE_7 表 |
| A5 | 64 卦三圈 | TYPE_11/12/13 interval=5.625f、offset=0；labels 64 条，例 TYPE_11 `乾小畜 兑中孚 离家人…`、TYPE_12 `坤复一 艮颐六…`、TYPE_13 `复甲子金 颐丙子水…` | 桌面/Android **完全没有** 64 卦圈 | 缺失 | 3×64 条文字 + 3 个圈层均缺 | 客户端/地图：加三圈（文本量 192 条） |
| A6 | 正兼向 | TYPE_14 interval=3.0f、offset=0；labels 120 条 = 每山 5 格，`九度外/内/内/内/外`（正向=中央 3 格=9°，兼向=两侧各 1 格=3°） | `CompassData.java:160-165` 用 ±4.5°（=9° 中央）判正向，兼向只给邻山名；桌面同 | 等价替代 | 判定阈值与原版 9° 一致；但原版输出是「X山九度外/内」120 条文字，重建输出「正向/兼向Y山」，文字面 1:1 不成立 | 客户端：改用 TYPE_14 表输出（判定阈值保留） |
| A7 | 八卦三圈 | TYPE_1 `震 离 兑 乾 巽 坎 艮 坤`（7/8 条带 U+200C 零宽前缀）；TYPE_2 `坤 兑七 乾六 离 艮八 震三 巽四 坎`（原版只有 5 个数字，坤/离/坎 无数字）；TYPE_3 `震 4 离 3 兑 2 乾 1 巽 5 坎 6 艮 7 坤 8` | `CompassView.java:208` 用教材 8 卦序 `坎艮震巽离坤兑乾`；知识面板另用 HOUTIAN/XIANTIAN 表 | 缺失 | 原版文字/顺序/数字残缺（原版数据如此）均不同；重建是「正确」的教材版，不是 1:1 | 客户端：按 TYPE_1/2/3 原样落表（含 U+200C） |
| A8 | 读数公式 | `getFaceText`/`getBackText` 逐指令见上；`CompassToolsKt.B1(D)D` = `v%360; if(v<0)v+=360` | `main.rs:167-173 normalize_deg`、`CompassData.java:85-89 norm` 同规则；`CompassData.java:143-159` 同式 | 已1:1 | 逐式等价（float-to-int 截断在 h∈[0,360) 下等价 floor） | 无 |
| A9 | **人盘读数** | TYPE_8：offset=**0.0f**（`const/4 v11,0x0`）、interval=15.0f；labels 首位 `癸山`、末位 `子山` → 子山中心 **−7.5°**（逆时针 7.5°）。等价值：以 `子=0` 的 MOUNTAINS 计，`i=(floor(h/15)+1) mod 24` | `MainActivity.java:235` `plate(h+7.5)`；`plate()`=floor(h/15)，代数上恒等于地盘；桌面 `main.rs:549-550` 同 | **缺失** | 重建把「盘面旋转」写成读数加偏移，结果人盘读数 ≡ 地盘；原版是「同一 offset(0) + 旋转过的文字表」。0.1° 扫频：人盘与地盘 **1800/3600 (50.0%) 不同**（`readings_check.txt` §3） | **P0**：客户端轮 B —— 人盘用 TYPE_8 表 + offset 0 |
| A10 | **天盘读数** | TYPE_9：offset=**0.0f**、interval=15.0f；labels 首位 `子山`、末位 `壬山` → 子山中心 **+7.5°**（顺时针）。等价值：`i=floor(h/15) mod 24` | `MainActivity.java:236` `plate(h−7.5+15)`，代数上仍等于地盘 | **缺失** | 同上；天盘与地盘也不同 1800/3600 (50.0%)。注意：原版 24 山名三盘相同，差异**只**在表旋转，不存在第四套 offset | **P0**：客户端轮 B —— 天盘用 TYPE_9 表 + offset 0 |
| A11 | 天盘分金 | TYPE_10：offset=0.0f、interval=3.0f，labels 120 条 **无空格**（`甲山甲子`，区别于地盘的 `癸山 甲子`） | 无此圈 | 缺失 | 文字连写是原版特征，1:1 要保留 | 客户端：按 TYPE_10 原样落表 |
| A12 | 圈层图形 | 圈层不是矢量绘制：`assets/webapp/assets/imgs/compass/compass.png`+`compass1..13.png` 共 14 幅整盘 PNG（620–2251 px）+ `pointer.png`(46×188)；`CompassToolsKt.smali:3716` 用 `CommonCompassEnum.getResId()` 取盘面图 | 桌面 egui 矢量 7 圈（`main.rs:280-519`）；Android Canvas 矢量（`CompassView.java`） | 等价替代 | 几何/读数可在矢量层等价，但**字体/笔画/配色/留白/线宽**不可能靠代码逼近；原版 14 套 artwork 是版权资产，能否复用需产品决策 | 产品+总监：裁定 artwork 复用（1:1）或永久「等价替代」并接受视觉差异 |
| A13 | 指针/对准线 | `pointer.png`(46×188) 顶部指北针资源 | 桌面/Android 各自画三角形/线 | 等价替代 | 形状与质感不同 | 同上（随 A12 决策） |
| A14 | 五行/阴阳/纳音着色 | 知识库 HTML 中的五行配色规则（`六十甲配六十四卦五行.html`） | `main.rs:608-630`、`CompassView.java:264-283` 自定配色 | 等价替代 | 配色取自 KB 规则而非原版渲染色值（原版渲染色未取证） | 低优先 |

## B. 5 盘面（`space.xinzhi.globe.common.CommonCompassEnum`）

构造器 `(name, ordinal, code, text, resId, isVip)`（`<clinit>` 逐条 `sget R$mipmap.compass*`）。**结论：盘面是「整幅 artwork + 独立读数层」，不是「盘面开哪些圈层」。**

| # | 模块 | 原版行为（证据） | 当前实现 | 状态 | 差距说明 | 建议任务 |
|---|---|---|---|---|---|---|
| B1 | 盘面清单 | `COMPASS_0` code=compass text=`二十四山(阴)` resId=`R$mipmap.compass` vip=false；`COMPASS_1` compass1/`常用罗盘`/compass1_min/false；`COMPASS_2` compass2/`三合罗盘(白)`/compass2_min/**true**；`COMPASS_3` compass3/`三元罗盘(金)`/compass3_min/**true**；`COMPASS_4` compass4/`综合罗盘(白)`/compass4_min/**true** | `CompassData.java:190-192` 只有 5 个名字字符串；无 artwork、无 code、无 resId | 缺失 | 名称 5 项一致；其余全缺。VIP 门槛按「去商业」原则**有意省略**（付费墙不实现，但盘面本身要保留） | 客户端：5 盘面 = 5 幅 artwork 切换（含 4 个 VIP 盘面免费开放） |
| B2 | COMPASS_0 圈层内容 | `compass.png` 1080×1080，由外向内 2 圈：360° 刻度（10° 主刻度+小刻度）→ 24 山（阴）单圈 → 中心圆 | `dialType=0` 时 `CompassView.java:156` 隐藏分金/龙层，`:178` 隐藏 64 卦 | 等价替代 | 重建「盘 0 = 少画几圈」，原版「盘 0 = 另一幅图」；圈数 2 vs 重建 3+ | 随 B1 |
| B3 | COMPASS_1 圈层内容 | `compass1.png` 2048²，由外向内约 9 圈：360° 刻度 → 24 山 → 七十二/六十龙干支（金色区块）→ 百二十分金（五子/三子/七子…）→ 后天八卦+洛书数 → 内层 24 山 → 中心指北针 | `dialType>0` 画分金/龙（`:155`） | 等价替代 | 层数（9）与层级次序均不同 | 随 B1 |
| B4 | COMPASS_2 圈层内容 | `compass2.png` 2048²，12 点钟方向条带逐圈清点（`.inspect\derived\dial_strips\compass2_top.png`）：360° 刻度 → 28宿（十六/虚/九…）→ 纳音五行色带 → 壬子紫带（七十二龙正/空亡）→ 分金五子（丙子/正子/五子/三子/七子）+五行色 → 二十四山（子/癸，带火土）→ 纳音色带 → 六十龙（甲子/丙子/庚子/壬子）→ 壬子癸紫带（三盘 24 山）→ 申巳艮（八卦）→ 乾巳巽 → 后天卦+卦符 → 中心指北针；**约 13 圈** | 无 | 缺失 | artwork 缺失；13 圈 vs 重建 7 圈 | 随 B1 |
| B5 | COMPASS_3 圈层内容 | `compass3.png` 2045²，条带逐圈清点（`dial_strips\compass3_top.png`）：360° 刻度 → 江东/江西·天元/地元/人元 → 卦符 → 九星（贪/巨/禄/文/廉/武/破）→ 洛书数带 → 玄空数带 → 64卦名（同/履/乾/小畜/孚/家…）→ 数字带 → 卦符 → 神煞（破/劫/刑/賓/客/輔/庫/正/天）→ 数字/方位 → 节气+五行（一水/立春/大寒/小）→ 三盘 24 山（壬/子/癸，天·地·人）→ 神煞（玉印/劫煞/華蓋/直符/地煞）→ 后天八卦+卦符 → 中心指北针；**约 17 圈** | 无 | 缺失 | artwork 缺失；三元/九星/神煞体系完全未实现 | 随 B1 |
| B6 | COMPASS_4 圈层内容 | `compass4.png` 2048² 目视确认（由外向内）：360°刻度 → 28宿 → 八煞/劫煞 → 64卦+九星（贪狼/巨门/禄存/文曲/廉贞/武曲/破军）→ 先天八卦 → 后天八卦 → 二十四山（三盘并排）→ 百二十分金 → 穿山七十二龙 → 透地六十龙/纳音 → 中心指向罗盘；约 20 圈（清点行数 ≥18） | 无 | 缺失 | artwork 缺失；且实测该盘圈层数与"综合盘"名称相符，远超重建的 7 圈 | 同 B1 |
| B7 | 盘面↔读数层映射 | 全 dex 内 `CommonCompassEnum.getResId()` 仅 `CompassToolsKt:3716` 取用（背景图）；`getFaceText/getBackText` 仅 `ui/compose/W$a$b/c/i`（读数面板）调用 | 无「读数面板」概念；读数是一行文本 | 缺失 | 原版读数面板按选中的 `CompassReadTypeEnum` 列 向/坐；重建把 14 类压成一行拼接 | **P0**：客户端加读数面板（14 类可选，向/坐分行） |
| B8 | VIP 盘面/圈层门槛 | TYPE_4..14 `isVip=true`（13/14 类），COMPASS_2..4 vip=true | 全部免费（`CompassData.java:189` 注释「all free, no paywall」） | 有意省略 | 按章程「去商业」：付费墙不做，功能全开 | 无 |

## C. 知识库

| # | 模块 | 原版行为（证据） | 当前实现 | 状态 | 差距说明 | 建议任务 |
|---|---|---|---|---|---|---|
| C1 | 页面数量/形态 | `assets/compassIntroduce/` 14 个 HTML（52–60 KB/个，含内嵌图片）：穿山七十二龙、地盘正针百二十分金、地盘正针二十四山、后天八卦_卦位、六十甲配六十四卦五行、人盘中针二十四山、天盘缝针百二十分金、天盘缝针二十四山、透地平分六十龙、先天八卦_卦位、先天八卦洛书数、先天方图六十四卦内卦、先天圆图六十四卦外卦、正兼向度数指标 | Android `CompassData.java:173-187` 8 个标题 + 8 段纯文本；桌面 `main.rs:520-542` 侧栏同 8 段 | 等价替代 | 14 页图文 → 8 段文字：内容覆盖率低、无图、无 HTML 排版 | 客户端：先补到 14 条（标题+正文），图片/排版另议 |
| C2 | 入口 | 点圈层文字 → `CompassToolsKt.a1(BaseActivity, boolean, CompassReadTypeEnum)`（`CompassToolsKt.smali:1539`）→ `CompassIntroduceActivity$a.a(Context, CompassReadTypeEnum)`（`:1568`），用枚举 `html` 字段（如 `地盘正针二十四山.html`）加载 | Android 知识 Tab 静态列表；桌面左侧 knowledge_panel 静态 | 等价替代 | 原版「从圈层直达对应页」，重建只能从 Tab 翻 | 客户端：读数行点击 → 对应 KB 条目 |
| C3 | 14 类 HTML 内容 | 14 个文件已解包可读（`decoded/assets/compassIntroduce/`），本轮未逐页比对正文 | 无对应正文 | 缺失 | KB 正文是原版资产，需逐页抽取成结构化数据（或直接内置 HTML） | 逆向+客户端：抽 14 页纯文本/图片清单 |
| C4 | KB 与盘面联动 | 枚举字段 `html` 把「读数类型」与「KB 页」一一绑定 | 重建无绑定关系 | 缺失 | 14↔14 映射需要落到数据里（`mechanics.json` 已记 `knowledge.files`，未落 `ring→html`） | 客户端轮 B：`rings[].html` 字段 |

## D. 截图

| # | 模块 | 原版行为（证据） | 当前实现 | 状态 | 差距说明 | 建议任务 |
|---|---|---|---|---|---|---|
| D1 | 罗盘截图 | `MainActivity` 字符串 `截屏` / `截屏中...`；`CompassToolsKt.E0` 走 `MapWebApiKt.getScreenshot(mapWebController, …, "compass", …)`，另有 CameraX `ImageCapture` 路径（`MECHANICS.md` 记录） | Android `MainActivity.java:126` `captureMap()`；`:163-179` 抓**地图 tab** 的 `mapView`（罗盘在另一个 WebView 上，且截图时该 tab GONE） | 缺失 | 产品 B-09 已记录：截图必现异常且不含罗盘。原版是「地图+罗盘合成图」 | **P0**：客户端修 `captureMap`（抓罗盘页 WebView + 合成 overlay） |
| D2 | 地图截图 | `MapWebApiKt.getScreenshot(...)` + JS 桥 `screenshotHtml() → Bitmap` | 无 | 缺失 | 依赖地图桥（LC地图 负责） | 待 LC地图 桥接口后派单 |
| D3 | 桌面截图（验收载体） | 原版无桌面端 | `main.rs:713-716` 走 `software::software_screenshot`（独立渲染器），与 egui 真 UI 有 6 处差异（客户端审计）；`main.rs:231-256` egui 截图代码为死代码 | 等价替代 | 「截图 = 实现」不成立；总监已定轮 C 统一 | 轮 C（总监已排） |
| D4 | 截图保存/分享 | 原版截图后存相册/分享（`READ_MEDIA_IMAGES`、`CAMERA` 权限字符串见 `strings_MainActivity.txt:104-107`） | 桌面存 PNG 到指定路径；Android 存 app files dir | 缺失 | 无相册写入/分享 | 产品定 P0/P1 后再派 |

## E. 设置

原版设置入口在**主界面底部抽屉** `MainActivity.MainDrawerContent (MainActivity.kt:851)`；独立页 `SettingActivity.kt:64 Item / :83 Divider`（Compose 通用行），`MapSettingActivity.kt:269/292/313/325/412/461/545`。

| # | 模块 | 原版行为（证据） | 当前实现 | 状态 | 差距说明 | 建议任务 |
|---|---|---|---|---|---|---|
| E1 | 设置菜单项 | 抽屉字符串（`MainActivity` 常量表）：`地图设置`、`账户设置`、`投诉反馈`、`关于我们`、`联系我们`、`推荐给好友`、`开具发票`、`帮助中心`、`用户协议`、`隐私政策`、`第三方sdk清单`、`权限清单` | Android/桌面均**无设置页**（grep「设置/关于/协议」0 命中） | 缺失 | 11 项中 `开具发票/推荐给好友/账户设置` 涉商业与登录 → 有意省略；其余 6–7 项应 1:1 | 客户端：加设置页（地图设置/关于/协议/隐私/权限清单） |
| E2 | 地图设置-滤镜 | `MapSettingActivity`：`地图滤镜`、`模式`、`常规/自然/高亮/自定义`、`地图效果预览`、预览用 `https://appassets.androidplatform.net/assets/webapp/index.html` | 无 | 缺失 | 4 种滤镜 + 预览 | 地图（预览）/客户端（页面） |
| E3 | 地图设置-自定义参数 | `图层亮度最小值/最大值`、`图层饱和度`、`图层对比度`、`图层色相旋转`、`恢复默认`、`参数`（每条带长说明文字） | 无 | 缺失 | 5 个滑块 + 说明 | 地图：栅格图层滤镜参数 |
| E4 | 账户设置 | `AccountSettingActivity`（76 KB，登录/销户/隐私） | 无 | 有意省略 | 按「去商业/去账号」不实现 | 无 |
| E5 | 协议/合规链接 | `https://agreement.yuan9.cn/agreement/swssqjdt/yhxyb.html`（用户协议）、`.../privacy.html`（隐私政策）、`https://cdn.xinzhi.space/doc/policyCompliance/globe/Android/第三方sdk清单.html`、`.../权限清单.html`、帮助中心 `https://xinzhispace.yuque.com/...` | 无 | 缺失 | 原版链接可用但属他人资产；LC 需要自己的协议页（产品/合规给出） | 产品：给 LC 自有协议 URL；客户端接入 |
| E6 | 罗盘显示开关 | 原版无「圈层开关」证据；盘面切换在盘面选择器（`CompassListActivity`） | 桌面 `ShowRings{earth,human,heaven,fenjin,dragon72,dragon60,bagua}`（`main.rs:210-232`）为用户可关；Android 无开关 | 未确认 | 原版是否有圈层显示开关**未取证**；桌面这 7 个开关可能是重建自创（1:1 之外的功能） | 逆向：查 CompassListActivity/MMKV 设置键确认 |
| E7 | 广告/会员弹窗 | `VIP弹窗0..3`、`7日优惠`、`¥6.9`、`立即解锁`、`免广告` 等（`strings_MainActivity.txt`） | 无 | 有意省略 | 去商业原则 | 无 |

## F. 地图（占位 · 本轮到 M0 只登记）

| # | 模块 | 原版行为（证据） | 当前实现 | 状态 | 差距说明 | 建议任务 |
|---|---|---|---|---|---|---|
| F1 | 底图/图层 | WebView 地图：`MapWebView`/`MapWebInterface`(33)/`MapWebController`(26)/`MapWebApiKt`(84)；JSAction `(action, callbackName, linkPar)` = `window[action](params, callback)` | `assets/map.html`（天地图影像+矢量），LC地图 本轮改造中 | 等价替代 | 天地图替代原版底图源 | LC地图 在跑 |
| F2 | 工具集 | `MainActivity` 常量：`图层`、`叠加`、`山脉`、`水系`、`探索`、`3D`、`罗盘工具`、`测量工具`、`画笔`、`收藏点工具`、`钓点`、`街景`、`VR全景`、`超清`、`历史影像`、`地形对比`、`数据叠加`、`定位`、`指北针`、`轨迹记录`、`海拔工具`、`环绕旋转` | `map.html` 仅有缩放/旋转/俯仰/坐向 | 缺失 | 20+ 工具，需按 PRD 取舍 | 产品定 P0 子集后派 LC地图 |
| F3 | 3D 模式 | `MapWebInterface.on3dChanged` + `MapWebKt$Control3d`，长按进入（`suppressLongPressFor`） | `map.html` 俯仰 0–60°（LC地图 本轮做真高程） | 等价替代 | 原版 3D 是 `libterrain.so` VM 保护字节码 | LC地图 |
| F4 | 地图↔罗盘桥 | `setSitFace`、`setHeading`、`onMapRotate`、`onMapScroll`、`showFullScreen`、`setDrawBtnState`、`deleteMapElementByType`、`openStreetView` | Android `MainActivity.java:247-248` 已有 `setHeading`/`setSitFace` | 等价替代 | 桥方法名需与 `docs/map-bridge.md`（LC地图 产出）对齐 | LC地图 |

## G. 3D 地形（占位）

| # | 模块 | 原版行为（证据） | 当前实现 | 状态 | 差距说明 | 建议任务 |
|---|---|---|---|---|---|---|
| G1 | 真高程 3D | `libterrain.so` 256 KB：`vmInterpret@0xfa30`/`cacheInitial@0x18eac`/`getCacheClass@0x19d5c` = Dalvik VM 解释器，地形逻辑为 VM 保护字节码，无罗盘字符串 | LC地图 spike：天地图 DEM + WebGL | 等价替代 | 章程风险#1，不可 1:1 静态还原 | LC地图（验收：≥30fps 实测） |
| G2 | 俯仰/旋转手势 | 长按进 3D + `onMapRotate` | `map.html` 俯仰 0–60° | 等价替代 | 手势阈值/阻尼无原版证据 | 地图 |

---

## H. 缺失清单（按影响排序）

| 优先级 | 缺口 | 影响 | 谁改 | 依赖 |
|---|---|---|---|---|
| P0-1 | 人盘/天盘读数恒等于地盘（`MainActivity.java:235-236`、`main.rs:549-550`） | 核心读数在 50% 方位错误；用户可直接看到盘面与读数矛盾 | 客户端 | 本矩阵 A9/A10（已取证） |
| P0-2 | 14 圈层文字表未落码：72龙/120分金/60龙/64卦×3/正兼向/八卦×3/天盘分金 全部是教材重建或缺失 | 读数文字与圈层名不符，1:1 的核心内容不成立 | 客户端 | `mechanics.json rings[].labels`（已入库）+ 生成器轮 A |
| P0-3 | 5 盘面无 artwork，只有名字字符串 | 视觉 1:1 不成立；盘面切换无内容 | 客户端+产品 | 版权裁定（A12） |
| P0-4 | Android 截图抓错 WebView，必现异常且不含罗盘 | 截图功能不可用（原版核心功能之一） | 客户端 | 无 |
| P0-5 | 无读数面板（14 类可选、向/坐分行） | 原版信息架构缺失，读数被压成一行 | 客户端 | P0-2 |
| P1-1 | 知识库 8 段文字 ↔ 原版 14 页 HTML 图文 | 内容/入口/联动三处都缺 | 客户端+逆向 | C3 正文抽取 |
| P1-2 | 无设置页（地图设置/关于/协议/隐私/权限清单） | 原版 11 项菜单缺 6–7 项 | 客户端 | 产品给 LC 协议 URL |
| P1-3 | 桌面截图 ≠ 真 UI（software.rs） | craft 红线的「截图=实现」前提不成立 | 客户端（轮 C） | 总监已排 |
| P2-1 | 地图工具集 20+ 项 | 原版主界面能力大面积缺失 | 地图/客户端 | 产品 PRD 取舍 |
| P2-2 | 地图设置-滤镜/5 参数 | 视觉可配置性缺失 | 地图 | E2/E3 |
| P3-1 | 3D 真高程 | VM 保护，判等价替代 | 地图 | 已在跑 |
| P3-2 | 圈层全量配色/字体（原版 artwork 内烘焙） | 不逐像素对齐 | — | 章程风险#2 已裁定不死抠 |

## I. 本轮「新证实结论」清单（类/方法/地址 → 建议改动）

1. `CompassReadTypeEnum.getTextList()`（`mem_smali/.../CompassReadTypeEnum.smali` 630-7094，packed-switch key 1..14 → 各 `const-string` 块）**14 张表 780 条全部还原**，且每条都带**逐条指令地址**（`.inspect\derived\ring_label_addrs.json`：`{i, text, code_off, smali_line}`，`code_off` 为 dex code-unit 偏移，与 packed-switch 载荷同一坐标系）→ 写入 `app/mechanics.json` `rings[].labels`；建议客户端轮 B2 以表替代推导（P0-2）。
2. `CompassReadTypeEnum.<init>` `iput v4->offsetDegrees; iput v5->intervalDegrees` → 证实 `MECHANICS.md` 表头（第4参 offset、第5参 interval），纠正「offset/interval 谁在前」的含糊（文档级，无代码影响）。
3. TYPE_8/TYPE_9 `offsetDegrees=0.0f`（`const/4`）+ labels 旋转 → **人盘/天盘的答案**：不是 offset，是表旋转；等价值 `人盘=(floor(h/15)+1)%24`、`天盘=floor(h/15)%24`（MOUNTAINS 子=0）。→ P0-1。
4. `getFaceText(F)` 用 `float-to-int`（截断）而非 `Math.floor` → 在 `h∈[0,360)`、`offset<=22.5` 下两者等价；但实现若允许负 heading 必须保留 `+360 后再取模` 的顺序（`CompassData` 已符合）。
5. 5 盘面 = `CommonCompassEnum`（code/text/resId/isVip）+ `assets/webapp/assets/imgs/compass/*.png` 14 幅整盘图 + `pointer.png`；`getResId()` 仅 `CompassToolsKt:3716` 使用 → **盘面不含圈层清单**，圈层由 14 类读数提供（P0-3/P0-5）。
6. 知识库 = 14 个 HTML（`assets/compassIntroduce/`，52–60 KB/个），由 `CompassToolsKt.a1 → CompassIntroduceActivity$a.a(Context, CompassReadTypeEnum)` 打开枚举 `html` 字段 → P1-1。
7. 原版主界面/菜单/链接清单（`MainActivity` 390 条常量，`strings_MainActivity.txt`）→ 设置页与地图工具集的验收清单（E1/F2）。
8. `MapSettingActivity` 地图滤镜 4 模式 + 5 参数 + webapp 预览 URL → E2/E3。
9. 原版数据自带瑕疵，1:1 必须保留：TYPE_2 只有 5 个卦带数字；TYPE_1 7/8 条带 U+200C；TYPE_5 错字「戌寅/戌午/巳未/壬坤」；TYPE_6 有「已山」；TYPE_10 干支连写无空格。
10. `.inspect/tools/BaksmaliLauncher.java`：apktool.jar 内 shaded baksmali 无 main，用 dexlib2 API 直接反汇编内存 dex —— 后续所有 smali 取证复用此脚本（证据链可复现）。

## J. 未确认（不臆造，等证据）

- B6 已关闭：`compass4.png` 目视确认为约 20 圈综合盘（360°/28宿/八煞/64卦+九星/先后天卦/三盘24山/分金/72龙/60龙纳音/指向罗盘），详见 B6 行。
- 原版「圈层显示开关」是否存在（E6）。
- 原版截图后的保存/分享路径（D4，仅有权限字符串与 `截屏中...` 文案）。
- **原版真机 10°/170°/250° 读数截图**：本轮未取。原因：真机 `L2E0222531014833` 本轮归 LC地图；原版为传感器驱动，需注入/模拟方位角才能定位到指定度数。替代证据：按 `getFaceText` 逐指令移植复算（`readings_check.txt` §2），人盘/天盘/地盘三行均为 DEX 数据的确定值；截图作为第二证据，等真机空出后补（预计 Frida hook `getFaceText(F)` 读入参 + 截图）。
- 原版圈层渲染的精确半径/颜色：artwork 为位图（A12），不存在「代码常量」可抽取，故**不再作为待办**（与章程风险#2 一致）。
