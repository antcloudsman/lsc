# M8 关账摘要（罗盘贴地兑现 + 遗留收口）

- 作者：LC审成文｜日期：**2026-11-10**｜版本：**v1.0**｜状态：**待 LC审核验 签字**（他负责的四项独立复核见 §3）
- 基线：`develop` = **`1bb650c`**（`fig-32` 入册后的最终顶点）；`app` 树 **`b203750ec6d71fd720f18187eb5ec15e97ef4a28`**
- **⚠ `app` 树在 `fig-32` 入册时变了一次，必须标清**：`0c5da8e`（M8 全部真机读数所在树）→ **`b203750e`**，差异**只有两项**——
  `android/app/src/main/assets/lc-help/fig-32.webp`（新增 109,944 B）与 `.../lc-help/index.html`（+4/−2）。
  **`map.html`、`MainActivity`、`KmlIo`、全部 Java 零改动** ⇒ 贴地渲染与十字经线的真机读数在 `b203750e` 上继续成立；但台账**不得**写「门禁包与真机读数是同一棵 `app` 树」，正确写法是「**渲染相关零改动；差异仅在帮助中心静态资源**」。
- 判据来源：`docs/fidelity/m8/ground-drape-acceptance.md`（333 行 / 20,704 B，LC审核验 独立于实现方编写）、`docs/fidelity/m7/ground-drape-migration.md`（方案 A1 + B1）、`docs/qa/m8-ground-device-method.md`
- 本文件只做汇总与索引：**判定归 LC审核验，实现归 LC地图／LC客户端，终态文字归 LC产品**；我不自签。

---

## 1. 基线身份（可直接复查）

| 项 | 值 | 复查命令 |
|---|---|---|
| develop tip | **`1bb650c`**（`fig-32` 入册后最终顶点） | `git log -1 --format='%h %s' 1bb650c` |
| app 子树 | **`b203750ec6d71fd720f18187eb5ec15e97ef4a28`**（真机读数所在树为 `0c5da8e`，差异仅 §基线说明两项） | `git rev-parse 1bb650c:app` |
| `map.html` blob | `26d17e9ebf90299a95abfffc632c03f24d74081e`（`0c5da8e` 与 `b203750e` 相同） | `git rev-parse 1bb650c:app/android/app/src/main/assets/map.html` |
| `help-center.md` blob | **`6a3ebdd988178d52fd0b1d7355f92228ac0833fb`** | `git rev-parse 1bb650c:docs/product/help-center.md` |
| `index.html` 头部 `@` | **`6a3ebdd988178d52fd0b1d7355f92228ac0833fb`**（逐字符相同）；`index.html` blob `bf2142868b811184dfc4ff591fff63f9ddff546c` | `git show 1bb650c:…/lc-help/index.html \| head -1` |
| `lc-help` webp 数 | **29**（`fig-32` 入册，109,944 B） | `git ls-tree -r --name-only 1bb650c -- …/lc-help \| grep -c '\.webp$'` |
| 尺寸规则数 | **7**（6 裁切 + `fig-32` 显式整屏） | `git show 1bb650c:tools/fidelity/evidence-size-map.json \| grep -c 'lc-help/fig-'` |
| 真机验收包 | `12,360,834 B` / `sha256:b306ce3d1876fa073512e3bf8da9e7dc6311bc40200cc8a19928cccd70bfa3d9` | `E:\APPDetector\reports\M8-final-d60a61d\app-debug.apk` |
| 装机设备 | `L2E0222531014833` 单台串行，用后已释放（`LEASE: none`、anim 1/1/1、airplane=0） | 群内 `LEASE_RELEASED` 回执 |

---

## 2. 三轮收口方式

