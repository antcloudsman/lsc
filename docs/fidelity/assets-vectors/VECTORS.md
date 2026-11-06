# 原版矢量图标（`res/drawable/svg_*`，apktool 解码）

来源：原版 `space.xinzhi.globe` 1.6.1，APK `E:\APPDetector\input\swss.apk`（sha256 `2fd1fc159c92cbd6fea1b210621eb619dce87b051a8a9275c9330ffb0992bfe8`）。
APK 内是编译后的二进制 AXML，条目名为混淆短名；本目录是 `apktool` 解码后的可读 XML，资源 id 取自同次解码的 `res/values/public.xml`。**本目录不进 `assets.py verify` 的严格清单**（那套校验只覆盖可直接从 APK 取出的原始字节，如 PNG/HTML）；矢量以「资源名 + 资源 id + sha256(解码文件)」追溯。

## 1. UI 项 → 资源名（抽屉 9 + 左列 8 + 右列 10）

| UI 项 | 资源名 | 资源 id | 清单锚点 |
|---|---|---|---|
| 抽屉 地图设置 | `svg_setting` | `0x7f0803fd` | A-02 |
| 抽屉 账户设置 | `svg_account_setting` | `0x7f08039b` | A-02 |
| 抽屉 投诉反馈 | `svg_tousu` | `0x7f080413` | A-02 |
| 抽屉 关于我们 | `svg_about` | `0x7f08039a` | A-02 |
| 抽屉 联系我们 | `svg_contact_us` | `0x7f0803bf` | A-02 |
| 抽屉 推荐给好友 | `svg_menu_share` | `0x7f0803e5` | A-02 |
| 抽屉 开具发票 | `svg_bill_apply` | `0x7f0803a6` | A-02 |
| 抽屉 帮助中心 | `svg_helper` | `0x7f0803cb` | A-02 |
| 抽屉 用户协议 | `svg_user_agreement` | `0x7f08041e` | A-02 |
| 左列 全地图工具 | `svg_tool_folder` | `0x7f08040c` | A-04 |
| 左列 截屏 | `svg_scropy` | `0x7f0803fa` | A-04 |
| 左列 超清优化 | `svg_super_map_old` | `0x7f080402` | A-04 |
| 左列 海拔查询 | `svg_altitude` | `0x7f0803a0` | A-04 |
| 左列 罗盘 | `svg_compass` | `0x7f0803b7` | A-04 |
| 左列 路网 | `svg_road` | `0x7f0803f9` | A-04 |
| 左列 等高线 | `svg_isoline` | `0x7f0803d0` | A-04 |
| 左列 更多 | `svg_more` | `0x7f0803e7` | A-04 |
| 右列 图层 | `svg_layer` | `0x7f0803da` | A-04 |
| 右列 叠加 | `svg_overlay` | `0x7f0803ec` | A-04 |
| 右列 山脉 | `svg_mountain` | `0x7f0803e8` | A-04 |
| 右列 水系 | `svg_water` | `0x7f080421` | A-04 |
| 右列 探索 | `svg_discovery` | `0x7f0803c4` | A-04 |
| 右列 指北针 | `svg_north_needle` | `0x7f0803eb` | A-04 |
| 右列 +（放大） | `svg_zoom_in` | `0x7f080424` | A-04 |
| 右列 −（缩小） | `svg_zoom_out` | `0x7f080425` | A-04 |
| 右列 定位 | `svg_locate` | `0x7f0803db` | A-04 |

说明：右列「3D」不在 `svg_*` 集里，原版用的是位图 `mipmap-xxxhdpi/ic_3d_white.png`（青色立方线稿），该图随 A-03 那批位图一并记录；本表只列矢量。

## 2. 导出文件清单

