# M8 收官索引（新会话从这里开始）

> 只做导航，不复制内容。三行制：结论 / 证据 / 下一件事。细节一律读被指向的文件。

## 结论
- 八条终态：`docs/product/m7-eight-final-states.md`（**树内 v1.17**，已验 8 / 未实现 0）—— BLOCKED 0 / **待复跑 1**（账户页五判据）。
  - 注：该文件头部版本行曾停在 `v1.5`（与其自身变更记录不一致），读版本**以变更记录最后一行 v1.17 为准**；产品已改头部。台账「版本/计数/口径」只留一个权威位置，别处只引用。
- M8 主线（罗盘贴地）已交付，第 7 条判「已验」；**贴地是新增能力，不写「与原版 1:1」**。

## 关账与判据
- 关账：`docs/fidelity/m8/m8-close-summary.md`（五道门禁 + 七门 smoke 原文、已知差、Docker 与账号记录方式）。
- 判据：`docs/fidelity/m8/ground-drape-acceptance.md`（G1–G5、N1–N7）。

## 证据（绝对路径）
- 门禁：`E:\APPDetector\reports\m8-gates-d260b78\exit-codes.txt`（五道退出码 + 参数行；含第一版错记 `fidelity.py` 为 ③ 的更正）。
- 真机读数：`E:\APPDetector\reports\M8-final-d60a61d\`。
- 账户页材料：`E:\APPDetector\reports\m8-ink\`。

## 未结（唯一待办：账户页五判据 + 终验签字）
材料同上；`verify_bounds.py` **只打印 bounds、不判定**，跑它不等于有结论。

| 判据 | 文件 | 判什么 | 缺什么判定逻辑 |
|---|---|---|---|
| BOUNDS | `account-after-login.xml` | 五行 y / 行高 / 标题对 M6 锁定值 | 容差定义（Δ≤2 是否适用） |
| STEP | 同上 | 相邻行步进 | 步进取法（行差 vs 行高） |
| THEME | `account-after-login.png` | 底色众数 | 取样框是否与 M6 同区 |
| CARRIER | `account-before.xml` 对比 after | 未登录→已登录状态判定 | 关键词差集口径 |
| TEXT | `account-after-login.xml` | 文案逐字核对 | 数据齐，需逐字核 |
| 红按钮 | `account-after-login.png` | 贴底红按钮取色 | 像素判据与阈值（dump 读不到颜色） |

- 规矩：判据与签字**由 LC审核验 自己写、自己跑**；实现方不自签，工具输出范围不等于判定结论。

## 凭据边界（别写成「零命中」）
- M8 账号名 / 密码入库命中 = 0；但**仓库内并非无密码字面量**：`tests/e2e/run-m4-e2e.ps1:31` 有 `'LcE2E!' + random` 模板。
- 判定：M4 随机一次性测试账号（账号名亦随机、后缀随机），无法直接使用，风险低、本轮不改，已记档；改它会作废该条 e2e 既有结果。

## 其它
- 已知差 3 项 / 证据不可得 1 项（3D 逐帧）/ 未做 1 项（L2a）：见关账 §4、§8。
- 下一轮首项：账户页五判据复跑；贴地盘面不跟随 `dialType`/`dialAlpha` 为真缺陷，归 LC地图。