| 轮 | 内容 | 收口依据 |
|---|---|---|
| **A 轮 · 补欠账（贴地）** | 按方案 **A1** 把盘面绘制搬进 `map.html` 的 canvas，作为栅格纹理按**四角投影**铺到地面平面；决策 **B1**：贴地态隐藏原生竖向指针 | 判据表 §2–§6；读数见 §3 |
| **C 轮 · 质量债** | 在 M8 内并入：`setSafeArea` 时序修复（banner 792→760）、贴地接线门禁补静态断言、`node --check` 与单测纳入门禁 | `E:\APPDetector\reports\m8-ground-gate\`（绿/红两份日志） |
| **B 轮 · 竞品 P0** | M7 已交付（KML 往返 `0.000e+00`、离线缓存 `hit=60/shown=60`）；M8 未改动其代码，读数继续有效（`KmlIo` / 缓存逻辑本轮未触碰） | M7 关账摘要 §2.3 |
| **遗留收口（第 4 阶段）** | 账户页登录态、about 落点 3px、`fig-32` 真展开态重拍 —— 见 §5 | 各自证据目录 |

---

## 3. 贴地交付：判据与读数

> 全部读数由 LC审核验／LC测试 在真机执行；我只核对「有没有、是不是同一棵树、参数齐不齐」。**未由我复测的项，一律标来源。**

### 3.1 G1 四角投影（核心：必须是地面平面四角，不是屏幕空间正圆）

| 项 | 读数 | 判据 | 结论 |
|---|---|---|---|
| pitch=0 近矩形 | `sideCss = 728.0164`，框宽 `2 × vw = 728.0 ± 2` | 上/下边差 ÷ 下边 ≤ 0.02 | PASS |
| tilt 透视单调收紧 | `r(0) = 1.0`、`r(30) = 0.6499`、`r(60) = 0.4625` | `r(0) ≥ 0.98`；`r(0) > r(30) > r(60)` 且相邻差 ≥ 0.05；`r(60) ≤ 0.95` | PASS |
| 不随 DEM 起伏 | 北京（elev≈53 m）与珠峰（elev≈8699 m）四角逐点差 **0.039 px** | 逐点差 ≤ 0.1 px | PASS |

`r(pitch)` 单调下降且 60° 时上边只有下边的 46%，正圆与斜切平行四边形都过不了这一条。

### 3.2 G2 盘面与 800 基线逐像素（阈值 ≤2% / CIEDE2000 ≤3）

**有效读数（1456 空间，`E:\APPDetector\reports\M8-g2-final\g2-final-1456.txt`，原文一行 JSON）**

```
canvasW/H  1456×1456    dial 549    off 453.5    cx/cy 728
imgW/H     800×800（基线源尺寸正确，未误用 1200 或原图）
smooth     base{enabled:true, quality:"low"}   comp{enabled:true, quality:"low"}   ← 两边同参数
metric     CIEDE2000>3
D 29931    E 11971
A1（纯位图区）  n=5638  pct=0.00%  meanDE=0
A2（环叠区）    n=6333  pct=0.00%  meanDE=0
C（中心文字）    n=0（源图 0.60r 内全透明，无可比像素，符合预期）
bestHyp    恒等 pct=0.00%；hypTop3 恒等 0.00% / dy=+1 → 44.58% / dx=−1 → 47.11%
stateOk/dbgOk  drawn/true（同帧一致）
```

**旁证（判据能抓错的关键）**：`dx=±1 px` 时差异立刻跳到 **44–47%**，恒等才 0%。**错一点就红、对了才绿**，说明位图与环的落点是逐像素对齐，无镜像、无亚像素偏移。

**必须写清的作废读数**：此前一轮在 **549 裁切空间**比对得 `A diffPct 94.14% / B 79.01% / C 100%`（存档 `E:\APPDetector\reports\M8-final-d60a61d\g2-result2.txt`），另一轮得 `58.73%`。根因是 `off = (1456 − 549)/2 = 453.5` 是**半像素**，而裁剪原点只能取 453 或 454，0.5px 位移被算成「画得不像」。改为**在 1456 空间用同一 `translate(453.5,453.5)` + 同一 `drawImage(img,0,0,549,549)` + 同默认 `imageSmoothingQuality`** 复刻基线后，`A1 = 0.00%`。⇒ **58.73% 的结论是「比对方式错」，不是「贴地画得不像」，实现侧无需修改。**

**判据参数（复现性关键，缺一不可，LC审核验 签字时要写进参数）**：**在 1456 空间比、不在 549 裁切空间比**；等效条件 = `translate(off,off)` + `drawImage(img,0,0,549,549)` + 默认 `imageSmoothingQuality`（实测 `low`）；mask 在 1456 尺度按基线 `alpha>250` 计，另排除 3×3 软边。

**A2 的性质要说清，不许当成「画得像」**：环叠区基线用的是**树内同一个 `drawOutlineRings`**，等于同一函数比自己，`0.00%` 属必然。它只能证明「这段代码确定性地画出了它自己」。方案 §5 原意 L2a 是「**原生 `CompassView` 渲染 vs canvas**」，**本轮未做**，记下一轮。

**三行口径（与终态表 v1.5 逐字对齐，避免两处打脸）**

```
G2-A1  PASS    与 800 素材基线同路径复刻，位图区 0.00%（n=5638, meanDE=0）；±1px 即 44–47% 的反证
G2-A2  自洽    同一 drawOutlineRings 比自己，0.00% 属必然；不是「设计一致」的证据
L2a    未做    原生 CompassView 渲染 vs canvas 的强对照，本轮未执行
```

### 3.3 G3 / B1 贴地态指针隐藏

判据表 §4 规定「贴地时原生竖向指针必须隐藏」；实现按决策 **B1** 执行。测量方式为 A/B 两帧 + 针掩膜命中数，读数由 LC审核验 复核（本文件不重复其数值，见其签字文件）。

### 3.4 G4 十字经线不变（回归保护）

- **前置条件（本轮实测新增，必须照做）**：`crosshairView.setVisibility(View.GONE)` 在 `MainActivity:684` 初始化即隐藏，**地图态取样不到红色像素**（实测 `x=610..619 / y=1346..1354` 全为地图底色 `(211,214,202)`）。**必须在罗盘态测**，否则得到「十字不见了」的假红。
- 判据：竖线取列 614、横线取行 1349 判 `#FF0000`（通道容差 ±2）；覆盖区间 `612..616 / 1347..1351` 只判「非背景」；overlay 态与 canvas 贴地态各测一次、逐值相等（±1 px）。
- 反例要求：把 crosshair 平移 3 px 重测必须 FAIL。

