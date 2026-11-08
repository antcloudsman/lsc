# M7 分支清理台账（删除前留痕）

> 执行：LC审成文　｜　日期：2026-11-08　｜　基线：`origin/develop` = `94a50c37b93f4075bcf1949c9e5b7d1cfe95cdb6`
> 依据：技术总监 2026-11-08 裁定——「每条先验证已合入，再删；删前把每条的分支顶 sha 记进台账，一行一条；只删已合入的 7 条，`review/*` 不动」。
> 目的：分支删了提交仍在，但「该分支当时指到哪个 sha」只存在于分支上；本表就是那行记录，供以后回答「那笔真的合了吗」。

## 1 删除清单（7 条，全部 `merge-base --is-ancestor` = 0）

| # | 分支 | 分支顶 sha（删除前） | 已合入 develop | 内容 |
|---|---|---|---|---|
| 1 | `feature/成文-charter-tech-discipline` | `8fea8293390180f44ed5fce3c15902d8bd2f580b` | ✓ exit=0 | CHARTER §10 技术纪律 T-01~T-04 |
| 2 | `feature/成文-m6-archive` | `b246d82d11d90908e4ad71d315a5187911e539c3` | ✓ exit=0 | `docs/m6/00-ARCHIVE.md` 归档索引 |
| 3 | `feature/成文-m6-eol-precision` | `e4e4342cd6affccc91ab017b6769aa704e533e6e` | ✓ exit=0 | `--eol` 必须显式文件列表（裸 `--eol` → SKIP） |
| 4 | `feature/成文-m6-evidence-lint` | `86677df76f5aace0e61f12bb645640bfcb98c26e` | ✓ exit=0 | 具名例外清单 + `evidence-lint` 能力 |
| 5 | `feature/成文-m6-lint-empty` | `1a054e6b788eb9bfd24f7266e7c6c049d4561460` | ✓ exit=0 | 空 dump 判 FAIL + `--root` 默认值 |
| 6 | `feature/成文-m6-lint-gate-eol` | `479c10ef50f7ea0d13722deb07ef217d909adec1` | ✓ exit=0 | 生成物 sha 门禁上主线 + `--eol` 首版 |
| 7 | `feature/成文-round2-report` | `f51a2e63606522fdfcb65d0af520db1241dfa380` | ✓ exit=0 | 第二轮复审报告 v1.0.5 |

## 2 复跑命令（任何人可重算）

```bash
# 逐条验证已合入（期望 exit=0）
git merge-base --is-ancestor origin/<branch> origin/develop; echo $?
# 再看该分支顶 sha 是否就是本表记录
git rev-parse origin/<branch>
```

## 3 不删的分支（留作判词锚点）

`review/delta-client`、`review/delta-map`、`review/verify-round2`、`review/fidelity-client`、`review/s4-client`、`review/m4-client` 等 `review/*` 分支：**保留**，关账表还要引用它们的判词提交。

## 4 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-08 | 建表：7 条已合入分支的顶 sha 与合入校验结果；随后执行删除（逐条 `git push origin --delete`）。 |
| 2026-11-08 | 新增 §5「内容已合、sha 未进图」；`feature/地图-c1-bclass` 按内容判定已合入后删除，顶 sha 留存于该节。 |

## 5 内容已合、分支 sha 未进图（按内容比对判合入）

判定方法（**不要只看 sha**）：先比内容，再看图关系。

```bash
git diff --stat origin/develop <sha> -- <涉及文件>   # 空 ⇒ 内容已完全一致
git merge-base --is-ancestor <sha> origin/develop     # 1 ⇒ 该 sha 本身不在 develop 图上
```

两者同时出现不是矛盾：**分支被 rebase 过，旧 sha 被新提交替换，内容却一样**。
只看 sha 会得出两个错误结论——要么以为丢了要重做，要么重复合一遍制造新冲突。

| # | 分支 | 分支顶 sha（删除前） | 内容比对（对 develop） | `merge-base` | 内容 |
|---|---|---|---|---|---|
| 1 | `feature/地图-c1-bclass` | `8915e0f` | `map.html` diff 为空 ⇒ 一致 | exit=1（sha 未进图） | C 轮 M-05 `emit`/`emitDiag` + M-07 DEM 失败原因链 |

复跑（2026-11-08，`origin/develop` = `518c99e`）：

```bash
git diff --stat origin/develop 8915e0f -- app/android/app/src/main/assets/map.html   # 无输出
git merge-base --is-ancestor 8915e0f origin/develop; echo $?                          # 1
git show origin/develop:app/android/app/src/main/assets/map.html | grep -c 'firstDemErr'  # >0
```

