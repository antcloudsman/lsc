# R2-013 A 项：读盘 sheet 行尾「◆」定位结论

结论一句话：**那不是 `◆` 字符，也不是自绘菱形，是原版位图 `ic_compass_vip.webp`（金色钻石 + V 的 VIP 徽标）**。已按 `assets.py` 纳入素材库。

## 1. 形态判定（先定性，再找文件）

| 判据 | 结果 | 出处 |
| --- | --- | --- |
| 字符串载体（dex / smali / 资源值 XML）里有无 `◆`（U+25C6） | **无**（`mem_smali` 7698 个 smali、`mem_dump` 30 个 dex、`decoded/res` 全部 XML 均无命中） | `probes/r2-013_vip_badge_scan.py` |
| 二进制文件里有无该字节序列 | 有 6 个（`.png/.webp/.pag/.zip`），**属字节巧合**，不是字符串资源；脚本已把两类分开统计 | 同上输出：`字符串载体命中 0 个；二进制文件命中 6 个` |
| 截图里它长什么样 | 金色钻石，内有字母 **V**；行结构是「圈层名 + 这枚徽标 + `点击查看 ›`」 | `probes/r2-013/r2-013-vip-badge-compare.png` |

→ 定性：**带 V 的 VIP 徽标位图**，因此「按 74×54 找 drawable」这个判据本身要订正（见 §3）。

## 2. 素材文件定位（实测，不是「看起来像」）

- 资源名 **`ic_compass_vip`**，源文件 `res/mipmap-xxxhdpi/ic_compass_vip.webp`，**132×112**，2074 B。
- APK 条目 **`res/Dn.webp`**（资源名被 `is.resFiles` 混淆，按解码件 sha256 与 APK 内条目逐一比对命中）。
- sha256 = `5e2811b6914f53b162bfa3cb1b7c62378eab8c4612711117a129634b41e39e48`。
- 落库路径：`docs/fidelity/assets/compass/ic_compass_vip.webp`（`assets-sources.json` 新增 1 条，`kind=bitmap`）。

### 2.1 为什么是这一张

1. **比例**：截图金色像素外接框 `[861,1966][936,2030]` = **76×65**，比例 **1.169**；资源 132×112 比例 **1.179**，差 0.8%。
2. **全量比对**：把 `decoded/res` 下**所有** PNG/WebP（≤500×500）缩放到 76×65，与截图该区域算平均绝对误差，取最小：

```
MAE  13.41  mipmap-xxxhdpi\ic_compass_vip.webp   132x112   ← 命中
MAE  19.97  mipmap-xxxhdpi\ic_tab_indicator2.png  67x31
MAE  25.58  mipmap-xxxhdpi\ic_coin.png            84x80
MAE  26.37  drawable-xhdpi\tt_reward_browse_multi_icon.png 280x104
```

   第一名与第二名差 6.56，不是近似撞车。

## 3. 判据订正（给审核验 / 产品）

- 原判据「单枚 bbox `[862,1967][935,2020]` = **74×54**」量到的是 **a11y 节点框，被裁过**：金色像素实际溢到 **y=2030**（比节点底边多 10px），节点框高度被压成 54。
- **正确口径**：图形是 VIP 徽标，绘制框按**金色像素外接框 76×65**（±1px 抗锯齿）判，不看节点框 74×54。
- 判据措辞从「`◆` 74×54 图形」改为「**`ic_compass_vip` 徽标 76×65（容差 ±1px）**」。

## 4. 对实现的要求（客户端）

- 用原版素材 `ic_compass_vip.webp`，**不得用 `◆` 字符**，也不得自绘。
- 位置：紧跟圈层名之后、`点击查看 ›` 之前，行内垂直居中；尺寸按 76×65 摆放（源 132×112 等比缩到高 65 → 宽 ≈76.6，横向 1px 内）。
- 不单列 DI：素材已可用。

## 5. 复现命令

脚本已入库，仓库根目录直接跑（需要 Pillow；`--res` 默认指向 `.inspect` 解码树，可用参数改）：

```powershell
# 1) 定性：字符串载体里有没有 ◆（dex/smali/资源 XML）——应得 PASS
python docs\fidelity\probes\r2-013_vip_badge_scan.py
# 2) 定量：金色外接框 + 全量位图缩放比对，取 MAE 最小——应得 ic_compass_vip.webp
python docs\fidelity\probes\r2-013_vip_badge_match.py --top 4
# 3) 落库 + 门禁
python tools\fidelity\assets.py extract --apk E:\APPDetector\input\swss.apk --sources docs\fidelity\assets-sources.json --out docs\fidelity\assets
python tools\fidelity\assets.py manifest --dir docs\fidelity\assets
python tools\fidelity\assets.py verify --dir docs\fidelity\assets --apk E:\APPDetector\input\swss.apk --strict
```

实测输出（2026-11-07）：

```
[PASS] 字符串载体（dex/smali/资源 XML）里无 U+25C6 -> 「◆」不是字符，是图形
金色像素外接框: [861,1966][936,2030] -> 76x65 ratio=1.1692
MAE= 13.41  mipmap-xxxhdpi\ic_compass_vip.webp  132x112
[PASS] 命中 mipmap-xxxhdpi\ic_compass_vip.webp (MAE=13.41)
manifest: 103 file(s), 10497517 bytes
verify: 103 file(s), 103 manifest entr(ies), 103 provenance row(s); sha256 + provenance + APK source all match  (EXIT=0)
```

## 6. 证据文件

- `probes/r2-013/r2-013-vip-badge-compare.png`：第一行为原版该行上下文（含 `点击查看`），第二行左为截图原位放大 4×、右为素材等比放大——两者同一图形。
- `assets/compass/ic_compass_vip.webp`：落库素材本体（2074 B，sha256 见 §2）。