### 3.5 G5 canvas 不可用时回退（「失败要能看出来，不能静默」）

两态实测（原文取自 `E:\APPDetector\reports\M8-final-d60a61d\banner-compass.txt` 与 `banner-map.txt`）：

```
compass  ui=compass  sab=70  diag=fail:glinit  disp=block  bottom=722  txt=「贴地盘面未能绘制（fail:glinit）」
map      ui=map      sab=32  diag=fail:glinit  disp=block  bottom=760  txt=「贴地盘面未能绘制（fail:glinit）」
```

`722 = 800 − 70 − 8`、`760 = 800 − 32 − 8`，两条都精确吻合；文案含失败原因 `fail:glinit`，用户能看出「没画出来」而不是静默。

### 3.6 负向对照（没有它整张表不算数）

同一条静态门禁脚本，在两棵树上跑出相反结果（存档 `E:\APPDetector\reports\m8-ground-gate\`）：

```
green-ea641c8.log（实现树）  MAP_HTML 200,088 B
  [PASS] real setGround 内切 canvas 档   RHS: on ? 'canvas' : 'overlay'
  [PASS] real setGround 关闭回 overlay 档
  [PASS] tilecache policy 单测  RESULT: PASS fails=0

red-on-current.log（旧树）   MAP_HTML 196,790 B
  [FAIL] real setGround 内切 canvas 档  setGround 内没有 groundRenderer = 'canvas'（产品路径进不了 canvas 档）
  [FAIL] real setGround 关闭回 overlay 档
