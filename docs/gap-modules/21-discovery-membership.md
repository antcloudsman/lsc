# 「发现/内容」21 项 · 会员专享标记（取证前置）

- 产出：LC逆向 2026-11-05
- 目的：按技术总监要求，在逐项逆向实现之前先判定「是否商业/会员内容」，带会员/付费标志的直接登记「有意省略（无数据授权）」，不投入逆向。
- 证据口径：
  - **T** = 真机点击实测（`mResumedActivity` 落到 `OpenVipActivity`）
  - **S** = DEX 静态（`android-method-show` / `android-class-users-find`）
  - 全量 VIP 判断点：`android-class-users-find --class-name space.xinzhi.globe.model.AccountModelKt --method-name isVip` → **76 处引用 / 68 个方法**；`OpenVipActivity` 被 **347 处引用 / 185 个方法**调用。以下"有 VIP 判断"均指该清单命中。
- 判定只取四类：**会员专享**（有付费墙判定或实测弹付费墙）/ **未付费不可达**（入口被移除或渠道限制）/ **可等价**（无 VIP 判断且入口可达）/ **未确认**（本次无证据，写明缺口）。

## 1. 总表（MODULES.md 编号）

| # | 组件 | 标记 | 证据 | 处置建议 |
|---|---|---|---|---|
| 73 | `ui.pages.AboutActivity` | **可等价** | T：抽屉「关于我们」点击实测打开；S：该类不在 isVip 清单 | 可做（静态页：版本/备案号/测绘资质/隐私清单） |
| 74 | `ui.pages.discovery.DiscoveryActivity` | **页面可等价，内容=会员专享** | T：右列「探索」点击实测进入；S：卡片点击 lambda `ui/compose/discovery/E$a$b;->a()` 调 `OpenVipActivity$a.a`；`DiscoveryViewModel` 有 isVip | 页面壳可做；**专题卡片内容有意省略** |
| 75 | `ui.pages.discovery.DiscoveryDetailActivity` | **会员专享** | S：`DiscoveryDetailActivity$b$a` 在 isVip 清单（列表/详情按会员分流） | 有意省略 |
| 76 | `ui.pages.discovery.EarthDiscoveryActivity` | **会员专享** | S：`EarthDiscoveryActivity$f$a$a` 在 isVip 清单 | 有意省略 |
| 77 | `ui.pages.discovery.GlobalLiveActivity` | **未确认** | S：该类无 isVip；入口未实测（未在本次设备窗口内点开） | 待第二窗口；倾向省略（直播类） |
| 78 | `ui.pages.discovery.LiveActivity` | **未确认** | S：无 isVip；入口未实测 | 同上 |
| 79 | `ui.pages.discovery.MountainActivity` | **会员专享** | **T：右列「山脉」点击 → `OpenVipActivity`**；S：`MountainActivity` / `$a` / `$c` 均在 isVip 清单 | 有意省略（无山势数据授权） |
| 80 | `ui.pages.discovery.PopulationActivity` | **未确认** | S：无 isVip；入口未实测 | 待第二窗口 |
| 81 | `ui.pages.discovery.PopulationIntroActivity` | **未确认** | S：无 isVip；入口未实测 | 待第二窗口 |
| 82 | `ui.pages.discovery.StreetDetailActivity` | **未确认** | S：无 isVip；入口未实测 | 待第二窗口 |
| 83 | `ui.pages.discovery.StreetDiscoveryActivity` | **未确认** | S：无 isVip；`ToolsEnum.STREET`（景点）被 `getTools()` 从菜单移除 | 倾向「未付费不可达」，待实测确认 |
| 84 | `ui.pages.discovery.WaterActivity` | **会员专享** | **T：右列「水系」点击 → `OpenVipActivity`**；S：`WaterActivity` / `$a` / `$c` 在 isVip 清单 | 有意省略（无水系数据授权） |
| 85 | `ui.pages.GeographicImageryActivity` | **可等价** | S：无 isVip；入口 `ToolsEnum.GEOGRAPHIC_IMAGERY` 正常显示，仅 `ChannelName.isRestrictedFeatureChannel()` 时隐藏 | 可做（但依赖影像源授权，见 F 组） |
| 86 | `ui.pages.HistoryImageActivity` | **会员专享** | S：入口 `MainViewModel.F3(Context)` 判 isVip → `OpenVipActivity.a(ctx, "历史影像")`（source 字符串取证） | 有意省略（历史影像数据源） |
| 87 | `ui.pages.SearchActivity` | **部分会员** | T：搜索框点击实测进入；S：`SearchActivity` 在 isVip 清单（部分能力按会员分流，具体分支未逐行确认） | 做基础搜索；会员分支省略 |
| 88 | `ui.pages.SuperMapListActivity` | **会员专享** | S：入口 `MainViewModel.y1(Context)` 判 isVip → `OpenVipActivity.a(ctx, "超清地图")`；真机「超清地图」带限时锁角标 | 有意省略（超清瓦片数据源） |
| 89 | `ui.pages.SuperMapPreviewActivity` | **未确认** | S：无 isVip；随 88 的超清链路，入口未单独实测 | 默认随 88 省略，待确认 |
| 90 | `ui.pages.VideoPlayActivity` | **未确认** | S：无 isVip；入口未实测（疑为发现详情/直播播放器） | 待第二窗口 |
| 91 | `ui.pages.VrActivity` | **未付费不可达** | S：`ToolsEnum.getTools()` 显式 `remove(VR)`（与 STREET 同批移除）；类内无 isVip；点击派发 `V8` case13 仍存在 | 不做（入口默认不展示） |
| 92 | `ui.pages.WeatherActivity` | **可等价** | S：`V8` case2 直接 `WeatherActivity$a.a(ctx)`，无 isVip 判断；类内无 isVip | 可做（需天气接口授权，另行评估） |
| 93 | `ui.pages.WebActivity` | **可等价（容器）** | T：抽屉「帮助中心」实测打开 WebView；S：抽屉 6 条链接均走 `WebActivity$a.b(ctx,url,title,false)`；容器内 `WebActivity$onCreate$1$1$2` 有 isVip（H5 内会员逻辑） | 容器必须做（协议/隐私/sdk清单都靠它） |

