# LC 全量源码审查矩阵（审查基线）

> 本文件由技术总监基于**冻结基线**生成，是「全量覆盖」的基准。任何审查报告必须能对照本表说明覆盖情况。

| 项 | 值 |
|---|---|
| 基线分支 | `develop` |
| 基线提交 | `d752efaf7936f887a70577681c703497e74e8b57` |
| 基线标签 | `review-20261105` |
| 基线工作区 | `E:\APPDetector\review-base`（独立 worktree，保证与开发并行不互相污染） |
| 跟踪文件总数 | 204 |
| 远端仓库 | https://github.com/antcloudsman/lsc |

## 1. 模块划分与审查人

| 模块 | 审查人 | 文件数 | 代码行数 | 说明 |
|---|---|---|---|---|
| M1 桌面客户端(Rust/egui) | LC审客户端 | 3 | 1276 | |
| M2 Android 客户端 | LC审客户端 | 25 | 1901 | |
| M3 地图/3D | LC审地图 | 3 | 1697 | |
| M4 服务端/基建 | LC审服务端 | 26 | 2812 | |
| M5 测试资产 | LC审测试 | 22 | 2974 | |
| M6 文档/契约 | LC审文档 | 42 | 3264 | |
| M7 数值档案/构建 | LC审文档 | 8 | 6334 | |
| M8 研究工件 | （不入审查，仅盘点） | 75 | 82258 | |

## 2. 审查维度 × 模块矩阵（每个模块至少覆盖 R 项）

| 模块 | D1 功能正确性 | D2 边界与异常 | D3 并发与资源 | D4 安全 | D5 性能与扩展 | D6 数据一致性与事务 | D7 错误处理与日志 | D8 接口契约一致性 | D9 可维护性与耦合 | D10 测试覆盖缺口 | D11 配置与部署风险 | D12 与关联文档一致性 |
|------|---|---|---|---|---|---|---|---|---|---|---|
| M1 桌面客户端(Rust/egui) | R | R | R | R | R | — | R | — | R | R | R | R |
| M2 Android 客户端 | R | R | R | R | R | R | R | R | R | R | R | R |
| M3 地图/3D | R | R | R | R | R | — | R | R | R | R | R | R |
| M4 服务端/基建 | R | R | R | R | R | R | R | R | R | R | R | R |
| M5 测试资产 | R | R | — | — | R | R | R | — | R | R | R | R |
| M6 文档/契约 | R | — | — | — | — | — | — | R | R | — | — | R |
| M7 数值档案/构建 | R | — | — | — | — | R | — | R | — | — | — | R |
| M8 研究工件 | — | — | — | — | — | — | — | — | — | — | — | — |

## 3. 全量文件清单（文件 × 模块 × 行数）