```

⇒ **「故意改坏 → 必须变红」成立**：旧树上正是这组断言红，才证明这条门禁不是空转。

---

## 4. 贴地是**新增能力**，不写「与原版 1:1」

**台账措辞一律按此**：贴地 canvas 通道是**我方按原版可观测行为实现的新增能力**，**禁止**写成「与原版 1:1」「完全一致」。

依据与边界：

1. **原版贴地截图至今未见**（判据表 §10 第 1 条自述）——没有原版贴地态画面，无法做 1:1 视觉对照；L2b 若拿不到，只判我方自洽并注明。
2. **原版口径有范围门控**（`symbols-stage1.md` §6.4）：`|lat| < 75°` 且 `zoom ≥ 6` 等条件；我方是否逐条对齐门控，需单独核，本文件不代结论。
3. **已知差（必须逐条记，不得省略）**：
   - **贴地态指针隐藏（B1）**：贴地时隐藏原生竖向指针，只保留地面盘面。这是**有意的可见差异**，不是缺陷。
   - **贴地盘面不跟随 `dialType` / `dialAlpha` 切换**：属**真缺陷**，下轮修。
   - **L2a 强检验（原生 `CompassView` 渲染 vs canvas）未做**：本轮只做了 G2-A1（与 800 素材基线逐像素），未做 A2 的「原生 vs canvas」强对照。
4. **逐帧动画不判**：设备无 `screenrecord`，只判稳态四角与像素（判据表 §10 第 2 条）。
5. **触控命中不判**：贴地盘面是否可点/可拖，本表不覆盖，另立判据。

---

## 5. 遗留收口（M7 结转的三项）

| 项 | M7 状态 | M8 处置 | 证据 |
|---|---|---|---|
| **账户页登录态** | BLOCKED（`pm clear` 清掉登录态） | **采证障碍已解除**：自注册账号登录后，`昵称`／账号·`uid`／`退出登录` 四节点齐，且坐标与原版登录态**逐点相同**（产品自量：五行落在 M6 锁定的 y 值上、行高均 79） | `E:\APPDetector\reports\m8-ink\account-after-login.xml`（10,033 B）、`account-after-login.png` |
>
> **技术总监裁定（2026-11-10，分两件事写，禁止合成一句）**：
> - **解除的是「采证障碍」**（M8 的「账户页登录态采证」这条达成）——**BLOCKED ≠ 仍在**。
> - **不是「四页 1:1 五判据复跑通过」**：`TEXT/BOUNDS/STEP/CARRIER/THEME` 没有在登录态下重跑。
> ⇒ 账户页定档：**「已采证，五判据待复跑」**；责任：**下一轮首项**；缺失证据：**一次登录态下的五判据读数**。
> ⇒ 计数栏写 **BLOCKED 0 / 待复跑 1**，**不得**写成「账户页已验」——那是拿「采到了证据」冒充「判据通过」。
| **about 落点 3px** | 已定位未修（`includeFontPadding`，公式少算 3px） | 复拍复核，见 `about-new.png`；**是否闭环以 LC客户端 的报告与 LC审核验 的复量为准**，本文件不代判 | `E:\APPDetector\reports\m8-ink\about-new.png`（83,755 B） |
| **`fig-32` 真展开态** | 图已撤下，记 CONFIRMED-MISSING | **已采到并走完五步**（LC客户端，分支 `feature/客户端-fig32-real` = `2eb18be`，基于 `73886ed`）：①「读盘」按**代码布局值** `(106,2114)` 进入罗盘读盘弹层；②点「地盘正针二十四山」行体 `(506,1706)`；③dump 证明**七行里正好这一行**展开——`地盘正针二十四山 [54,1670][751,1743]` + `向：子山　坐：午山 [751,1675][1174,1738]`，其余六行仍是「点击查看 ›」 | 落树后 `lc-help` **28→29**、`index.html` 重生成（`@6a3ebdd9`）、尺寸规则 **6→7**；采集件 `fig-32.webp` 1228×2700 / 109,944 B / `sha256:32F93E87…` |

> **帮助中心覆盖口径（不写「29 张全过」，写「29 张全部受检，分类 22+6+1」）**：落树后 29 张 = **22 张按默认 `--expect-size 1228x2700`** + **6 张裁切按 `evidence-size-map.json`** + **1 张 `fig-32`（整屏，但显式入表）**；尺寸规则 **7 条 = 6 裁切 + 1 显式整屏**，覆盖 **29/29**。
> `fig-32` 走的是**显式表条目**（`"…/lc-help/fig-32.*": "1228x2700"`，实测在 `2eb18be` 的 `tools/fidelity/evidence-size-map.json` 第 8 行），**不是默认值**——这正是「入表才算专门验过」的意义。
> **工具边界（我实测，待 LC测试 补检查）**：尺寸规则**指向不存在的文件时，既不比也不报**（无 WARN / 无 FAIL / 无 unused-rule 输出）。复现：
> ```
> {"…/lc-help/fig-32.*": "1228x2700"}  +  --expect-size-map <该表>
> → 输出中 fig-32 零提及
> 对照：{"…/lc-help/fig-08.*": "999x999"} → FAIL png fig-08.webp: declared 999x999, actual 1228x2700
> ```
> ⇒ 「先加表条目、后补图」的顺序下，该条目是空转；**只有图入册之后再加条目才有判据意义**。工具补「规则命中 0 文件 → WARN/FAIL」之后，本表才能加一句更硬的 **「规则命中数 7/7，无空转条目」**——到那时「29 张全部受检」才是数得出来的，而不是一句话。这条与 `--expect-size` 空转同类：**声明与实际对象没对上，检查就静默通过**。
> **顺带更正我自己的旧判断**：M7 时我写过「尺寸表留着 `fig-32` 会报『声明了但文件不存在』」——实测**不会报**，那句作废。

> 账户页那条是 M7 done-when 的硬项，现已从 BLOCKED 转为有实测证据；但**「已登录态 dump 存在」≠「四页 Δ 判据全过」**，逐条判据仍归 LC审核验。

---

## 6. 后端与账号的记录方式（**只记方法，不写任何明文**）

**开发阶段后端跑在本机 Docker，数据 volume 持久化，不把依赖装到宿主机。**

```
backend/docker-compose.yml（develop 上已跟踪）
  service api / image lc-backend:local / container lc-backend
  ports   127.0.0.1:8787:8787        ← 只绑 loopback，不对外
  volume  lc-data:/data              ← 持久化
  env     DB_PATH=/data/lc.sqlite    PORT=8787
          LC_TOKEN_PEPPER=${LC_TOKEN_PEPPER:-}    ← 为空时服务端自生成 /data/.token-pepper（0600）并跨重启复用
  healthcheck  GET /health
