# Android 基线 · round-acceptance

- 冻结时间：2026-11-06 13:09:38（UTC+8），LC测试

- 设备：JAD-AL00 / Android 12 / `HUAWEI/JAD-AL00/HWJAD-Q:12/HUAWEIJAD-AL00/104.2.0.186C00:user/release-keys`
- 屏幕：Physical size: 1228x2700；Physical density: 540
- App：versionName 1.0 (1)，lastUpdateTime 2026-11-06 13:08:31，pid 14043
- 构建：`develop` HEAD `0dda97e`；磁盘 APK `E:\APPDetector\wt\lc-wire-f0607\app\android\app\build\outputs\apk\debug\app-debug.apk`（7059441 B），SHA256 `A928023FF12334C903094A929D1E89A8152CD238EF9DC751974A4FC7CF7E07BB`
- 设备运行 APK（`pm path` + pull 现算）：SHA256 `A928023FF12334C903094A929D1E89A8152CD238EF9DC751974A4FC7CF7E07BB` / 7059441 B；与磁盘包一致
- 脚本运行树：`E:\APPDetector\wt\lc-wire-f0607` HEAD `f332b9c`（仅诊断；APK 的构建来源以上一行 `-BuildHead` 为准）
- 结构：单页全屏地图 + 罗盘浮层，无底部导航；左列 8（全地图工具/截屏/超清优化/海拔查询/罗盘/路网/等高线/更多）、右列 10（图层/叠加/山脉/水系/探索/指北针/+/-/定位/3D）、更多面板 16 格（4x4），罗盘浮层左 6 右 4 工具条 5；18 项入口全部接通
- 冷启动：am start -W TotalTime = 653 ms（原文见 `am-start-w.txt`）；注入 heading = 0（debug 广播）
- 数值门禁：`tests=85 failures=0 errors=0 skipped=0` + `CARGO: PASS`（`numeric-gate.txt`；原始输出见文末）
- logcat：FATAL EXCEPTION（应用进程）**0** / 已排除非应用 FATAL **0** / ANR **0**（`logcat.txt`）
- SW-006 面板裁切窗：待客户端按 round-acceptance 新布局实测；**不得沿用旧裁切窗**（旧窗已随上一轮 UI 失效）
- 裁切窗测量依据：未提供——重取时必须写明测量方法/取样点（谁量、哪张截图、怎么定面板上缘）
- 待跑清单：`docs/qa/k-01-pending.md`（设备放行后一次跑完；待跑不得写成已过）

## 采集结果（K-01 验收硬项）

| 组 | 预期 | 命中 | 缺失 |
|---|---|---|---|
| 地图态左列 | 8 | 8 |  |
| 地图态右列 | 10 | 10 |  |
| 「更多」16 宫格 | 16 | 16 |  |
| 罗盘态文字（dump 可读部分，非硬项） | 19 | 19 | 全中（K-02 顶栏 返回/搜索/图层/隐藏 已实现） |

- 结构校验：侧别错误 0 个 ；列内顺序异常 0 个 
- 中心偏差（参考坐标取自 orig-main.xml，分母**内容帧 2578**；容差 ±45px）：最大 **8.5 px**（dx 最大 8.5 px，dy 最大 0 px）；超差 0 个 

## 冻结物（SHA256 见 `SHA256SUMS.txt`）

- `am-start-w.txt`：177 B
- `k01-compass-needle-off.png`：1413067 B
- `k01-compass-needle-on.png`：1416532 B
- `k01-compass.png`：1416532 B
- `k01-map-after-hide.png`：901503 B
- `k01-map.png`：908981 B
- `k01-more.png`：572177 B
- `logcat.txt`：471975 B
- `nodes-k01.csv`：2316 B
- `numeric-gate.txt`：10483 B
- `README.md`：5290 B
- `ui-compass-mode.xml`：32400 B
- `ui-first.xml`：6056 B
- `ui-map-after-hide.xml`：32035 B
- `ui-map.xml`：13695 B
- `ui-more.xml`：12579 B

## 限制

