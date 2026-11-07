# M6 装机记录（统一走 `install-debug.ps1`）

- 适用范围：M6 期间所有成员的真机装包（`L2E0222531014833`）。
- 唯一入口：`tools\device\install-debug.ps1`（脚本内临时禁用 `com.huawei.appmarket` 并在 `finally` 恢复）。
- 记录脚本：`tests\e2e\m6-install-record.ps1`，每次装包产出一份 `RECORD.txt` + `record.json`。

## 命令

```powershell
pwsh -NoProfile -File tests\e2e\m6-install-record.ps1 -Apk <apk 绝对路径> -Device L2E0222531014833
# 记录默认写到 checks\m6-install-<时间戳>\（checks 已 gitignore；正式证据复制到 reports\M6-install\<成员>-<主题>\）
```

退出码：`0` 全部通过；`1` 有检查项不满足（记录仍写盘）；`15` = `MARKET_RESTORE_FAIL`，**停机上报，不许继续装**。

## 记录字段（每条必带）

| 字段 | 含义 | 通过条件 |
|---|---|---|
| `apk_sha256` / `apk_size` | APK 指纹 | 与交付声明一致 |
| `lastUpdate_before/after` | 装前/装后 `lastUpdateTime` | 两者不同（证明真的替换了包） |
| `install_exit` / `taps` | 脚本退出码 / 「继续安装」点击数 | `exit=0` 且 `taps≥1` |
| `market_restored` | 市场恢复结果 | `ok`（`skipped` 只在装前已被别人禁用时出现，需人工确认） |
| `market_disabled_after` | 装后 `pm list packages -d` 是否还有市场 | `False` |
| `package_listed` | `pm list packages com.luopan.fengshui` | `True` |

## 本机样例（2026-11-08 01:52，develop `0879355` 构建）

```
apk_sha256=39df3bc18e8440ae4ea45bc72fb2cbcdfe824a589b9a37280c6a7e5bba6f2a47
apk_size=8853263
lastUpdate_before=2026-11-07 23:33:15
lastUpdate_after=2026-11-08 01:52:28
install_exit=0 taps=2 duration_s=15
market_restored=ok market_disabled_after=False
package_listed=True
RESULT=PASS
```

说明：该 APK 由 develop `0879355` 构建；`git diff --stat 9e42ef1 origin/develop -- app/` 为空（36 笔提交全部是文档/证据），Gradle 判定 up-to-date，产物与 `9e42ef1` 同一 sha，属预期。

证据：`E:\APPDetector\reports\M6-install\`（`RECORD.txt`、`record.json`，含 install-debug 全程输出）。

## 纪律

1. 装包一律走本脚本，**不手动点确认**；成员贴出的装载结果必须带 `RECORD.txt` 路径与 `sha256`。
2. 出现 `MARKET_RESTORE_FAIL`（exit 15）立即停机上报，先恢复市场（`pm enable com.huawei.appmarket`）再谈下一次装包。
3. 同一构建重复安装也要留记录（`lastUpdateTime` 会变，可证明替换发生）。
4. 记录里不写凭据（PIN 不进日志；脚本本身也不打印）。