```

**账号记录方式（用户 2026-11 定，本轮只落方法）**

| 项 | 规则 |
|---|---|
| 原 APP 账号 | **用户代为登录**，我方不索取、不保存密码 |
| 新 APP 账号 | **我方自行注册、自行记录**；账号密码**只许写入仓库外 secrets 目录** |
| 实际落点 | `E:\APPDetector\secrets\lc-m8-account.txt`（**245 B**；本文件只记文件名与字节数，**不引内容、不打印明文**） |
| 禁止 | 不入仓库、不在群内打印明文、不写进任何台账或证据文件 |
| token/pepper | 优先走 secrets 挂载（compose 内已留 `secrets:` 注释位），env 仅作本地回退 |

---

## 7. 证据索引（绝对路径）

| 路径 | 内容 | 说明 |
|---|---|---|
| `E:\APPDetector\reports\M8-final-d60a61d\` | 19 文件：`app-debug.apk`（12,360,834 B）、`compass-ground-on.png/txt`、`compass-pre.txt`、`banner-compass.png/txt`（722）、`banner-map.png/txt`（760）、`g2-final.txt`、`g2-result.txt`、`g2-result2.txt`（作废的 549 空间读数）、`g2-state.png` 等 | 收官树 `d60a61d` |
| `E:\APPDetector\reports\M8-g2-final\` | `g2-final-1456.txt`（**有效 G2 读数全文**）、`ground-state.png` | 1456 空间有效版 |
| `E:\APPDetector\reports\M8-device-175d1fb\` | 25 文件：`compass-ground-off/on.png`、`compass.xml`、`g5-compass-inj*.png/txt`、`g5-compass-reset.png`、`after-tap-ground.txt` | 贴地开关与 G5 注入态 |
| `E:\APPDetector\reports\m8-ground-gate\` | `green-ea641c8.log`、`red-on-current.log` | **负向对照**绿/红两份 |
| `E:\APPDetector\reports\m8-ink\` | 22 文件：`account-before/after-login.png/xml`、`login.png/xml`、`about-new.png`、`more-panel.xml`、`compass-mode-70.png` | 账户页登录与 about 复拍 |
| `E:\APPDetector\wt\lcs-verify-stage4\reports\M8\` | LC审核验 的贴地验收现场（四项独立复核） | 签字方自己的取数 |
| `docs/fidelity/m8/ground-drape-acceptance.md` | 判据表（333 行 / 20,704 B） | 判定规范 |
| `docs/qa/m8-ground-device-method.md` | 真机操作方法 | 复现前提 |

---

## 8. 未覆盖 / 待补

<<<<<<< HEAD
- **门禁与七门 smoke 在 `1bb650c` 上由 LC测试 执行（本节留槽，不预填结果）**：命令 = 七门 smoke + 四道门禁，其中 ② 必须带双参数 `--expect-size 1228x2700 --expect-size-map tools/fidelity/evidence-size-map.json`，按 **29 张 + 7 条规则**判；证据头须含：顶点 sha、`app` 树、APK 字节+`sha256:`、图数 29、规则数 7、覆盖情况（22 默认 + 6 裁切 + 1 显式）、以及**规则命中数 7/7**（待工具补「命中 0 文件即 WARN/FAIL」后可写）。**输出未到之前本文件不写任何门禁结论。**
=======
- **门禁是五道，不是四道（技术总监 2026-11-10 纠正）**。原文：任务定义里 ③ = `evidence-lint --root docs/fidelity`；而 LC测试 首版记录里 `GATE3_CMD` 写的是 `fidelity.py report --root docs/fidelity`——**两套检查，都 exit 0，但验的不是同一件事**（前者扫 144 个文件查重复与 allowlist，后者比 27/65 对图并校验 checksheet_id）。按定义 ③ 在首版记录里**没跑过**，故 `fidelity.py report` 单列为 **③b**，不顶替 ③：

```
① assets.py verify                          ⑤ 之外的第一道
② evidence-lint --root …/lc-help（双参数）
③ evidence-lint --root docs/fidelity        ← 任务定义里的那道
③b fidelity.py report --root docs/fidelity  ← 额外那道，单独编号，不顶替 ③
④ help sha（--generated-html + --source）
```

- **七门 smoke（LC测试，`d260b78`，`pwsh -NoProfile -File scripts\smoke.ps1 -SkipDevice`，exit 0）**：`copy / secret / desktop / numeric（85 tests，0 fail）/ mech-src / android / ground` 全 PASS；`desktop`：`cargo_exit=0`、png `1100×780` / 266,376 B、`mechanics_values=19`、`estimated=0`；`android`：`assembleDebug BUILD SUCCESSFUL in 43s`；`ground`：`M8-GROUND PASS`、`NEGATIVES PASS（N0–N5）`。
- **门禁包（门禁副产物，非关账基线）**：`12,470,938 B` / `sha256:123e75f1965fd2c0dcc1a73b0251403c2fd5081f28745dc66d31f636433c2ea3`；原件 `E:\APPDetector\reports\m8-gates-d260b78\exit-codes.txt`（5,525 B、LF、无 BOM）。
- **四道门禁我在 `d260b78` 干净树（`git status --porcelain` = 0 行）上本人复跑，exit 全 0**：

```
① python tools/fidelity/assets.py verify
   [PASS] verify: 103 file(s), 103 manifest entr(ies), 103 provenance row(s); sha256 + provenance + APK source all match   exit=0