| 文件 | 对应资源名 | 资源 id | 字节 | sha256 |
|---|---|---|---|---|
| `$svg_farmland__0.xml` | `$svg_farmland` | `-` | 426 | `10c3c7340e56b8b9…` |
| `$svg_farmland__1.xml` | `$svg_farmland` | `-` | 425 | `41f006f04f4423aa…` |
| `$svg_farmland__10.xml` | `$svg_farmland` | `-` | 422 | `0e8a227cdf08506b…` |
| `$svg_farmland__11.xml` | `$svg_farmland` | `-` | 423 | `c46e4894211908a0…` |
| `$svg_farmland__12.xml` | `$svg_farmland` | `-` | 424 | `7985ebb6dba826fc…` |
| `$svg_farmland__13.xml` | `$svg_farmland` | `-` | 424 | `ad5bb679e0a106c8…` |
| `$svg_farmland__2.xml` | `$svg_farmland` | `-` | 424 | `76b291a707d33c52…` |
| `$svg_farmland__3.xml` | `$svg_farmland` | `-` | 424 | `f529dc7ef2d92017…` |
| `$svg_farmland__4.xml` | `$svg_farmland` | `-` | 426 | `1d1fc0c5ce00a57d…` |
| `$svg_farmland__5.xml` | `$svg_farmland` | `-` | 426 | `9e737d2715340bdd…` |
| `$svg_farmland__6.xml` | `$svg_farmland` | `-` | 426 | `16e257dfd19cd30b…` |
| `$svg_farmland__7.xml` | `$svg_farmland` | `-` | 424 | `3e3cabad68e02e4d…` |
| `$svg_farmland__8.xml` | `$svg_farmland` | `-` | 422 | `e6706a15ccf9924f…` |
| `$svg_farmland__9.xml` | `$svg_farmland` | `-` | 424 | `ad1f36944e3bf227…` |
| `$svg_item_hook10__0.xml` | `$svg_item_hook10` | `-` | 420 | `9358c3bf1369560a…` |
| `$svg_item_hook9__0.xml` | `$svg_item_hook9` | `-` | 421 | `1d0520919a7ee9ae…` |
| `svg_about.xml` | `svg_about` | `0x7f08039a` | 2656 | `14919d6143436953…` |
| `svg_account_setting.xml` | `svg_account_setting` | `0x7f08039b` | 2494 | `f1611352cacac974…` |
| `svg_add.xml` | `svg_add` | `0x7f08039c` | 597 | `c4df63eb415ab651…` |
| `svg_add_collect.xml` | `svg_add_collect` | `0x7f08039d` | 1213 | `b940f78b8a2bc4ed…` |
| `svg_add_map.xml` | `svg_add_map` | `0x7f08039e` | 707 | `9dac4124ebb3a579…` |
| `svg_add_setting.xml` | `svg_add_setting` | `0x7f08039f` | 3093 | `fd4909323faddb58…` |
| `svg_altitude.xml` | `svg_altitude` | `0x7f0803a0` | 2224 | `6447852ad993dddf…` |
| `svg_altitude_point.xml` | `svg_altitude_point` | `0x7f0803a1` | 827 | `c14e599a985d08fa…` |
| `svg_arrow_down.xml` | `svg_arrow_down` | `0x7f0803a2` | 661 | `4e24f7e23b826b48…` |
| `svg_arrow_right.xml` | `svg_arrow_right` | `0x7f0803a3` | 631 | `e4559271c0c0167e…` |
| `svg_arrow_right_small.xml` | `svg_arrow_right_small` | `0x7f0803a4` | 460 | `0b95cf2d71cd378c…` |
| `svg_back_circle.xml` | `svg_back_circle` | `0x7f0803a5` | 702 | `38d2db98dcbfc08e…` |
| `svg_bill_apply.xml` | `svg_bill_apply` | `0x7f0803a6` | 2294 | `979f6b478472ccd8…` |
| `svg_bottom_delete.xml` | `svg_bottom_delete` | `0x7f0803a7` | 1910 | `256a43108e227098…` |
| `svg_bottom_detail.xml` | `svg_bottom_detail` | `0x7f0803a8` | 1374 | `e10bc6e5469bb76c…` |
| `svg_bottom_edit.xml` | `svg_bottom_edit` | `0x7f0803a9` | 1955 | `144aff35d1c849b5…` |
| `svg_bottom_hide.xml` | `svg_bottom_hide` | `0x7f0803aa` | 1836 | `9889083a775f6c09…` |
| `svg_bottom_intro.xml` | `svg_bottom_intro` | `0x7f0803ab` | 1908 | `df2483236e158eb6…` |
| `svg_bottom_moutain.xml` | `svg_bottom_moutain` | `0x7f0803ac` | 1323 | `40302c9094ee86eb…` |
| `svg_bottom_sheet_close.xml` | `svg_bottom_sheet_close` | `0x7f0803ad` | 965 | `551532c1c25c0621…` |
| `svg_bottom_show.xml` | `svg_bottom_show` | `0x7f0803ae` | 1026 | `c2a27a29b2954e89…` |
| `svg_bottom_sync.xml` | `svg_bottom_sync` | `0x7f0803af` | 1638 | `e6283e23c668b29e…` |
| `svg_bottom_weather.xml` | `svg_bottom_weather` | `0x7f0803b0` | 4613 | `35b80d8f17238782…` |
| `svg_cancel_import.xml` | `svg_cancel_import` | `0x7f0803b1` | 1429 | `d3ba425fa513a60a…` |
| `svg_clear.xml` | `svg_clear` | `0x7f0803b2` | 1592 | `9afc99839985347d…` |
| `svg_close.xml` | `svg_close` | `0x7f0803b3` | 1083 | `bb056395c5d6676e…` |
| `svg_close3.xml` | `svg_close3` | `0x7f0803b4` | 914 | `4cc7ff1964686d74…` |
| `svg_collect.xml` | `svg_collect` | `0x7f0803b5` | 1657 | `e59a91fdbf65f6f9…` |
| `svg_collect_list.xml` | `svg_collect_list` | `0x7f0803b6` | 1880 | `84c5d601dc3b0e73…` |
| `svg_compass.xml` | `svg_compass` | `0x7f0803b7` | 1047 | `23316aa139fb59f8…` |
| `svg_compass_alpha.xml` | `svg_compass_alpha` | `0x7f0803b8` | 812 | `d40bbf9dea4eec20…` |
| `svg_compass_lock.xml` | `svg_compass_lock` | `0x7f0803b9` | 1718 | `e609f51f6aa172e3…` |
| `svg_compass_reset.xml` | `svg_compass_reset` | `0x7f0803ba` | 1726 | `576a8e3e9ee9ce05…` |
| `svg_compass_rotate.xml` | `svg_compass_rotate` | `0x7f0803bb` | 1355 | `cdf737ee6c42bee6…` |
| `svg_compass_scale.xml` | `svg_compass_scale` | `0x7f0803bc` | 1149 | `ab0579604ebc1717…` |
| `svg_compass_skyline.xml` | `svg_compass_skyline` | `0x7f0803bd` | 1627 | `52614652963f5d68…` |
| `svg_compass_switch.xml` | `svg_compass_switch` | `0x7f0803be` | 2020 | `298c461b26d7ca97…` |
| `svg_contact_us.xml` | `svg_contact_us` | `0x7f0803bf` | 3826 | `224b27638b59236a…` |
| `svg_copy.xml` | `svg_copy` | `0x7f0803c0` | 1643 | `4347475874d1fd5b…` |
| `svg_copy2.xml` | `svg_copy2` | `0x7f0803c1` | 1683 | `b38840a17417a941…` |
| `svg_delete.xml` | `svg_delete` | `0x7f0803c2` | 2219 | `e9e988b69701e12e…` |
| `svg_dialog_close.xml` | `svg_dialog_close` | `0x7f0803c3` | 1274 | `4c65af10011bbb4d…` |
| `svg_discovery.xml` | `svg_discovery` | `0x7f0803c4` | 1808 | `c9f168ca4278840e…` |
| `svg_drawer_back.xml` | `svg_drawer_back` | `0x7f0803c5` | 1051 | `bb6e21a1af756d88…` |
| `svg_farmland.xml` | `svg_farmland` | `0x7f0803c6` | 8563 | `5c6a37979fc559e6…` |
| `svg_fengsu.xml` | `svg_fengsu` | `0x7f0803c7` | 4859 | `b27319efcb2ed74a…` |
| `svg_fengxiang.xml` | `svg_fengxiang` | `0x7f0803c8` | 1042 | `17d0032361260a95…` |
| `svg_fish_point.xml` | `svg_fish_point` | `0x7f0803c9` | 1822 | `6a4c171cbb60f8bd…` |
| `svg_geo_imagery.xml` | `svg_geo_imagery` | `0x7f0803ca` | 2179 | `ec3f0c6c0a97d3a4…` |
| `svg_helper.xml` | `svg_helper` | `0x7f0803cb` | 2902 | `dce394bdd5a4c4b9…` |
| `svg_history.xml` | `svg_history` | `0x7f0803cc` | 1463 | `b8f5fd1f445a480a…` |
| `svg_hook2.xml` | `svg_hook2` | `0x7f0803cd` | 428 | `d132c5dfb56f3692…` |
| `svg_hook2_selected.xml` | `svg_hook2_selected` | `0x7f0803ce` | 665 | `ed63d739425af556…` |
| `svg_import.xml` | `svg_import` | `0x7f0803cf` | 1986 | `4ccb3a1f03db2cd1…` |
| `svg_isoline.xml` | `svg_isoline` | `0x7f0803d0` | 2333 | `940bbe2fa75a21b9…` |
| `svg_item_delete3.xml` | `svg_item_delete3` | `0x7f0803d1` | 1609 | `e5d0d5b7edb56eb9…` |
| `svg_item_hook10.xml` | `svg_item_hook10` | `0x7f0803d2` | 714 | `b3bf9ab737c41f64…` |
| `svg_item_hook4.xml` | `svg_item_hook4` | `0x7f0803d3` | 453 | `965d50c6d0ace73c…` |
| `svg_item_hook6.xml` | `svg_item_hook6` | `0x7f0803d4` | 665 | `ed63d739425af556…` |
| `svg_item_hook9.xml` | `svg_item_hook9` | `0x7f0803d5` | 721 | `d2c5420224278bf3…` |
| `svg_item_no_hook3.xml` | `svg_item_no_hook3` | `0x7f0803d6` | 492 | `cac986924c30e095…` |
| `svg_item_no_hook6.xml` | `svg_item_no_hook6` | `0x7f0803d7` | 428 | `d132c5dfb56f3692…` |
| `svg_jiangshui.xml` | `svg_jiangshui` | `0x7f0803d8` | 1261 | `8404e45aa938c950…` |
| `svg_kqzl.xml` | `svg_kqzl` | `0x7f0803d9` | 1245 | `b8f5125ec7d9a2d3…` |
| `svg_layer.xml` | `svg_layer` | `0x7f0803da` | 1348 | `3fc8a1eda0fccbd4…` |
| `svg_locate.xml` | `svg_locate` | `0x7f0803db` | 2248 | `6905bc68aee674ae…` |
| `svg_locate_0.xml` | `svg_locate_0` | `0x7f0803dc` | 1567 | `f890ee58f4ae3344…` |
| `svg_locate_1.xml` | `svg_locate_1` | `0x7f0803dd` | 1867 | `19559d4e4fdb6a1a…` |
| `svg_locate_2.xml` | `svg_locate_2` | `0x7f0803de` | 1877 | `6e1b74c5d5d25203…` |
| `svg_location.xml` | `svg_location` | `0x7f0803df` | 729 | `500864ce88adae66…` |
| `svg_main_drawer.xml` | `svg_main_drawer` | `0x7f0803e0` | 1126 | `60c3600e805005c7…` |
| `svg_map_temp_clear.xml` | `svg_map_temp_clear` | `0x7f0803e1` | 1036 | `45933074cecd235d…` |
| `svg_measure.xml` | `svg_measure` | `0x7f0803e2` | 1189 | `425c9ca2ba0dedd6…` |
| `svg_menu_add.xml` | `svg_menu_add` | `0x7f0803e3` | 1041 | `69118125d193e760…` |
| `svg_menu_added.xml` | `svg_menu_added` | `0x7f0803e4` | 1049 | `d5a1bff188548c77…` |
| `svg_menu_share.xml` | `svg_menu_share` | `0x7f0803e5` | 2537 | `6eeeb76d63c96e82…` |
| `svg_moonrise.xml` | `svg_moonrise` | `0x7f0803e6` | 1052 | `3cec0da682064465…` |
| `svg_more.xml` | `svg_more` | `0x7f0803e7` | 1632 | `914f43d9cb18f5f0…` |
| `svg_mountain.xml` | `svg_mountain` | `0x7f0803e8` | 1114 | `ec6fa1723a293e30…` |
| `svg_mountain2.xml` | `svg_mountain2` | `0x7f0803e9` | 1174 | `d2180c3ad072e58a…` |
| `svg_navigate.xml` | `svg_navigate` | `0x7f0803ea` | 838 | `70a447f0456594c2…` |
| `svg_north_needle.xml` | `svg_north_needle` | `0x7f0803eb` | 1146 | `fc8d99684cafec72…` |
| `svg_overlay.xml` | `svg_overlay` | `0x7f0803ec` | 1150 | `dfdb3c6c2980ae8d…` |
| `svg_paint.xml` | `svg_paint` | `0x7f0803ed` | 1076 | `d7bf8ded11da1d8e…` |
| `svg_pay_alipay.xml` | `svg_pay_alipay` | `0x7f0803ee` | 1768 | `5679f7a5e8d90a92…` |
| `svg_pay_wechat.xml` | `svg_pay_wechat` | `0x7f0803ef` | 2554 | `1f9e78228b1a0d8f…` |
| `svg_place_small.xml` | `svg_place_small` | `0x7f0803f0` | 614 | `cbf9ac32af4a1217…` |
| `svg_preview.xml` | `svg_preview` | `0x7f0803f1` | 1855 | `c623fb8e562191c2…` |
| `svg_qiya.xml` | `svg_qiya` | `0x7f0803f2` | 2353 | `5ca902a28b2d3819…` |
| `svg_read_compass.xml` | `svg_read_compass` | `0x7f0803f3` | 3566 | `151bce43085aee71…` |
| `svg_recovery.xml` | `svg_recovery` | `0x7f0803f4` | 1185 | `d4ae475e61cbfc91…` |
| `svg_refresh.xml` | `svg_refresh` | `0x7f0803f5` | 1272 | `92b11cbb8eee193d…` |
| `svg_refresh2.xml` | `svg_refresh2` | `0x7f0803f6` | 1077 | `e86b05615134dd7f…` |
| `svg_reload.xml` | `svg_reload` | `0x7f0803f7` | 2027 | `0466d309f7bc8d71…` |
| `svg_remove.xml` | `svg_remove` | `0x7f0803f8` | 823 | `b3af1652676ceae6…` |
| `svg_road.xml` | `svg_road` | `0x7f0803f9` | 1684 | `8d2e40a60d1d510c…` |
| `svg_scropy.xml` | `svg_scropy` | `0x7f0803fa` | 1840 | `5b9bc6c9fc45dddc…` |
| `svg_search.xml` | `svg_search` | `0x7f0803fb` | 905 | `4fdac370de401d57…` |
| `svg_secrecy_policy.xml` | `svg_secrecy_policy` | `0x7f0803fc` | 3057 | `fd276caa492a6687…` |
| `svg_setting.xml` | `svg_setting` | `0x7f0803fd` | 4638 | `204e8f0de278c3b4…` |
| `svg_shidu.xml` | `svg_shidu` | `0x7f0803fe` | 1396 | `dfe446e1dc1fcc5b…` |
| `svg_street.xml` | `svg_street` | `0x7f0803ff` | 2024 | `42e504ebe48a40da…` |
| `svg_sunrise.xml` | `svg_sunrise` | `0x7f080400` | 2237 | `e35e61492fdd6b53…` |
| `svg_super_map.xml` | `svg_super_map` | `0x7f080401` | 2500 | `0055b643eee556d4…` |
| `svg_super_map_old.xml` | `svg_super_map_old` | `0x7f080402` | 2124 | `77c821b3a7a0a866…` |
| `svg_super_star.xml` | `svg_super_star` | `0x7f080403` | 2002 | `6bdb258df13f9c4a…` |
| `svg_surround.xml` | `svg_surround` | `0x7f080404` | 2504 | `3c34f913f44d0585…` |
| `svg_surround_selected.xml` | `svg_surround_selected` | `0x7f080405` | 2504 | `5bab0ddfc54f84ac…` |
| `svg_task.xml` | `svg_task` | `0x7f080406` | 1533 | `534615905e8d63fb…` |
| `svg_tgwd.xml` | `svg_tgwd` | `0x7f080407` | 2538 | `0b605b1f7b86b896…` |
| `svg_tiedi.xml` | `svg_tiedi` | `0x7f080408` | 1281 | `7f8118a8dd14c631…` |
| `svg_tips.xml` | `svg_tips` | `0x7f080409` | 1318 | `adcf539768cd239d…` |
| `svg_title_back.xml` | `svg_title_back` | `0x7f08040a` | 456 | `14be0ac5f1fef97c…` |
| `svg_title_close.xml` | `svg_title_close` | `0x7f08040b` | 928 | `cc57572c18ed2e55…` |
| `svg_tool_folder.xml` | `svg_tool_folder` | `0x7f08040c` | 1284 | `fdda8bf5547523f2…` |
| `svg_tool_layer.xml` | `svg_tool_layer` | `0x7f08040d` | 1381 | `4d696159d2eef7d0…` |
| `svg_tool_search.xml` | `svg_tool_search` | `0x7f08040e` | 1013 | `184e4b9dccaa0c57…` |
| `svg_tool_setting.xml` | `svg_tool_setting` | `0x7f08040f` | 1153 | `dd74541b34602cd4…` |
| `svg_tools_layer.xml` | `svg_tools_layer` | `0x7f080410` | 1348 | `1a273491f74c55c6…` |
| `svg_tools_search.xml` | `svg_tools_search` | `0x7f080411` | 1122 | `c28e4aa8808d5b6a…` |
| `svg_topic_center_cross.xml` | `svg_topic_center_cross` | `0x7f080412` | 1018 | `a93711ac0868e890…` |
| `svg_tousu.xml` | `svg_tousu` | `0x7f080413` | 2787 | `3013fd9e6c00f73f…` |
| `svg_track.xml` | `svg_track` | `0x7f080414` | 1765 | `35fd9c9247c5f8f2…` |
| `svg_track_camera.xml` | `svg_track_camera` | `0x7f080415` | 740 | `f3859857408ce3d1…` |
| `svg_track_history.xml` | `svg_track_history` | `0x7f080416` | 1362 | `b7aaf3b5e416791a…` |
| `svg_track_look_all.xml` | `svg_track_look_all` | `0x7f080417` | 1236 | `e4513393662988cc…` |
| `svg_track_pause.xml` | `svg_track_pause` | `0x7f080418` | 764 | `fab26050fd3899ff…` |
| `svg_track_record.xml` | `svg_track_record` | `0x7f080419` | 737 | `94738d73976b0472…` |
| `svg_track_resume.xml` | `svg_track_resume` | `0x7f08041a` | 736 | `d465a50d4536f51e…` |
| `svg_track_setting.xml` | `svg_track_setting` | `0x7f08041b` | 2063 | `13e27bbc8add9b60…` |
| `svg_track_stop.xml` | `svg_track_stop` | `0x7f08041c` | 463 | `d28091a7e949aaf6…` |
| `svg_track_video_time.xml` | `svg_track_video_time` | `0x7f08041d` | 2079 | `18a36d47b3cbd990…` |
| `svg_user_agreement.xml` | `svg_user_agreement` | `0x7f08041e` | 3075 | `0543423d5c355451…` |
| `svg_vip_tips.xml` | `svg_vip_tips` | `0x7f08041f` | 1304 | `4695c4d0fad7935d…` |
| `svg_vr2.xml` | `svg_vr2` | `0x7f080420` | 1101 | `ee02241c3b349ebd…` |
| `svg_water.xml` | `svg_water` | `0x7f080421` | 3348 | `926c542208b66f93…` |
| `svg_water2.xml` | `svg_water2` | `0x7f080422` | 2607 | `66e516e970b38e16…` |
| `svg_weather.xml` | `svg_weather` | `0x7f080423` | 2878 | `d1c4dd37a716f8c4…` |
| `svg_zoom_in.xml` | `svg_zoom_in` | `0x7f080424` | 1039 | `f8a98325ab48f716…` |
| `svg_zoom_out.xml` | `svg_zoom_out` | `0x7f080425` | 481 | `1346ac6140fb46de…` |

