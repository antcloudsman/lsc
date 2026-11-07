# M6 Stage 2 复验：第 2 条方向（2a/2b）+ 第 6 条复位 + 第 8 条 3D 三态

- 设备 `L2E0222531014833`；构建 `0113714`（含 2a/2b 修复、resetCompass、3D 失败可读、`last3dWarn` 拆分）
- APK sha256 `f31603d8f814273078e632cb6f78ff3ee030524c01dd94f07ee73880a012a77c`，8,595,293 B
- 装机记录：`m6-install-record.ps1` → PASS，`INSTALL_OK taps=2`、`MARKET_RESTORED=ok`、`lastUpdateTime 03:03:58`、机上 `pm path` size/sha 与构建产物一致
- 上机前置三句：包 sha/size ✔、`getState().uiMode="map"` ✔、市场未禁用 ✔
- 证据：`E:\APPDetector\reports\M6-stage2\`（`drag/` 前后截图、`install/` 装机记录、`3d-*.png`、`states.txt`）

## 负向对照（先证脚本能识别缺陷）

未修版本 `8f1ea11`（sha `2fea976c…`、`lastUpdateTime 02:27:20`）：`m6-drag-verify.ps1` **10/10 FAIL**
（2a 八条全部符号相反；2b 两档角误差 180°）。命令与输出见 `M6-drag` 证据目录。

## 正向：2a 符号（bearing=0，z=18 / z=12）

| 手势 | 手指 | Δ中心 | 判据 | 结果 |
|---|---|---|---|---|
| 右 | (+400,0) | Δlng=−0.000644 | Δlng·dx<0 | PASS |
| 左 | (−400,0) | Δlng=+0.000633 | 同上 | PASS |
| 上 | (0,−400) | Δlat=−0.000574 | Δlat·dy>0 | PASS |
| 下 | (0,+400) | Δlat=+0.000564 | 同上 | PASS |

z=12 同四方向同样 PASS（Δlng ±0.0413/0.0405、Δlat ∓0.0367/0.0359）。**2a：8/8 PASS。**

## 正向：2b 逆旋转（上滑 300px）

| bearing | Δlng | Δlat | 角误差 | 结果 |
|---|---|---|---|---|
| 30° | +0.014954 | −0.023160 | 0° | PASS |
| 330°（镜像档） | −0.015354 | −0.023785 | 0° | PASS |
| 90° | +0.030289 | 0 | 0° | PASS |

判据是「与 `R(−bearing)` 推出的期望向量同向且夹角 ≤3°」，镜像写反会被 330 档抓出。**2b：3/3 PASS。**

## 第 6 条 resetCompass

```
调用前：mode=3d bearing=45 lat=26.559126964684054 lng=106.7511743468213 z=12
调用后：mode=2d bearing=0  lat=26.559126964684054 lng=106.7511743468213 z=12
```
3D 退 2D、bearing 归 0，**center/zoom 一位不差**。

## 第 8 条 3D 三态（真机注入）

| 态 | `last3dError` | `last3dWarn` | `mode` | `hasTexture` | `demZoom` |
|---|---|---|---|---|---|
| 成功 | `null` | `null` | `3d` | `true` | 11 |
| `dem-missing`（TERRARIUM 指不可达 + 清三张 DEM 表 + `ensurePatch(true)`） | `dem-missing` | `dem-edge` | `3d`（不回落） | `true`（沿用旧 patch） | 11 |
| `texture-fail`（`tileUrl` 指不可达 + `refreshPatchTexture()`） | `texture-fail 0/36` | `null` | `3d`（不回落） | `false` | 11 |
| 恢复后 | `null` | `null` | `3d` | `true` | 11 |

- `texture-fail` 按**前缀**判定并记录 `ok/36`（本次 `0/36`）。
- `dem-missing` 实测**不回落、且保留旧 patch**（`hasTexture=true`、`demZoom=11`）；`dem-edge` 落在 `last3dWarn`，不污染成功判定。这与「失败即回落 2d」的统一判法不同，按实现记录，避免误判。
- `webgl-unavailable` **无法注入**（需 GPU 不可用），按代码路径登记：唯一赋值点 + 唯一回落 2d 的分支；本轮未真机触发，**不写「已验证」**。
- 恢复方式：`TERRARIUM` 改回 + 清表 + `ensurePatch(true)`；`tileUrl` 用 `window.__origTileUrl` 还原，均实测恢复成功。

## 边界与未做

- 本轮未验：`webgl-unavailable` 真机触发（不可注入）；`dem-edge` 只在 `last3dWarn`，未单独构造「拖到 patch 边缘」的截图（`dem-missing` 注入时已顺带出现 `dem-edge`）。
- 设备状态：测试用构建 `f31603d8…` 已装机（`lastUpdateTime 03:03:58`），测后释放。