② python tools/fidelity/evidence-lint.py --root app/android/app/src/main/assets/lc-help \
     --expect-size 1228x2700 --expect-size-map tools/fidelity/evidence-size-map.json
   SIZE-MAP: 7 rule(s)
   SIZE-MAP-COVERAGE: rules 7/7 matched; files by rule=7 by default=22
   DUP: files=29 unique=29 duplicated=0 redundant=0
   EVIDENCE-LINT: scanned xml=0 png=29 other=1 fail=0 warn=0 allowed=0        exit=0
③ python tools/fidelity/evidence-lint.py --root docs/fidelity
   SIZE: SKIP (no --expect-size and no --expect-size-map: images are NOT size-checked)
   DUP: files=138 unique=136 duplicated=4 redundant=2
   EVIDENCE-LINT: scanned xml=155 png=138 other=149 fail=0 warn=0 allowed=3    exit=0
   ↑ LC测试 按定义补跑后与我本人这次输出**逐项相同**（138/136/4/2、155/138/149、fail=0 warn=0 allowed=3），互证成立。
   注：③ 里出现 `SIZE: SKIP` 属**设计如此**——尺寸检查在 ② 用双参数做，③ 不做尺寸；这行是「不静默冒充通过」的提示，不是漏检。
③b python tools/fidelity/fidelity.py report --root docs/fidelity   （LC测试 记录）
   27/65 pair compared，26 对登记 known-red 未比较，checksheet_id 29/29        exit=0
