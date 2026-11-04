# 审图号核对留档（T-13）

> 状态：**未确认**（2026-11-05）｜负责人：LC产品｜回填后请更新 `docs/product/PRD-v0.1.md` §5.1 与 T-13

## 1. 已尝试的官方渠道（全部失败）

| # | 渠道 | URL | 结果 |
|---|---|---|---|
| 1 | 天地图官网首页 | `https://www.tianditu.gov.cn/` | **HTTP 418**（WAF 拦截），事件 ID `08-clou-148-20261105033844-d7db7061` |
| 2 | 天地图官网 about | `https://www.tianditu.gov.cn/about/` | **HTTP 418**，事件 ID `90-clou-148-20261105033844-17c9c91f` |
| 3 | 天地图 WMTS 能力文档 | `http://t0.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetCapabilities` | **HTTP 418**，事件 ID `30-clou-148-20261105033849-a547dfba` |
| 4 | 只读文本镜像（r.jina.ai 代理） | `https://r.jina.ai/https://www.tianditu.gov.cn/` | 镜像返回 **418**，事件 ID `32-clou-153-20261105033921-17d6ef47` |
| 5 | 天地图授权说明页 | `http://lbs.tianditu.gov.cn/authorization/authorization.html` | **HTTP 418**（早些时候已记录） |

结论：**本机网络被天地图 WAF 拦截，拿不到官方页脚/控制台里的审图号**；不猜、不引用第三方博客里的号。

## 2. 人工核对路径（需要一台能正常访问的浏览器）

1. 打开 `https://www.tianditu.gov.cn/`，看页脚版权区（通常有「审图号：GS(20xx)xxxx号」与「数据来源」）。
2. 登录天地图开发者控制台，看应用/服务详情页的服务声明（部分节点在这里标注当前服务的审图号）。
3. 分别核对**卫星（img）、地图（vec）、地形（ter）**三种服务；若三者共用一个号，在表格里注明「三源共用」。
4. 截图（含地址栏与日期），存到本目录：`tianditu-portal-footer-<yyyyMMdd>.png`、`tianditu-console-<yyyyMMdd>.png`。
5. 把号回填 PRD §5.1 表格，状态改「已核对（yyyy-mm-dd）」，同时更新 T-13。

## 3. 纪律

- 核对完成前，界面/分享图只显示「地图来源：天地图 · 审图号待核对」。
- **不得**照抄世纪空间、高德、腾讯、中科星图、吉林一号的审图号；那是别家地图的审批号。
- 审图号只从官方页面/控制台取，不从博客、二手文章、竞品 App 里取。
- **已知的反面样本**：逆向拍到原版地图页底部「数据来自世纪空间 审图号: `GS(2026)1219号`」（`win-01-tools.png`）。那是世纪空间的号，LC 未接入、也不得复制；它只证明「地图页必须按源显示审图号」这一行为。