1. 罗盘浮层与圆盘为自绘/WebView 层，`uiautomator` 只能拿到少量原生节点；圆心/直径/透明度以截图测量为准（`k01-compass.png`）。
2. 左列/右列双判据：① 身份按**文案**判定（`text`/`content-desc` 精确匹配，含 `+`/`−` 符号按钮），② 按钮**中心**与 `orig-main.xml` 参考坐标偏差 ≤45px。参考坐标是内容帧坐标，归一除以 **2578**（内容帧高），不是物理屏 2700——按 2700 算本机「更多」偏 −106px。另做结构校验：控件在正确的半屏、列内 y 单调递增。**不再用「45px 邻域找最近节点」代替身份判定**：那会把邻居控件记成命中、把存在的控件报缺失（F-17）。
3. 历史基线目录（如 `round-d5/` 及更早）仅作历史对照，不得作为本轮基线或裁切参考；本轮基线以 `round-acceptance` 为准。

## 数值门禁原始输出（F-01/F-02）

~~~text
test_both_ends_identical (test_b2_golden.B2GeneratedTablesTest.test_both_ends_identical) ... ok
test_java_rings_match_mechanics (test_b2_golden.B2GeneratedTablesTest.test_java_rings_match_mechanics) ... ok
test_rust_rings_match_mechanics (test_b2_golden.B2GeneratedTablesTest.test_rust_rings_match_mechanics) ... ok
test_total_780_labels (test_b2_golden.B2GeneratedTablesTest.test_total_780_labels) ... ok
test_readout_calls_ring_text (test_b2_golden.B2RingTextWiringTest.test_readout_calls_ring_text) ... ok
test_vectors_from_mechanics_json (test_b2_golden.B2VectorsTest.test_vectors_from_mechanics_json) ... ok
test_vectors_in_java_rings (test_b2_golden.B2VectorsTest.test_vectors_in_java_rings) ... ok
test_vectors_in_rust_rings (test_b2_golden.B2VectorsTest.test_vectors_in_rust_rings) ... ok
test_java_heaven_plate_matches_spec (test_divergences.PlatesRegressionTest.test_java_heaven_plate_matches_spec) ... ok
test_java_human_plate_matches_spec (test_divergences.PlatesRegressionTest.test_java_human_plate_matches_spec) ... ok
test_rust_heaven_plate_matches_spec (test_divergences.PlatesRegressionTest.test_rust_heaven_plate_matches_spec) ... ok
test_rust_human_plate_matches_spec (test_divergences.PlatesRegressionTest.test_rust_human_plate_matches_spec) ... ok
test_source_parse_ok (test_divergences.PlatesRegressionTest.test_source_parse_ok)
解析健康检查：确保三盘用例不是因为解析失败而「假通过」。 ... ok
test_all_corpus_rows_present (test_ends_parity.JavaRealCodeTest.test_all_corpus_rows_present) ... ok
test_back_equals_sitting (test_ends_parity.JavaRealCodeTest.test_back_equals_sitting)
坐向 = 向+180°：对每个语料，sitting 名 == face((h+180)%360)。 ... ok
test_divisions (test_ends_parity.JavaRealCodeTest.test_divisions) ... ok
test_earth_mountain_offset (test_ends_parity.JavaRealCodeTest.test_earth_mountain_offset) ... ok
test_jian_label (test_ends_parity.JavaRealCodeTest.test_jian_label) ... ok
test_sitting_and_names (test_ends_parity.JavaRealCodeTest.test_sitting_and_names) ... ok
test_earth_mountain_offset (test_ends_parity.RustSourceTest.test_earth_mountain_offset) ... ok
test_jian_range_boundary (test_ends_parity.RustSourceTest.test_jian_range_boundary) ... ok
test_matches_java_earth_readings (test_ends_parity.RustSourceTest.test_matches_java_earth_readings) ... ok
test_back_180_on_24_mountains (test_formula.BackFormulaTest.test_back_180_on_24_mountains) ... ok
test_back_equals_sitting_index (test_formula.BackFormulaTest.test_back_equals_sitting_index)
back 的文本 == MOUNTAINS[(m+12)%24]（+180° 与 +12 格等价）。 ... ok
test_back_wraps_at_360 (test_formula.BackFormulaTest.test_back_wraps_at_360) ... ok
test_24_mountain_boundaries (test_formula.FaceFormulaTest.test_24_mountain_boundaries)
地盘 24 山：interval 15 / offset 7.5（DEX TYPE_4）。 ... ok
test_359_99_is_last_division (test_formula.FaceFormulaTest.test_359_99_is_last_division) ... ok
test_boundary_is_lower_inclusive (test_formula.FaceFormulaTest.test_boundary_is_lower_inclusive)
每个圈层：h=offset+interval*k 属于第 k 格；再少一点属于第 k-1 格。 ... ok
test_index_never_out_of_range (test_formula.FaceFormulaTest.test_index_never_out_of_range) ... ok
test_zero_for_zero_offset (test_formula.FaceFormulaTest.test_zero_for_zero_offset) ... ok
test_known_values (test_formula.NormalizeTest.test_known_values) ... ok
test_result_in_range (test_formula.NormalizeTest.test_result_in_range) ... ok
test_120_fenjin_offset_1_5 (test_formula.SpecialRingsTest.test_120_fenjin_offset_1_5) ... ok
test_60_dragons_offset_1_5 (test_formula.SpecialRingsTest.test_60_dragons_offset_1_5)
透地平分六十龙：offset 1.5，不是 0（MECHANICS.md:160-161）。 ... ok
test_64_hexagrams_exact_division (test_formula.SpecialRingsTest.test_64_hexagrams_exact_division) ... ok
test_72_dragons_offset_2_5 (test_formula.SpecialRingsTest.test_72_dragons_offset_2_5) ... ok
test_bagua_45_degrees (test_formula.SpecialRingsTest.test_bagua_45_degrees) ... ok
test_ring_intervals_match_mechanics (test_formula.SpecialRingsTest.test_ring_intervals_match_mechanics)
14 圈层的 (interval, offset) 必须与 mechanics.json / MECHANICS.md 一致。 ... ok
test_24_mountains (test_mechanics.BothEndsTableParityTest.test_24_mountains) ... ok
test_houtian (test_mechanics.BothEndsTableParityTest.test_houtian) ... ok
test_jiazi (test_mechanics.BothEndsTableParityTest.test_jiazi) ... ok
test_nayin (test_mechanics.BothEndsTableParityTest.test_nayin) ... ok
test_ring_constants_present_in_both_ends (test_mechanics.BothEndsTableParityTest.test_ring_constants_present_in_both_ends)
64 卦 5.625°、60 龙 offset 1.5 必须两端都在（数值同源的最低要求）。 ... ok
test_wuxing (test_mechanics.BothEndsTableParityTest.test_wuxing) ... ok
test_xiantian (test_mechanics.BothEndsTableParityTest.test_xiantian) ... ok
test_yinyang (test_mechanics.BothEndsTableParityTest.test_yinyang) ... ok
test_all_have_source_and_none_estimated (test_mechanics.MechanicsJsonTest.test_all_have_source_and_none_estimated) ... ok
test_derived_counts (test_mechanics.MechanicsJsonTest.test_derived_counts) ... ok
test_exactly_19_items (test_mechanics.MechanicsJsonTest.test_exactly_19_items) ... ok
test_names_unique_and_groups_known (test_mechanics.MechanicsJsonTest.test_names_unique_and_groups_known) ... ok
test_numeric_values_match_oracle (test_mechanics.MechanicsJsonTest.test_numeric_values_match_oracle) ... ok
test_order_and_bagua_strings (test_mechanics.MechanicsJsonTest.test_order_and_bagua_strings) ... ok
test_dials_match_android_compass_types (test_mechanics.SingleSourceTest.test_dials_match_android_compass_types) ... ok
test_no_estimated_markers_in_values (test_mechanics.SingleSourceTest.test_no_estimated_markers_in_values) ... ok
test_rings_labels_complete (test_mechanics.SingleSourceTest.test_rings_labels_complete)
LC逆向 118357a：14 张文字表共 780 条，全部 confirmed_dex。 ... ok
test_rings_section_matches_oracle (test_mechanics.SingleSourceTest.test_rings_section_matches_oracle) ... ok
test_tables_section_matches_oracle (test_mechanics.SingleSourceTest.test_tables_section_matches_oracle) ... ok
test_versioned_sections (test_mechanics.SingleSourceTest.test_versioned_sections) ... ok
test_type4_with_dex_order_is_earth (test_plates.DexListEquivalenceTest.test_type4_with_dex_order_is_earth) ... ok
test_type8_with_dex_order_is_human (test_plates.DexListEquivalenceTest.test_type8_with_dex_order_is_human) ... ok
test_type9_with_zi_order_is_heaven (test_plates.DexListEquivalenceTest.test_type9_with_zi_order_is_heaven) ... ok
test_60_dragons_60_divisions (test_plates.FenjinDragonSlotTest.test_60_dragons_60_divisions) ... ok
test_dragon72_slots_3_per_mountain (test_plates.FenjinDragonSlotTest.test_dragon72_slots_3_per_mountain) ... ok
test_fenjin_slots_5_per_mountain (test_plates.FenjinDragonSlotTest.test_fenjin_slots_5_per_mountain) ... ok
test_earth_and_human_relation (test_plates.PlateTableTest.test_earth_and_human_relation)
人盘恒为天盘的下一格，地盘介于两者之间（r<7.5 取天盘，r>=7.5 取人盘）。 ... ok
test_exactly_two_distinct_on_any_heading (test_plates.PlateTableTest.test_exactly_two_distinct_on_any_heading)
人盘 = 地盘+1 格、天盘 = 地盘-1 格 → 三盘恒有且仅有两个不同读数。 ... ok
test_plate_offsets_from_mechanics (test_plates.PlateTableTest.test_plate_offsets_from_mechanics) ... ok
test_three_plates_key_headings (test_plates.PlateTableTest.test_three_plates_key_headings) ... ok
test_boundary_4_5_inclusive (test_plates.ZhengJianTest.test_boundary_4_5_inclusive) ... ok
test_center_is_zheng (test_plates.ZhengJianTest.test_center_is_zheng) ... ok
test_jian_range_is_outer_3_degrees (test_plates.ZhengJianTest.test_jian_range_is_outer_3_degrees)
兼向区 = 山界两侧各 3°（九度外 = 15-9=6 → 每侧 3°）。 ... ok
test_just_outside_4_5_is_jian (test_plates.ZhengJianTest.test_just_outside_4_5_is_jian) ... ok
test_boundaries_advance_one_division (test_rings.RingGeometryTest.test_boundaries_advance_one_division) ... ok
test_center_maps_to_its_division (test_rings.RingGeometryTest.test_center_maps_to_its_division) ... ok
test_center_plus_minus_quarter_stays (test_rings.RingGeometryTest.test_center_plus_minus_quarter_stays) ... ok
test_ring_intervals_exact_binary (test_rings.RingGeometryTest.test_ring_intervals_exact_binary)
5.625/3/5/6/15/45 都是可精确表示的二进制小数，不应出现浮点漂移。 ... ok
test_all_14_rings_present (test_rings.RingTableTest.test_all_14_rings_present) ... ok
test_counts (test_rings.RingTableTest.test_counts) ... ok
test_oracle_matches_mechanics_md (test_rings.RingTableTest.test_oracle_matches_mechanics_md) ... ok
test_set_heading_offset_does_not_freeze (test_sensor_math.CompassViewOffsetSourceTest.test_set_heading_offset_does_not_freeze) ... ok
test_set_sensor_heading_composes_offset_and_keeps_follow (test_sensor_math.CompassViewOffsetSourceTest.test_set_sensor_heading_composes_offset_and_keeps_follow) ... ok
test_clamp_declination (test_sensor_math.SensorMathJvmTest.test_clamp_declination) ... ok
test_declination_pipeline (test_sensor_math.SensorMathJvmTest.test_declination_pipeline) ... ok
test_offset_pipeline (test_sensor_math.SensorMathJvmTest.test_offset_pipeline)
B-05：翻坐向的持久偏移只做合成，JVM 真执行两条向量。 ... ok
test_tilt_formula (test_sensor_math.SensorMathJvmTest.test_tilt_formula) ... ok

----------------------------------------------------------------------
Ran 85 tests in 1.547s

OK
LC numeric suite — python 3.13.14
repo root: E:\APPDetector\wt\lc-wire-f0607
------------------------------------------------------------------------
tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0
CARGO: HEAD=f332b9c18ca03e0c3df8a3ea0d496e15d365f7c8 version=cargo 1.99.1 (5f94df478 2026-08-27)
CARGO| running 5 tests
CARGO| test mechanics_pin::ring_scalars_match_reading_literals ... ok
CARGO| test mechanics_pin::readings_pinned_at_boundaries ... ok
CARGO| test mechanics_pin::b2_vectors_match_dex_tables ... ok
CARGO| test mechanics_pin::table_lengths ... ok
CARGO| test mechanics_pin::b2_all_780_cells ... ok
CARGO| test result: ok. 5 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
CARGO: test-binary=app\target\debug\deps\luopan-a3ff9c2d47b522fa.exe sha256=a8299f9945fd11e05d0f7d73eba0838abdc5168b13bd47def0bdbc03fd16716f
CARGO: PASS
RESULT: PASS
~~~