④ python tools/fidelity/evidence-lint.py --generated-html …/lc-help/index.html --source docs/product/help-center.md
   ROOT: SKIP evidence scan
   EVIDENCE-LINT: scanned xml=0 png=0 other=0 fail=0 warn=0 allowed=0          exit=0
   头部 @ 6a3ebdd988178d52fd0b1d7355f92228ac0833fb == md blob 6a3ebdd9…
```

- **④ 的正向输出只有 `fail=0`，不能自证它比了什么** —— LC测试 补了两次反证才敢判绿：改坏头部 sha → `FAIL generated … stale/mismatched (regenerate)` `exit 1`；删掉 `GENERATED` 整行 → `FAIL … missing header` `exit 1`。**且第一次反证是假绿**（替换模式漏了 `@` 后的空格，文件根本没被改到），加 `CHANGED=True` 断言后才变红——这与「参数给了没执行」「规则指向不存在的文件」同族，已记入 `exit-codes.txt` 第三节。

- **「覆盖」现在能数出来，不再是一句「全过」**：`rules 7/7 matched` + `files by rule=7` + `by default=22` ⇒ **29 = 22 + 6 + 1**，其中 `fig-32` 走显式表条目。
- **空转检查已有反证（我本人跑）**：把规则指向不存在的 `fig-99.*` → `SIZE-MAP-MISS … <- (0 files)` + `FAIL size-rule … declared-but-never-compared (idle rule)`，`exit=1`。⇒ 「无空转条目」这条现在有判据撑着。
- **③ `other=149` 是本次实测值**，与 M7 时期的 `147` **不可比**（`docs/fidelity` 内容已变）；判据只认 `fail=0` 与 `allowed=3`，`other` 只作参照。
>>>>>>> 1dea29c (docs(fidelity): M8 关账摘要 v1.4——门禁改记五道(③ evidence-lint + ③b fidelity.py 分列)；补七门 smoke/门禁包 sha256/④ 两次反证与假绿记录 [LC审成文])
- **终态表第 7 条已改判**：`docs/product/m7-eight-final-states.md` **v1.5（`33cb119`，`blob:08fa04beca07d0e2723bba154d23f617b8e51274`，24,949 B / 187 行 / CR=0）**把第 7 条从「未实现」改为 **已验**，并附 G1/G2-A1/G3/G4/G5⑤ 读数。**规范文本以该表为准**，本文件只做索引。
- **八条计数（按终态表 v1.5/v1.6）**：已验 **8**｜未实现 **0**｜原版不可得 **0**｜**BLOCKED 0 / 待复跑 1（账户页五判据）**｜已知差 **3 项**｜证据不可得 **1 项（3D 逐帧）**｜未做 **1 项（L2a）**。
- **账户页已按技术总监裁定分两件事写**（见 §5）：采证障碍**已解除**，但五判据**未在登录态下复跑** ⇒ 记「已采证，五判据待复跑」，**不写「账户页已验」**。
- **L2a 强检验未做**（原生 `CompassView` vs canvas），记下一轮。
- **贴地盘面不跟随 `dialType`/`dialAlpha`** 属真缺陷，下轮修，不改判据。
- **未由我复测**：G1/G2/G3/G4 的读数来自 LC审核验／LC测试 真机执行；我只核了「文件在不在、树对不对、参数齐不齐」，**没有独立重跑**。
- **未改**：`help-center.md`、`docs/review/`、`docs/qa/baseline-android/` 一律未触碰；本文件为新增文件。

---

## 9. 时间戳

- 开工 **00:30** / 收工 **00:4x**（Asia/Shanghai）｜返工轮次 **0**
- 本文件不含二进制、不含构建产物、不含任何凭据明文；哈希一律带 `blob:` / `sha256:` 前缀。
