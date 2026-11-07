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