## 2. 有付费墙判定的完整类清单（供后续复核）

```
manager/ADHelper, manager/AppConfig, manager/LogUploadManager, manager/ToolsManager
ui/compose/discovery/E, ui/compose/h, ui/compose/h$e, ui/compose/I1, ui/compose/P1$c, ui/compose/v1, ui/compose/W
ui/pages/CompassListActivity$b$a$a$e
ui/pages/discovery/DiscoveryDetailActivity$b$a
ui/pages/discovery/EarthDiscoveryActivity$f$a$a
ui/pages/discovery/MountainActivity, $a, $c
ui/pages/discovery/WaterActivity, $a, $c
ui/pages/guide/GuideActivity, ui/pages/guide/VipCenterFragment
ui/pages/interest/SaveInterestActivity
ui/pages/MainActivity, MainActivity$A
ui/pages/OpenVipActivity
ui/pages/SearchActivity
ui/pages/setting/feedback/ComplaintFeedbackActivity$b$b
ui/pages/WebActivity$onCreate$1$1$2
ui/tools/o, ui/tools/o$a
viewmodel/DiscoveryViewModel, viewmodel/MainViewModel(+$h$a/$i/$k)
```

## 3. 本次得到的"付费墙来源标签"（`OpenVipActivity.a(Context,String)` 的 String 实参）

| 来源标签 | 触发方法 | 证据 |
|---|---|---|
| `超清地图` | `MainViewModel.y1(Context)` | @0x0033 `const-string "超清地图"` |
| `历史影像` | `MainViewModel.F3(Context)` | @0x002c `const-string "历史影像"` |
| `钓点` | `MainViewModel.F1(Context)` | @0x0035 `const-string "钓点"` |
| `等高线` | `MainViewModel.G3(Context)` | @0x003c `const-string "等高线"` |
| 罗盘类 | `MainViewModel.D1()` | 有 isVip 判断（`compass_show` 埋点），未见 OpenVip 调用 → 疑为站内引导弹层，未确认 |

这些标签是原版埋点/引导语，可作 LC 侧「已解除付费墙」横幅的文案参考（但 LC 按 D-006 不收费）。

## 4. 结论

1. 「发现/内容」21 项里，**12 项可确认为会员/商业内容或会员不可达**（75、76、79、84、86、88 + 74 的内容层 + 87 的部分 + 91 + 83 倾向），按「有意省略」登记，不投入实现。
2. **3 项可等价实现**（73 关于我们、85 地理影像、92 天气）+ **1 项容器必须做**（93 WebActivity）。
3. 其余 **8 项未确认**（77、78、80、81、82、83、89、90），缺口是「入口未实测」而非"没找"，等第二设备窗口或补充静态链路后定。
4. 与总纲一致：这些省略项在差距矩阵里统一写「有意省略（无数据授权）」，不写"未实现"。
