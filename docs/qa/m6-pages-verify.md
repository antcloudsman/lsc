# M6 第 4 条：四页 1:1 复验工具与基线（Stage 3）

## 工具（可复跑）

```powershell
pwsh -NoProfile -File tests\e2e\verify-m6-pages.ps1 -Device L2E0222531014833
# 输出：checks\m6-pages-<时间戳>\act-<页>.xml/.png、compare.log、run.log
```

- 逐页：`am start MainActivity` → 点 ≡ 打开抽屉 → 按 `lc_drawer_item_N` 进入 → `uiautomator dump --compressed` + `screencap` → 返回 → 比对。
- 比对 `checks\compare_page.py`：按文本多重集配对，逐节点算 bounds 四边 Δ；**`missing>0` 或 `max_bounds_delta>2` 即 FAIL**；`--step` 输出相邻 y 步进中位数。
- 色值 `checks\color_palette.py`：截图调色板 top-N 对照（**辅助项**，圆角/色值只能靠像素）。
- **几何唯一来源 = dump 的 bounds**；像素拟合只作辅助（583×111 vs 157 的教训：拟合值不得进判据）。

## 参照文件

```
账户设置  docs/fidelity/m3/account-setting.xml/.png
投诉反馈  docs/fidelity/m3/complaint.xml/.png（一级）；complaint-form.xml（表单）
关于我们  docs/fidelity/m3/about.xml/.png；about-links.png / about-after-swipe.xml（链接区）
联系我们  docs/fidelity/m3/contact.xml/.png（Dialog：根 FrameLayout、无标题无返回、3 个文本）
```

## 基线（当前机上构建 `81e6a44`，四页仍是深色整页——预期失败，用于证明工具能 FAIL）

| 页 | 结果 | max_bounds_delta | missing | step_y 中位 |
|---|---|---|---|---|
| account（账户设置） | FAIL | 1663px | 10 | 2387 |
| complaint（投诉反馈） | FAIL | 965px | 1 | 189.5 |
| about（关于我们） | FAIL | 1959px | 0 | 158 |
| contact（联系我们） | FAIL | 1059px | 0 | 149 |

说明：
- 基线跑在客户端四页改动的**分支之前**（机上为 `81e6a44` 构建），不能作为「改后未达标」的证据，只证明工具在缺陷版本上会 FAIL。
- `step_y` 中位数受 matched 节点分布影响，只作参考；正式判据按第 4 条：**账户 163±2、投诉 180±2、关于顶部 95±2**。
- 像素调色板对「整页截图含状态栏/头像」会误选邻近色，因此只做辅助，判色值必须三点取样（底/卡/字）。

## 结论（工具侧）

工具已可用；下一步等客户端 `feature/client-m6-four-pages`（`90cc4d6`/`6d6c6ee`/`e9adce7` 及后续居中/贴底按钮）合入或给出构建，按同一脚本跑正式 Δ 表。

## `6ddc747`（四页全屏窗口）复测结果

构建 sha `1a95e36d…`，装机 `lastUpdateTime 03:51:xx`、`MARKET_RESTORED=ok`。

**红按钮 + 窗口（按像素判）：**
```
红底 bbox   [39,2512][1188,2635] = 1150×124   （容差 20/40/60 三档结果一致，含抗锯齿）
导航区       y2578..2699 深色像素 = 0          （无导航图标）
导航区主色   #F0F3F7 83,162 px；#E93B4D 65,328 px（红条下探）；白 726 px
root bounds dump [0,0][1228,2578]              与原版 contact/account dump 相同（都是内容帧）
```
→ **红按钮几何/颜色/窗口这三项与 `1150×124 @(39,2512)` 原版一致，PASS。**