| # | 文件 | 行数 | 模块 | 审查人 |
|---|---|---|---|---|
| 1 | `app/src/dial.rs` | 254 | M1 桌面客户端(Rust/egui) | LC审客户端 |
| 2 | `app/src/main.rs` | 701 | M1 桌面客户端(Rust/egui) | LC审客户端 |
| 3 | `app/src/software.rs` | 321 | M1 桌面客户端(Rust/egui) | LC审客户端 |
| 4 | `app/android/app/src/main/AndroidManifest.xml` | 30 | M2 Android 客户端 | LC审客户端 |
| 5 | `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java` | 280 | M2 Android 客户端 | LC审客户端 |
| 6 | `app/android/app/src/main/java/com/luopan/fengshui/CompassListActivity.java` | 112 | M2 Android 客户端 | LC审客户端 |
| 7 | `app/android/app/src/main/java/com/luopan/fengshui/CompassReadSheet.java` | 95 | M2 Android 客户端 | LC审客户端 |
| 8 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java` | 358 | M2 Android 客户端 | LC审客户端 |
| 9 | `app/android/app/src/main/java/com/luopan/fengshui/DebugBridge.java` | 42 | M2 Android 客户端 | LC审客户端 |
| 10 | `app/android/app/src/main/java/com/luopan/fengshui/LCAndroid.java` | 66 | M2 Android 客户端 | LC审客户端 |
| 11 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 767 | M2 Android 客户端 | LC审客户端 |
| 12 | `app/android/app/src/main/java/com/luopan/fengshui/MapMarker.java` | 28 | M2 Android 客户端 | LC审客户端 |
| 13 | `app/android/app/src/main/java/com/luopan/fengshui/SensorMath.java` | 40 | M2 Android 客户端 | LC审客户端 |
| 14 | `app/android/app/src/main/res/drawable-nodpi/dial_full_0.png` | bin | M2 Android 客户端 | LC审客户端 |
| 15 | `app/android/app/src/main/res/drawable-nodpi/dial_full_1.png` | bin | M2 Android 客户端 | LC审客户端 |
| 16 | `app/android/app/src/main/res/drawable-nodpi/dial_full_2.png` | bin | M2 Android 客户端 | LC审客户端 |
| 17 | `app/android/app/src/main/res/drawable-nodpi/dial_full_3.png` | bin | M2 Android 客户端 | LC审客户端 |
| 18 | `app/android/app/src/main/res/drawable-nodpi/dial_full_4.png` | bin | M2 Android 客户端 | LC审客户端 |
| 19 | `app/android/app/src/main/res/drawable-nodpi/dial0.png` | bin | M2 Android 客户端 | LC审客户端 |
| 20 | `app/android/app/src/main/res/drawable-nodpi/dial1_min.webp` | bin | M2 Android 客户端 | LC审客户端 |
| 21 | `app/android/app/src/main/res/drawable-nodpi/dial2_min.webp` | bin | M2 Android 客户端 | LC审客户端 |
| 22 | `app/android/app/src/main/res/drawable-nodpi/dial3_min.webp` | bin | M2 Android 客户端 | LC审客户端 |
| 23 | `app/android/app/src/main/res/drawable-nodpi/dial4_min.webp` | bin | M2 Android 客户端 | LC审客户端 |
| 24 | `app/android/app/src/main/res/layout/activity_main.xml` | 50 | M2 Android 客户端 | LC审客户端 |
| 25 | `app/android/app/src/main/res/values/colors.xml` | 8 | M2 Android 客户端 | LC审客户端 |
| 26 | `app/android/app/src/main/res/values/ids.xml` | 5 | M2 Android 客户端 | LC审客户端 |
| 27 | `app/android/app/src/main/res/values/strings.xml` | 11 | M2 Android 客户端 | LC审客户端 |
| 28 | `app/android/app/src/main/res/values/themes.xml` | 9 | M2 Android 客户端 | LC审客户端 |
| 29 | `app/android/app/src/main/assets/map.html` | 1605 | M3 地图/3D | LC审地图 |
| 30 | `tools/map/run_at_p04.ps1` | 41 | M3 地图/3D | LC审地图 |
| 31 | `tools/map/run_td015_device.ps1` | 51 | M3 地图/3D | LC审地图 |
| 32 | `.gitattributes` | 2 | M4 服务端/基建 | LC审服务端 |
| 33 | `.gitignore` | 39 | M4 服务端/基建 | LC审服务端 |
| 34 | `app/android/app/build.gradle` | 38 | M4 服务端/基建 | LC审服务端 |
| 35 | `app/android/app/proguard-rules.pro` | 2 | M4 服务端/基建 | LC审服务端 |
| 36 | `app/android/build.gradle` | 4 | M4 服务端/基建 | LC审服务端 |
| 37 | `app/android/docker-compose.yml` | 12 | M4 服务端/基建 | LC审服务端 |
| 38 | `app/android/Dockerfile` | 20 | M4 服务端/基建 | LC审服务端 |
| 39 | `app/android/gradle.properties` | 4 | M4 服务端/基建 | LC审服务端 |
| 40 | `app/android/gradle/wrapper/gradle-wrapper.properties` | 7 | M4 服务端/基建 | LC审服务端 |
| 41 | `app/android/README.md` | 41 | M4 服务端/基建 | LC审服务端 |
| 42 | `app/android/settings.gradle` | 16 | M4 服务端/基建 | LC审服务端 |
| 43 | `backend/.dockerignore` | 6 | M4 服务端/基建 | LC审服务端 |
| 44 | `backend/api.md` | 269 | M4 服务端/基建 | LC审服务端 |
| 45 | `backend/docker-compose.yml` | 26 | M4 服务端/基建 | LC审服务端 |
| 46 | `backend/Dockerfile` | 22 | M4 服务端/基建 | LC审服务端 |
| 47 | `backend/package.json` | 15 | M4 服务端/基建 | LC审服务端 |
| 48 | `backend/README.md` | 80 | M4 服务端/基建 | LC审服务端 |
| 49 | `backend/scripts/selfcheck.mjs` | 145 | M4 服务端/基建 | LC审服务端 |
| 50 | `backend/src/db.js` | 298 | M4 服务端/基建 | LC审服务端 |
| 51 | `backend/src/server.js` | 501 | M4 服务端/基建 | LC审服务端 |
| 52 | `scripts/build-android.ps1` | 312 | M4 服务端/基建 | LC审服务端 |
| 53 | `scripts/build-desktop.ps1` | 195 | M4 服务端/基建 | LC审服务端 |
| 54 | `scripts/check-staged-paths.ps1` | 212 | M4 服务端/基建 | LC审服务端 |
| 55 | `scripts/README.md` | 93 | M4 服务端/基建 | LC审服务端 |
| 56 | `scripts/smoke.ps1` | 174 | M4 服务端/基建 | LC审服务端 |
| 57 | `tools/mechanics/gen-mechanics.ps1` | 279 | M4 服务端/基建 | LC审服务端 |
| 58 | `tests/at/golden_readsheet.py` | 100 | M5 测试资产 | LC审测试 |
| 59 | `tests/at/run-final.ps1` | 743 | M5 测试资产 | LC审测试 |
| 60 | `tests/numeric/.gitignore` | 2 | M5 测试资产 | LC审测试 |
| 61 | `tests/numeric/java/CompassProbe.java` | 45 | M5 测试资产 | LC审测试 |
| 62 | `tests/numeric/java/SensorMathProbe.java` | 27 | M5 测试资产 | LC审测试 |
| 63 | `tests/numeric/oracle.py` | 246 | M5 测试资产 | LC审测试 |
| 64 | `tests/numeric/README.md` | 80 | M5 测试资产 | LC审测试 |
| 65 | `tests/numeric/run_all.py` | 39 | M5 测试资产 | LC审测试 |
| 66 | `tests/numeric/source_parse.py` | 198 | M5 测试资产 | LC审测试 |
| 67 | `tests/numeric/test_b2_golden.py` | 132 | M5 测试资产 | LC审测试 |
| 68 | `tests/numeric/test_divergences.py` | 62 | M5 测试资产 | LC审测试 |
| 69 | `tests/numeric/test_ends_parity.py` | 159 | M5 测试资产 | LC审测试 |
| 70 | `tests/numeric/test_formula.py` | 163 | M5 测试资产 | LC审测试 |
| 71 | `tests/numeric/test_mechanics.py` | 187 | M5 测试资产 | LC审测试 |
| 72 | `tests/numeric/test_plates.py` | 155 | M5 测试资产 | LC审测试 |
| 73 | `tests/numeric/test_rings.py` | 87 | M5 测试资产 | LC审测试 |
| 74 | `tests/numeric/test_sensor_math.py` | 98 | M5 测试资产 | LC审测试 |
| 75 | `tests/numeric/tools/at_p0_0_check.ps1` | 160 | M5 测试资产 | LC审测试 |
| 76 | `tests/numeric/tools/check_mechanics.py` | 44 | M5 测试资产 | LC审测试 |
| 77 | `tests/numeric/tools/dump_readings.py` | 54 | M5 测试资产 | LC审测试 |
| 78 | `tests/numeric/tools/freeze_android_baseline.ps1` | 146 | M5 测试资产 | LC审测试 |
| 79 | `tests/numeric/tools/img_diff.py` | 47 | M5 测试资产 | LC审测试 |
| 80 | `docs/design/round-c-diff.md` | 64 | M6 文档/契约 | LC审文档 |
| 81 | `docs/design/round-d-b2.md` | 62 | M6 文档/契约 | LC审文档 |
| 82 | `docs/design/single-source-of-truth.md` | 298 | M6 文档/契约 | LC审文档 |
| 83 | `docs/FINAL-REPORT.md` | 105 | M6 文档/契约 | LC审文档 |
| 84 | `docs/gap-matrix.md` | 185 | M6 文档/契约 | LC审文档 |
| 85 | `docs/map-bridge.md` | 233 | M6 文档/契约 | LC审文档 |
| 86 | `docs/product/attribution/README.md` | 30 | M6 文档/契约 | LC审文档 |
| 87 | `docs/product/market-scan.md` | 119 | M6 文档/契约 | LC审文档 |
| 88 | `docs/product/PRD-v0.1.md` | 437 | M6 文档/契约 | LC审文档 |
| 89 | `docs/qa/AT-B2-ring-labels.md` | 173 | M6 文档/契约 | LC审文档 |
| 90 | `docs/qa/AT-final-round.md` | 71 | M6 文档/契约 | LC审文档 |
| 91 | `docs/qa/AT-P0-0-feature-switch.md` | 139 | M6 文档/契约 | LC审文档 |
| 92 | `docs/qa/AT-P0-1-reading-trust.md` | 115 | M6 文档/契约 | LC审文档 |
| 93 | `docs/qa/AT-P0-2-capture-record.md` | 135 | M6 文档/契约 | LC审文档 |
| 94 | `docs/qa/AT-P0-4-3d-pick.md` | 46 | M6 文档/契约 | LC审文档 |
| 95 | `docs/qa/AT-P0-4-3d-probe.md` | 117 | M6 文档/契约 | LC审文档 |
| 96 | `docs/qa/atb2-result.md` | 64 | M6 文档/契约 | LC审文档 |
| 97 | `docs/qa/baseline-01.md` | 151 | M6 文档/契约 | LC审文档 |
| 98 | `docs/qa/baseline-android/manifest.md` | 95 | M6 文档/契约 | LC审文档 |
| 99 | `docs/qa/baseline-android/probe.csv` | 7 | M6 文档/契约 | LC审文档 |
| 100 | `docs/qa/baseline-android/round-d5/disc-dial-list.png` | bin | M6 文档/契约 | LC审文档 |
| 101 | `docs/qa/baseline-android/round-d5/first-run-calibration.png` | bin | M6 文档/契约 | LC审文档 |
| 102 | `docs/qa/baseline-android/round-d5/manifest.md` | 38 | M6 文档/契约 | LC审文档 |
| 103 | `docs/qa/baseline-android/round-d5/SHA256SUMS.txt` | 7 | M6 文档/契约 | LC审文档 |
| 104 | `docs/qa/baseline-android/round-d5/tab-compass.png` | bin | M6 文档/契约 | LC审文档 |
| 105 | `docs/qa/baseline-android/round-d5/tab-knowledge.png` | bin | M6 文档/契约 | LC审文档 |
| 106 | `docs/qa/baseline-android/round-d5/tab-map.png` | bin | M6 文档/契约 | LC审文档 |
| 107 | `docs/qa/baseline-android/round-d5/ui-dial-list.xml` | 1 | M6 文档/契约 | LC审文档 |
| 108 | `docs/qa/baseline-android/round-d5/ui-first-run.xml` | 1 | M6 文档/契约 | LC审文档 |
| 109 | `docs/qa/baseline-android/round-d5/variant-p01/compass-p01-off.png` | bin | M6 文档/契约 | LC审文档 |
| 110 | `docs/qa/baseline-android/round-d5/variant-p01/compass-p01-on.png` | bin | M6 文档/契约 | LC审文档 |
| 111 | `docs/qa/baseline-android/round-d5/variant-p01/manifest.md` | 15 | M6 文档/契约 | LC审文档 |
| 112 | `docs/qa/baseline-android/round-d5/variant-p01/prefs-off.xml` | 8 | M6 文档/契约 | LC审文档 |
| 113 | `docs/qa/baseline-android/round-d5/variant-p01/prefs-on.xml` | 4 | M6 文档/契约 | LC审文档 |
| 114 | `docs/qa/baseline-android/round-d5/variant-p01/SHA256SUMS.txt` | 4 | M6 文档/契约 | LC审文档 |
| 115 | `docs/qa/baseline-android/tab-compass.png` | bin | M6 文档/契约 | LC审文档 |
| 116 | `docs/qa/baseline-android/tab-knowledge.png` | bin | M6 文档/契约 | LC审文档 |
| 117 | `docs/qa/baseline-android/tab-map.png` | bin | M6 文档/契约 | LC审文档 |
| 118 | `docs/qa/baseline-android/ui.xml` | 1 | M6 文档/契约 | LC审文档 |
| 119 | `docs/qa/bugs.md` | 287 | M6 文档/契约 | LC审文档 |
| 120 | `docs/qa/device-method.md` | 140 | M6 文档/契约 | LC审文档 |
| 121 | `docs/TEAM_CHARTER.md` | 112 | M6 文档/契约 | LC审文档 |
| 122 | `app/.dockerignore` | 4 | M7 数值档案/构建 | LC审文档 |
| 123 | `app/Cargo.lock` | 4112 | M7 数值档案/构建 | LC审文档 |
| 124 | `app/Cargo.toml` | 18 | M7 数值档案/构建 | LC审文档 |
| 125 | `app/docker-compose.yml` | 19 | M7 数值档案/构建 | LC审文档 |
| 126 | `app/Dockerfile` | 21 | M7 数值档案/构建 | LC审文档 |
| 127 | `app/mechanics.json` | 1605 | M7 数值档案/构建 | LC审文档 |
| 128 | `app/MECHANICS.md` | 505 | M7 数值档案/构建 | LC审文档 |
| 129 | `app/README.md` | 50 | M7 数值档案/构建 | LC审文档 |
| 130 | `craft.json` | 1584 | M8 研究工件 | （不入审查，仅盘点） |
| 131 | `investigation.cany/evidence/05/ev_05803e590229a2e9c07df080bcb45af7186056e6d2d1dfa401f4b55a2406b361.json` | 1875 | M8 研究工件 | （不入审查，仅盘点） |
| 132 | `investigation.cany/evidence/05/ev_05f3e99ec4b60352419910ee05e41d5efb2323e12ec3a5cc566c3262b5f8f9c4.json` | 75 | M8 研究工件 | （不入审查，仅盘点） |
| 133 | `investigation.cany/evidence/06/ev_06a5c7ff98d2e9ee5ce69e1144db4c8c888c0060526fc472fb1f0ddf6b7481c4.json` | 3535 | M8 研究工件 | （不入审查，仅盘点） |
| 134 | `investigation.cany/evidence/07/ev_0725435d10022123662d4d34886f0cef62efbc70d094de3e77f5e2f938870a4a.json` | 703 | M8 研究工件 | （不入审查，仅盘点） |
| 135 | `investigation.cany/evidence/0f/ev_0f98410e68d0f9ece165586878090b6b944c9bd1550d06abf0c2b656a0b1adce.json` | 2048 | M8 研究工件 | （不入审查，仅盘点） |
| 136 | `investigation.cany/evidence/13/ev_1344147b108d3e70e2b6ef414e5546f4a45a11d9bd3c21f78538dd15daf4dbdc.json` | 37124 | M8 研究工件 | （不入审查，仅盘点） |
| 137 | `investigation.cany/evidence/19/ev_19d82db9fb3f0759c45b5768aac3886f6e56347e10d16d72022dd2359003fc92.json` | 1575 | M8 研究工件 | （不入审查，仅盘点） |
| 138 | `investigation.cany/evidence/1a/ev_1a495e944afdb5f3df3c72c4a2dd1a6046e7054ffa20384eb00ac998479ea055.json` | 35 | M8 研究工件 | （不入审查，仅盘点） |
| 139 | `investigation.cany/evidence/1b/ev_1b5f02f41145ab078670cd539e5517ce621feb408fa92070904bb0bf1eee7248.json` | 64 | M8 研究工件 | （不入审查，仅盘点） |
| 140 | `investigation.cany/evidence/1b/ev_1b96e685019bd89987ba50647e10c82c1393fbf88a57d617952e054699e63ae6.json` | 339 | M8 研究工件 | （不入审查，仅盘点） |
| 141 | `investigation.cany/evidence/21/ev_21bb7a919396361f309385a96d76703d7a1b5de1d9050c04d26c774152bb451f.json` | 3655 | M8 研究工件 | （不入审查，仅盘点） |
| 142 | `investigation.cany/evidence/22/ev_22b3a76d1cfe859a9041e88dfa836a3b231a1098f396eb7f6e889cbd94fe3d4b.json` | 970 | M8 研究工件 | （不入审查，仅盘点） |
| 143 | `investigation.cany/evidence/25/ev_257e8cac44b5468f8f8573af019cf20c3ea4a1e1507b1b0a74f9afbac2d7f4e3.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 144 | `investigation.cany/evidence/26/ev_2672f67f3e49564f71e3b37c26da8471d0dcadb31587fede8161018b8d5d99d1.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 145 | `investigation.cany/evidence/26/ev_26ed22aacbc0d6568cd27cf7d6acf0c63c6372397d071197ba97aa334618d970.json` | 52 | M8 研究工件 | （不入审查，仅盘点） |
| 146 | `investigation.cany/evidence/27/ev_2775f6970a10b110268729cd0bc2d74868a89eee1d3b578085f98c577283ccfd.json` | 182 | M8 研究工件 | （不入审查，仅盘点） |
| 147 | `investigation.cany/evidence/38/ev_3884eb0e25db98d22087173d4280c258edf630385161bb3f2668fd3ff381b679.json` | 42 | M8 研究工件 | （不入审查，仅盘点） |
| 148 | `investigation.cany/evidence/3a/ev_3a2acd004f8e7faa437d1af0034c708ce29cb10aa5f1a68aaf83c34687be662b.json` | 649 | M8 研究工件 | （不入审查，仅盘点） |
| 149 | `investigation.cany/evidence/3a/ev_3a6587fbcb9c11de62b8ea8fa47969e703380868c9bc2fcad96c00321e93e616.json` | 58 | M8 研究工件 | （不入审查，仅盘点） |
| 150 | `investigation.cany/evidence/3b/ev_3ba4914bde9b7374f2217dd3fb01d6aeb4f3c90f74b10ec8eec1dad9a08b026c.json` | 500 | M8 研究工件 | （不入审查，仅盘点） |
| 151 | `investigation.cany/evidence/43/ev_439cd15a0382f0edc3c93501ec9613934339a75becdcbbe68141fac5e29eb000.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 152 | `investigation.cany/evidence/4a/ev_4a0b7d716c112a4cecf5b4ae7176159c1ef1421a401132949964fd08df97481e.json` | 51 | M8 研究工件 | （不入审查，仅盘点） |
| 153 | `investigation.cany/evidence/4b/ev_4b0ad53f4a3d3cdd55b11127db2ffdfab9b8e7d1e8f7810a0e5731ea24808ee6.json` | 42 | M8 研究工件 | （不入审查，仅盘点） |
| 154 | `investigation.cany/evidence/4b/ev_4b64569bd2707cf5b2d2099ab8b038ee114683df61e8ae5ee524598c745a3739.json` | 116 | M8 研究工件 | （不入审查，仅盘点） |
| 155 | `investigation.cany/evidence/5b/ev_5bc0d74e88dd15886d486817a37bbf472eafcc4f8aa8fa4c5a10a7a28e5693d5.json` | 261 | M8 研究工件 | （不入审查，仅盘点） |
| 156 | `investigation.cany/evidence/64/ev_64804378c9cd53798101ccbb1285b1c22ac2698a9096e992eb130335b3dd3126.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 157 | `investigation.cany/evidence/69/ev_690f232538b50b29d23b6a4d0c95cd65e1f027db33593030da36f7d9cd8e9461.json` | 913 | M8 研究工件 | （不入审查，仅盘点） |
| 158 | `investigation.cany/evidence/6b/ev_6b0bd4686587e862c7add63e7db98a6be32f3fd5d6db0732498edf050bf61b86.json` | 956 | M8 研究工件 | （不入审查，仅盘点） |
| 159 | `investigation.cany/evidence/6f/ev_6fcb95f3a531d9606b1a0380cfecc5af1f73dbcb52e532bd5a12bfeb2825471f.json` | 717 | M8 研究工件 | （不入审查，仅盘点） |
| 160 | `investigation.cany/evidence/72/ev_72eb018d86cd5e71b2cb43fcf3d43dc1b579844c2579d23d1d0f12f9a73b6afb.json` | 3134 | M8 研究工件 | （不入审查，仅盘点） |
| 161 | `investigation.cany/evidence/74/ev_74f4cc1eb157193640241d8774d65a3702140daf584464cccf26a829f205afbb.json` | 3662 | M8 研究工件 | （不入审查，仅盘点） |
| 162 | `investigation.cany/evidence/77/ev_77ca657faabc0dd46d6c1dd256061a75bda4dff2f540d8fcd43deac488daf88c.json` | 971 | M8 研究工件 | （不入审查，仅盘点） |
| 163 | `investigation.cany/evidence/79/ev_793eabbd90f1915a7515585d2eacb8bad0913973becd1cbcecf9d1d8658aec3c.json` | 117 | M8 研究工件 | （不入审查，仅盘点） |
| 164 | `investigation.cany/evidence/79/ev_79db4b8497358a5c0de491cd481f6f7aa4f557d34669a10275bc575c89ee0bae.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 165 | `investigation.cany/evidence/7b/ev_7b1fd68058d6a62ad361e0f45896ea49b5de9f2ae110ba565634f1fa2b8350c4.json` | 52 | M8 研究工件 | （不入审查，仅盘点） |
| 166 | `investigation.cany/evidence/7d/ev_7d062d9500c11414525357bd1adbe1bea494e481fd0a6c344c7921eb2facb56d.json` | 1249 | M8 研究工件 | （不入审查，仅盘点） |
| 167 | `investigation.cany/evidence/93/ev_9379fc19d63605482420187d5730b3bc6f29120a989ac21b4c74d622776e9c2c.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 168 | `investigation.cany/evidence/94/ev_945123cff81783f47ff2a3fcc9e1b2f7ca412e0b6cead55f431d8dfdbe1a61cc.json` | 2068 | M8 研究工件 | （不入审查，仅盘点） |
| 169 | `investigation.cany/evidence/95/ev_95ee630fdf92755e9d9e64ed5dde56d62da4bfba5468fee7f273795e051b5cc5.json` | 120 | M8 研究工件 | （不入审查，仅盘点） |
| 170 | `investigation.cany/evidence/96/ev_9630f46ce1c740a04ba28747c03c3bc8e489c5986c8c6b2f2b71ba4755e361d5.json` | 119 | M8 研究工件 | （不入审查，仅盘点） |
| 171 | `investigation.cany/evidence/9a/ev_9a7743ef509f4ebf6cff60330326ae006c502d5010aa149ffbde06e63192a148.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 172 | `investigation.cany/evidence/a1/ev_a1f305a3a602bd31a0e1f64cb55a27a4a125996c792f5f2f6d01eb96f02caec6.json` | 76 | M8 研究工件 | （不入审查，仅盘点） |
| 173 | `investigation.cany/evidence/a7/ev_a733cd909a0a534e359a9e0f5caa9c2503aeba7a083ce311114409592553cb06.json` | 585 | M8 研究工件 | （不入审查，仅盘点） |
| 174 | `investigation.cany/evidence/ad/ev_ad4cc72bcbc73571bf4c430ca39b24e46af459e3093443e25529bca44208869f.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 175 | `investigation.cany/evidence/b3/ev_b38bad7535c430acacaaf7ed75a83dce8ef6648f9fb1421925a7a69f979847c1.json` | 403 | M8 研究工件 | （不入审查，仅盘点） |
| 176 | `investigation.cany/evidence/b6/ev_b6cbe4fb117fb2e7238219aae449e425cebb014f4106cb2bb0b86decb4091381.json` | 2049 | M8 研究工件 | （不入审查，仅盘点） |
| 177 | `investigation.cany/evidence/b7/ev_b7193e8b48226285ef112a6aebdf43a82a9ce8312f48e686699948bc666c3d56.json` | 416 | M8 研究工件 | （不入审查，仅盘点） |
| 178 | `investigation.cany/evidence/c5/ev_c57b81f8b12a4886ed8edd390bbd0897027cc97a342e597afbd48dbfe59e713b.json` | 364 | M8 研究工件 | （不入审查，仅盘点） |
| 179 | `investigation.cany/evidence/c5/ev_c5ed914d75375eea51ef97623dea99ca170d07c53236d004cb11acc68984b71b.json` | 42 | M8 研究工件 | （不入审查，仅盘点） |
| 180 | `investigation.cany/evidence/cb/ev_cb5f568efdfab759aa70694b618ee1eb1ee2659ba017a629964319a7c00f60a3.json` | 42 | M8 研究工件 | （不入审查，仅盘点） |
| 181 | `investigation.cany/evidence/cb/ev_cb64d55ada36d0f03384206a82807babb4b7b1e9ecd83fac61185b575953597f.json` | 781 | M8 研究工件 | （不入审查，仅盘点） |
| 182 | `investigation.cany/evidence/cc/ev_cc26ab8137304296328f7efd0417347c439225441200cffd4b95f2de049d29f6.json` | 370 | M8 研究工件 | （不入审查，仅盘点） |
| 183 | `investigation.cany/evidence/d4/ev_d4aa7ed92836ee852079b4a845986b00b71e75b9d62925e34fe00d18644a0196.json` | 94 | M8 研究工件 | （不入审查，仅盘点） |
| 184 | `investigation.cany/evidence/d9/ev_d9a07c00028a21f758d3d5ac89b15695c455ce55eee67f45094e134e265fef9c.json` | 46 | M8 研究工件 | （不入审查，仅盘点） |
| 185 | `investigation.cany/evidence/df/ev_dfe7114e8d74ebc1a7e840856d8fe871f157375742d1a9b5696f62a962a52f15.json` | 49 | M8 研究工件 | （不入审查，仅盘点） |
| 186 | `investigation.cany/evidence/e9/ev_e9252cb16d4d4186426a0d5526a81d74007a9570f3a94ff5f57c593ec5c647e2.json` | 79 | M8 研究工件 | （不入审查，仅盘点） |
| 187 | `investigation.cany/evidence/f1/ev_f1175cf06354d3ddb245c9a6a5a59d92a8515c3b0793cd628815c615870708f0.json` | 2549 | M8 研究工件 | （不入审查，仅盘点） |
| 188 | `investigation.cany/evidence/f1/ev_f1365d88204dfe5521402f36ef854b0366ee964883a13ca38d7920714d9c8e03.json` | 1737 | M8 研究工件 | （不入审查，仅盘点） |
| 189 | `investigation.cany/evidence/f3/ev_f3b45cf14e73cfffa0a6fba06dcbaa591ef1205601e8cd6b6e533e6cacb7b2d8.json` | 561 | M8 研究工件 | （不入审查，仅盘点） |
| 190 | `investigation.cany/evidence/f7/ev_f7ef33a4c76b56d87d3d98092d9dec21059df235411f293704b693afdc865c53.json` | 213 | M8 研究工件 | （不入审查，仅盘点） |
| 191 | `investigation.cany/evidence/fb/ev_fb015f404a188b9d9804e8add4d7bc8a388914147b1a40cc3d6f465e0e25698d.json` | 177 | M8 研究工件 | （不入审查，仅盘点） |
| 192 | `investigation.cany/evidence/fb/ev_fbb90efae39a8bad547a6282696560a1b21aab63aaa2302fdbf8a1711d3549b2.json` | 365 | M8 研究工件 | （不入审查，仅盘点） |
| 193 | `investigation.cany/evidence/ff/ev_ff2ee9f94cc81ecd99b043c84ccd90a56f516f0c84608b2e42562e1bb590a43e.json` | 655 | M8 研究工件 | （不入审查，仅盘点） |
| 194 | `investigation.cany/investigation/brief.md` | 16 | M8 研究工件 | （不入审查，仅盘点） |
| 195 | `investigation.cany/investigation/findings.md` | 3 | M8 研究工件 | （不入审查，仅盘点） |
| 196 | `investigation.cany/investigation/handoff.md` | 3 | M8 研究工件 | （不入审查，仅盘点） |
| 197 | `investigation.cany/investigation/hypotheses.md` | 3 | M8 研究工件 | （不入审查，仅盘点） |
| 198 | `investigation.cany/investigation/plan.md` | 3 | M8 研究工件 | （不入审查，仅盘点） |
| 199 | `investigation.cany/investigation/state.json` | 22 | M8 研究工件 | （不入审查，仅盘点） |
| 200 | `investigation.cany/journal/2026-11-04.jsonl` | 532 | M8 研究工件 | （不入审查，仅盘点） |
| 201 | `investigation.cany/project.toml` | 4 | M8 研究工件 | （不入审查，仅盘点） |
| 202 | `investigation.cany/unknowns/unk_8c499e1b15d1868c.json` | 15 | M8 研究工件 | （不入审查，仅盘点） |
| 203 | `investigation.cany/unknowns/unk_dc8a939d5cca92be.json` | 14 | M8 研究工件 | （不入审查，仅盘点） |
| 204 | `usage.json` | 9 | M8 研究工件 | （不入审查，仅盘点） |

## 4. 未覆盖与例外声明

- `M8 研究工件`（`investigation.cany/**`、`craft.json`、`usage.json`）为逆向研究过程的证据工件，**不属源码**，仅纳入盘点，不做代码审查；其内容作为证据来源时只作引用。
- 二进制/图片类文件（`res/drawable*`、`*.png`）只做资源一致性与体积检查，不做逐行审查。
- 基线之外的任何开发分支、共享工作树中的未提交改动，**不在本次审查范围**。
