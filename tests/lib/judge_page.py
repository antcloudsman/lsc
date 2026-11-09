"""M6 四页判据：三条判据各自出 PASS/FAIL（F-01/F-02 修复）。

输入：原版 ref xml + 我方 act xml + 我方 act png（+ 白名单/步进/载体/主题参数）。
输出（每行一个判据，供 PowerShell 解析）：
  TEXT=PASS|FAIL     文本缺失（白名单外）
  BOUNDS=PASS|FAIL   匹配节点 bounds 四边 Δ≤2px
  STEP=PASS|FAIL|NA  匹配节点 y 步进中位数 = 期望 ± 容差
  THEME=PASS|FAIL|NA 底色众数 / 纯白占比 / 字色占比
  CARRIER=PASS|FAIL|NA 载体性质（dialog 弹层 vs page 整页）
  VERDICT=PASS|FAIL  以上全 PASS（NA 不计）
用法：
  python tests/lib/judge_page.py --ref r.xml --act-xml a.xml --act-png a.png \
      --ignore-text 昵称,头像 --step 163 --step-tol 2 --carrier page \
      --theme-bg F0F3F7 --theme-text 23272B --theme-white-min 5
退出码：0 = VERDICT PASS；1 = FAIL；2 = 参数/输入错误。
"""
import argparse
import os
import statistics
import sys
import warnings
from collections import Counter

warnings.filterwarnings('ignore', category=DeprecationWarning)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from compare_page import match, carrier_kind, nodes  # noqa: E402

TEXT_MIN_PCT = 0.10   # 字色像素占比下限（原版实测 0.16%–0.48%；LC 深色页 0.00%）
RADIUS = 3            # 色值容差（/255）


def hex2rgb(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def img_stats(path):
    from PIL import Image
    im = Image.open(path).convert('RGB')
    px = list(im.getdata())
    n = len(px)
    return Counter(px), n


def frac_near(c, n, target, radius=RADIUS):
    hit = sum(v for k, v in c.items() if all(abs(k[i] - target[i]) <= radius for i in range(3)))
    return 100.0 * hit / n if n else 0.0


def theme_check(png, bg_hex, text_hex, white_min):
    """返回 (ok, 明细行列表)；任一子项失败即 False。"""
    c, n = img_stats(png)
    mode = c.most_common(1)[0][0]
    ok = True
    details = []
    if bg_hex:
        want = hex2rgb(bg_hex)
        good = all(abs(mode[i] - want[i]) <= RADIUS for i in range(3))
        ok = ok and good
        details.append(f"底色众数={mode} 期望={want} {'OK' if good else 'FAIL'}")
    if white_min is not None:
        wpct = frac_near(c, n, (255, 255, 255), 0)
        good = wpct >= white_min
        ok = ok and good
        details.append(f"纯白占比={wpct:.2f}% ≥{white_min}% {'OK' if good else 'FAIL'}")
    if text_hex:
        want = hex2rgb(text_hex)
        tpct = frac_near(c, n, want, RADIUS)
        good = tpct >= TEXT_MIN_PCT
        ok = ok and good
        details.append(f"字色占比={tpct:.2f}% ≥{TEXT_MIN_PCT}% {'OK' if good else 'FAIL'}")
    return ok, details


def act_step(act_xml, region):
    """步进 = 我方页面内、指定 y 区域内文本节点的相邻 y 中心差中位数（与文本是否匹配无关）。

    依据 verify-four-pages-client §1.2「步进＝相邻条目 y 中心差」；白名单把某些行从
    「缺失判定」里摘掉后，仍要能判这些行的**行高节奏**，所以按 act 侧 y 位置算。
    """
    nodes_list = nodes(act_xml)
    y1, y2 = region
    centers = sorted({round((n['t_'] + n['b']) / 2) for n in nodes_list if y1 <= n['t_'] <= y2})
    diffs = [centers[i + 1] - centers[i] for i in range(len(centers) - 1)]
    diffs = [d for d in diffs if d > 0]
    return (statistics.median(diffs) if diffs else None), centers


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ref', required=True)
    ap.add_argument('--act-xml', required=True)
    ap.add_argument('--act-png', required=True)
    ap.add_argument('--ignore-text', default='')
    ap.add_argument('--step', type=float, default=None)
    ap.add_argument('--step-tol', type=float, default=2.0)
    ap.add_argument('--step-region', default=None, help='按 act 侧 y 区域算步进，形如 300:2000')
    ap.add_argument('--carrier', choices=['page', 'dialog'], default=None)
    ap.add_argument('--theme-bg', default=None, help='期望底色众数 RRGGBB')
    ap.add_argument('--theme-text', default=None, help='期望字色 RRGGBB（占比 ≥0.10%%）')
    ap.add_argument('--theme-white-min', type=float, default=None, help='纯白像素占比下限 %%')
    a = ap.parse_args()

    ignore = {x.strip() for x in a.ignore_text.split(',') if x.strip()}
    r = match(a.ref, a.act_xml, ignore)

    text_ok = len(r['missing']) == 0
    bounds_ok = r['maxd'] <= 2
    print(f"TEXT={'PASS' if text_ok else 'FAIL'}")
    if r['missing']:
        print("MISSING_TEXTS=" + " | ".join(x[0] for x in r['missing'][:12]))
    print(f"BOUNDS={'PASS' if bounds_ok else 'FAIL'}")
    print(f"max_bounds_delta={r['maxd']} matched={len(r['matched'])}")
    if r['bad']:
        print("OVER2PX=" + " | ".join(f"{t}({d[0]},{d[1]},{d[2]},{d[3]})" for t, d, _ in r['bad'][:12]))

    if a.step is None:
        step_ok = None
        print("STEP=NA")
    else:
        if a.step_region:
            y1, y2 = (float(x) for x in a.step_region.split(':'))
            med, centers = act_step(a.act_xml, (y1, y2))
            print(f"step_centers={centers}")
        else:
            med = r['step_median']
        step_ok = med is not None and abs(med - a.step) <= a.step_tol
        print(f"STEP={'PASS' if step_ok else 'FAIL'}")
        print(f"step_y_median={med} expect={a.step}±{a.step_tol}")

    if a.carrier is None:
        carrier_ok = None
        print("CARRIER=NA")
    else:
        kind = carrier_kind(a.act_xml)
        carrier_ok = kind == a.carrier
        print(f"CARRIER={'PASS' if carrier_ok else 'FAIL'}（实际={kind} 期望={a.carrier}）")

    theme_ok = None
    if a.theme_bg or a.theme_text or a.theme_white_min is not None:
        try:
            theme_ok, details = theme_check(a.act_png, a.theme_bg, a.theme_text, a.theme_white_min)
        except Exception as e:
            print(f"THEME=FAIL（读图失败: {e}）")
            print("VERDICT=FAIL")
            return 1
        print(f"THEME={'PASS' if theme_ok else 'FAIL'}")
        print("theme_detail=" + " ; ".join(details))
    else:
        print("THEME=NA")

    checks = [x for x in (text_ok, bounds_ok, step_ok, carrier_ok, theme_ok) if x is not None]
    verdict_ok = all(checks)
    print("VERDICT=" + ("PASS" if verdict_ok else "FAIL"))
    return 0 if verdict_ok else 1


if __name__ == '__main__':
    sys.exit(main())