合计 **156** 个文件（含 `$svg_*` 拆分片段）。

## 3. 代码锚点（`R$drawable;->svg_*` 引用点）

| 资源名 | 引用点 | 备注 |
|---|---|---|
| `svg_setting` | `ui\pages\MainActivity$A.smali:5627` | 2 refs 处引用，邻近文案已核 |
| `svg_account_setting` | `ui\pages\MainActivity.smali:316` | 1 refs 处引用，邻近文案已核 |
| `svg_tousu` | `ui\pages\MainActivity.smali:334` | 1 refs 处引用，邻近文案已核 |
| `svg_about` | `ui\pages\MainActivity.smali:352` | 1 refs 处引用，邻近文案已核 |
| `svg_contact_us` | `ui\pages\MainActivity.smali:370` | 1 refs 处引用，邻近文案已核 |
| `svg_menu_share` | `ui\pages\MainActivity.smali:388` | 1 refs 处引用，邻近文案已核 |
| `svg_bill_apply` | `ui\pages\MainActivity.smali:406` | 1 refs 处引用，邻近文案已核 |
| `svg_helper` | `ui\pages\MainActivity.smali:424` | 1 refs 处引用，邻近文案已核 |
| `svg_user_agreement` | `ui\pages\MainActivity.smali:442` | 1 refs 处引用，邻近文案已核 |
| `svg_tool_folder` | — | smali 里无引用：原版用彩色位图，不走矢量（见下） |
| `svg_scropy` | `ui\pages\MainActivity.smali:31269` | 3 refs 处引用，邻近文案已核 |
| `svg_super_map_old` | `common\ToolsEnum.smali:566` | 1 refs 处引用，邻近文案已核 |
| `svg_altitude` | `common\ToolsEnum.smali:278` | 1 refs 处引用，邻近文案已核 |
| `svg_compass` | `common\ToolsEnum.smali:158` | 1 refs 处引用，邻近文案已核 |
| `svg_road` | `common\ToolsEnum.smali:518` | 1 refs 处引用，邻近文案已核 |
| `svg_isoline` | `common\ToolsEnum.smali:350` | 7 refs 处引用，邻近文案已核 |
| `svg_more` | `ui\pages\MainActivity$h.1.smali:218` | 1 refs 处引用，邻近文案已核 |
| `svg_layer` | `ui\pages\MainActivity.smali:15130` | 1 refs 处引用，邻近文案已核 |
| `svg_overlay` | `ui\pages\MainActivity.smali:15287` | 1 refs 处引用，邻近文案已核 |
| `svg_mountain` | `ui\pages\MainActivity.smali:15367` | 1 refs 处引用，邻近文案已核 |
| `svg_water` | `ui\pages\MainActivity.smali:15447` | 1 refs 处引用，邻近文案已核 |
| `svg_discovery` | `ui\pages\MainActivity.smali:15527` | 1 refs 处引用，邻近文案已核 |
| `svg_north_needle` | — | smali 里无引用：原版用彩色位图，不走矢量（见下） |
| `svg_zoom_in` | `ui\compose\u4.smali:983` | 1 refs 处引用，邻近文案已核 |
| `svg_zoom_out` | `ui\compose\u4.smali:1394` | 1 refs 处引用，邻近文案已核 |
| `svg_locate` | `ui\compose\p1.1.smali:604` | 2 refs 处引用，邻近文案已核 |

**`svg_tool_folder`（全地图工具）与 `svg_north_needle`（指北针）在 smali 里没有 `R$drawable` 引用**：
原版截图中这两个按钮是**彩色位图**（全地图工具=扳手/锤子，指北针=红白针），不是白线矢量；
对应位图在 `mipmap-xxxhdpi/ic_*` 里，属位图那批的追加项，本文件不硬套。

**右列 3D** 同理：`svg_*` 集里没有 3D，原版用 `mipmap-xxxhdpi/ic_3d_white.png`（青色立方线稿）。