**共有节点 Δ（仍 FAIL，属客户端的第 7 笔范围）：**
```
account   标题「账户设置」ΔL=-331 ΔR=+465（我方标题节点整宽 [175..1187]，原版 [506..722]）
          「退出登录」ΔL=-484 ΔR=+483（文字节点=整按钮宽 [39..1189]，原版 [523..706]）
          原版独有 10 行（昵称/头像/绑定手机/去绑定/绑定微信/注销账户/账户ID/340992）→ 按裁定记 DI
          我方独有 6 项（账号/uid/修改密码/同步收藏点/说明/返回）→ 只判样式，不判文案
complaint 「问题反馈」ΔT=+141 ΔR=+944、「订单投诉」ΔT=+143 ΔR=+615（我方整行通栏）
          「我的」ΔL=-881 ΔR=-880（我方左上 [175..333]，原版右上 [1056..1213]）
          步进 180 ✓
about     整体偏高 700–1950px：标题块 ΔT=-736、公司块 ΔT=-739、底部三段 ΔT=-1888/-1869/-1954
contact   载体一致 ✓、蓝填充 583×111 ✓、步进 149 ✓
          「复制邮箱」ΔL=-198 ΔR=+197（我方文字节点=整按钮宽 [322..905]，原版 [520..708]）
          标题 ΔL=-7 ΔR=+5；正文 ΔL=-3 ΔR=+2；多一个 `×` 文本节点（原版关闭是图标无文本）
```

**Δ 表按三类判**：共有节点按 Δ≤2 判；原版独有行记 DI；我方独有行只判几何样式。红按钮/窗口三项已按像素判 PASS，其余留待第 7 笔（文字 wrap_content、投诉「我的」位置、about 垂直分布、contact 去 `×`）。

## `8f1194b`（第二十一笔）复测：**BLOCKED，设备不在线**

```
分支    feature/client-m6-four-pages = 8f1194b3e4da（21 笔）
构建    pwsh -NoProfile -File scripts\build-android.ps1 -SkipDevice → BUILD SUCCESSFUL in 30s / [PASS] assembleDebug OK, 8399 KB
APK     sha256 353e39be4ad4ff80…（8,600,393 B，mtime 11-08 09:36:10）
装机    pwsh -File tools\device\install-debug.ps1 → M6_INSTALL_RECORD FAIL exit=0 taps=0 market=ok sha=353e39be4ad4
adb     adb devices → List of devices attached（空）；Get-PnpDevice 无 ADB/Android/HUAWEI 设备
```

→ **本机 USB 上没有 L2E0222531014833，`8f1194b` 的四页 Δ 表尚未产生任何有效数据**。
09:39 那次 `checks\m6-pages-20261108-093917` 的 account/complaint 数字**不得引用**：装机 `taps=0`（无设备）后脚本仍继续 dump，`step_y=2387`、`找不到条目 lc_drawer_item_3`、`打不开抽屉` 都是设备不在场的产物。

**静态核对（只看各笔 diff，不看合并后现状）——第 7/8 笔待验的五项在 `8f1194b` 里都有代码**：

| 项 | 落点 | 静态证据 |
|---|---|---|
| 标题 `wrap_content` + 居中 | `66da868` | `M3PageActivity:138` 标题栏 `FrameLayout`；`:162-165` 标题 `WRAP_CONTENT` 居中 |
| 投诉行文本 `wrap_content` | `66da868` | `:803/:838` 行内 `LinearLayout` + `WRAP_CONTENT` 标签 |
| 「我的」右上 | `bbe7cc4` | `:187-188` `FrameLayout.LayoutParams(157,157)` 右侧 |
| contact 去 `×` 文本 | `bbe7cc4` | `M3PageActivity` 8+/7− |
| `退出登录` 拆容器+文字 | `8d85733` | `:270-287` `bar2` + `blp2`，文字 `WRAP_CONTENT`，`bottomMargin=64` |
| about 绝对定位 | `8d85733` | `:921-988` `stage` `FrameLayout` 按 dump 绝对 y 落位 |

静态只能证明「代码在」，**不能代替 Δ 表**：标题是否真落在 216 宽、about 各块 y 是否落到 995/1090/2331/2489/2496、`退出登录` 文字是否 183 宽，都必须设备 dump 才认。
设备回来后一条命令即可续跑：

```powershell
pwsh -NoProfile -File tests\e2e\verify-m6-pages.ps1 -Device L2E0222531014833
```