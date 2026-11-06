# main 绿线记录（2026-11-06，第一批）

- 出：LC服务端｜目的：把「`main` 只收绿」真正落地——先在一个确定提交上跑全量门禁，再把该提交快进合入 `main` 并打 tag
- 合并前基线：`develop` @ **`1e91bba`**（`merge(product): TEAM_CHARTER §9 工作流裁决 + FINAL §6 基线重冻状态`）
- `main` 原位置：`4770c20`（项目基线，落后 develop 444 个提交）→ 本批**快进**到 `1e91bba` 之上（本文件提交后）
- 执行：2026-11-06 12:17–12:21（Asia/Shanghai），独立 worktree `E:\APPDetector\wt\lc-server`（按章程 D-007，不在共享工作树提交）
- 判定：**PASS**

环境：Windows｜JDK `17.0.21`（Temurin）｜Gradle `8.7 --no-daemon`｜Python `3.13.14`｜cargo `1.99.1`
真机 leg：跳过（`-SkipDevice`）｜天地图 key：仓库外 secrets 文件构建期注入，日志不含 key 值

## 1. 门禁结果

| # | 项 | 命令 | 结果 |
|---|---|---|---|
| 1 | 文案/红线 | `python tests\copy\copy_gate.py` | `COPY: PASS`（3/3） |
| 2 | 密钥泄露 | `python tests\secrets\secret_gate.py` | `SECRET: PASS`（2/2） |
| 3 | 一键六门禁 | `scripts\smoke.ps1 -SkipDevice` | `SMOKE: PASS`（6/6，`overall : PASS`） |
| 4 | 数值回归 | `python tests\numeric\run_all.py` | `tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0` |
| 5 | 桌面单元测试 | `cargo test`（`app\`） | `test result: ok. 5 passed; 0 failed` |

### 1.1 原文（关键行）

```
[PASS] copy: 用户可见文案零命中 会员|付费|VIP|开通|升级|订阅|解锁|充值|去水印（28 个 values/*.xml，注释已剔除）
[PASS] audi: 发行面无原版审图号（扫 133 个已跟踪文件，注释与 docs/ · tests/ · *.md 已豁免）
[PASS] tiles: 发行面无原版私有瓦片域 21atcloud / xinzhi.space（扫 133 个已跟踪文件，注释与 docs/ · tests/ · *.md 已豁免）
COPY: PASS

[PASS] key: 2 个 key × 385 个已跟踪文件，零命中
[PASS] hex: assets/*.html 与 *.java 无 32 位十六进制串
SECRET: PASS

copy     : PASS
secret   : PASS
desktop  : PASS
numeric  : PASS  (tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0)
mech-src : PASS
android  : PASS  (build only, device skipped)
overall  : PASS
SMOKE: PASS
```

门禁内部：`[PASS] cargo build exited 0`、`[PASS] screenshot 1100x780, 270394 bytes`、
`[PASS] mechanics.json: 19 values, 0 estimated, 0 duplicate names`、`PASS Rust/Java generated region is up to date`、
`BUILD SUCCESSFUL in 35s`、`[PASS] assembleDebug OK, 6894 KB`。

## 2. 产物

| 类型 | 文件 | 字节数 | sha256 |
|---|---|---|---|
| debug | `app-debug.apk` | 7,059,485 | `651721BCF9A7CFD78C80091F3898F64FAA7ADD73BDF65D37ED643F973B4FE299` |

- release 本轮**未重打**（未配签名，见 §4）；上一轮收官留档的 release 哈希见 `docs/qa/final-gates-20261106.md`。
- APK 在 `app\android\app\build\outputs\`（git-ignored），不入库；本文件只留字节数与哈希。
- 哈希对应本次构建产物，重打会变（含签名/时间戳），不作可复现校验。

## 3. 合并与标记

- 合并方式：`main` 是 `develop` 的祖先 → **`git merge --ff-only`**（无 merge commit，历史线性）
- 标记：附注 tag **`v1.0.0`**，message 记录基线提交与本文件路径
- 推送：`develop`、`main`、`v1.0.0` 三者同源（同一提交）

## 4. 口径与边界

1. **本轮只证明「自动化门禁在 main 上是绿的」**：`-SkipDevice`，不含真机启动/读数；真机证据仍以 18 项验收文件为准。
2. `main` **没有分支保护**：远端仍可直推；真正"只收绿"还靠人跑门禁 + 后续 CI（第二批次）。
3. release **未签名**：`assembleRelease` 出的是 unsigned 包；正式分发前需配 signingConfig（待拍板）。
4. 本文件是 docs-only 改动，合并进 `main` 不改变任何门禁输入；文案/密钥两门禁在提交后复跑过（见 §1）。
5. 复跑：`powershell -File scripts\smoke.ps1 -SkipDevice`；个别门禁单跑见 `scripts/README.md`。

## 5. 后续批次

- 批 2：GitHub Actions（可信 job 带 secrets 跑六门禁；fork PR 用 `--allow-missing` 降级）
- 批 3：gradle wrapper + JDK 约束，换机器可复现
- 批 4：后端 selfcheck 进 smoke（第 7 门禁）+ 备份/恢复
- 批 5：流程纠偏（`AGENTS.md` 指向、worktree 纪律）
