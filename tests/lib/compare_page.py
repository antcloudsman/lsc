import sys, re, statistics
import xml.etree.ElementTree as ET


def nodes(path):
    tree = ET.parse(path)
    out = []
    for n in tree.getroot().iter('node'):
        t = n.get('text') or ''
        b = n.get('bounds') or ''
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
        if not t.strip() or not m:
            continue
        l, tp, r, bo = (int(m.group(i)) for i in range(1, 5))
        if r - l <= 0 or bo - tp <= 0:
            continue
        out.append({'t': t, 'l': l, 't_': tp, 'r': r, 'b': bo,
                    'id': n.get('resource-id') or '', 'cls': n.get('class') or ''})
    return out


def carrier(path):
    tree = ET.parse(path)
    root = tree.getroot().find('node')
    if root is None:
        return ('unknown', '', False, False)
    b = root.get('bounds') or ''
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
    area = 0
    if m:
        area = (int(m.group(3)) - int(m.group(1))) * (int(m.group(4)) - int(m.group(2)))
    has_content = any(n.get('resource-id') == 'android:id/content' for n in tree.getroot().iter('node'))
    full = area >= 1228 * 2400
    return (root.get('class') or '', b, has_content, full)


def carrier_kind(path):
    """载体性质：看**顶层窗口节点**（hierarchy 的第一个 node）。

    dialog = 顶层节点是 FrameLayout/Dialog 且不是全屏（弹层窗口，如原版「联系我们」[168,1127][1059,1694]）；
    page   = 顶层节点全屏或自身是 android:id/content（整页 Activity）。
    不能看全树的 android:id/content：原版 dialog dump 里也带有下层 Activity 的 content 节点
    （contact.xml 实测 content id = 1），按全树判会把弹层误判成整页。
    """
    root = ET.parse(path).getroot()
    top = root.find('node')
    if top is None:
        return 'unknown'
    b = top.get('bounds') or ''
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
    area = (int(m.group(3)) - int(m.group(1))) * (int(m.group(4)) - int(m.group(2))) if m else 0
    cls = top.get('class') or ''
    rid = top.get('resource-id') or ''
    if rid == 'android:id/content' or area >= 1228 * 2400:
        return 'page'
    if 'Dialog' in cls or 'FrameLayout' in cls:
        return 'dialog'
    return 'unknown'


def match(ref_path, act_path, ignore=()):
    """按文本多重集配对；ignore 中的文本（ref 侧）不参与缺失判定。"""
    ignore = set(ignore or ())
    ref = [n for n in nodes(ref_path) if n['t'] not in ignore]
    act = nodes(act_path)
    from collections import defaultdict
    act_by_text = defaultdict(list)
    for a in act:
        act_by_text[a['t']].append(a)
    used = set()
    rows = []
    for r in ref:
        cand = act_by_text.get(r['t'], [])
        pick = None
        for c in cand:
            if id(c) not in used:
                pick = c
                break
        if pick is None:
            rows.append((r['t'], None, r['l'], r['t_'], r['r'], r['b'], None, None))
            continue
        used.add(id(pick))
        dl, dt, dr, db = pick['l'] - r['l'], pick['t_'] - r['t_'], pick['r'] - r['r'], pick['b'] - r['b']
        rows.append((r['t'], pick, r['l'], r['t_'], r['r'], r['b'], (dl, dt, dr, db), pick['id']))
    matched = [x for x in rows if x[1] is not None]
    missing = [x for x in rows if x[1] is None]
    maxd = 0
    bad = []
    for t, a, l, tp, r, b, d, aid in matched:
        m = max(abs(v) for v in d)
        maxd = max(maxd, m)
        if m > 2:
            bad.append((t, d, aid))
    extra = [a for a in act if id(a) not in used]
    ys = sorted(((x[2], x[3]) for x in matched), key=lambda p: p[1])
    diffs = [ys[i + 1][1] - ys[i][1] for i in range(len(ys) - 1)]
    diffs = [d for d in diffs if d > 0]
    med = statistics.median(diffs) if diffs else None
    return {'ref': len(ref), 'act': len(act), 'matched': matched, 'missing': missing,
            'maxd': maxd, 'bad': bad, 'extra': extra, 'step_median': med,
            'step_min': min(diffs) if diffs else None, 'step_max': max(diffs) if diffs else None}


def _argv_list():
    return sys.argv[1:]


def _take_ignore(argv):
    """--ignore-text a,b / 可重复；返回 (剩余参数, ignore 集合)"""
    out, ig = [], set()
    i = 0
    while i < len(argv):
        if argv[i] == '--ignore-text':
            if i + 1 >= len(argv):
                raise SystemExit('--ignore-text 需要参数（逗号分隔）')
            ig |= {x.strip() for x in argv[i + 1].split(',') if x.strip()}
            i += 2
            continue
        out.append(argv[i])
        i += 1
    return out, ig


def main():
    argv, ignore = _take_ignore(_argv_list())
    if len(argv) < 2:
        print('usage: compare_page.py <ref.xml> <act.xml> [--step] [--ignore-text a,b]')
        return 2
    ref_path, act_path = argv[0], argv[1]
    r = match(ref_path, act_path, ignore)
    print(f"REF={ref_path}")
    print(f"ACT={act_path}")
    if ignore:
        print(f"IGNORED={len(ignore)}（{'|'.join(sorted(ignore))}）")
    print(f"ref_text_nodes={r['ref']} act_text_nodes={r['act']} matched={len(r['matched'])} "
          f"missing={len(r['missing'])} extra={len(r['extra'])}")
    print(f"max_bounds_delta={r['maxd']}")
    if r['missing']:
        print("MISSING_TEXTS=" + " | ".join(x[0] for x in r['missing'][:12]))
    if r['bad']:
        print("OVER2PX=" + " | ".join(f"{t}({d[0]},{d[1]},{d[2]},{d[3]})" for t, d, _ in r['bad'][:12]))
    if r['extra']:
        print("EXTRA_TEXTS=" + " | ".join(a['t'] for a in r['extra'][:12]))
    if '--step' in argv and r['step_median'] is not None:
        print(f"step_y_median={r['step_median']} min={r['step_min']} max={r['step_max']}")
    print("VERDICT=" + ("PASS" if (not r['missing'] and r['maxd'] <= 2) else "FAIL"))
    return 0


if __name__ == '__main__':
    sys.exit(main())
