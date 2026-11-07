import sys, re
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

ref = nodes(sys.argv[1])
act = nodes(sys.argv[2])
# 按文本多重集配对（同文本出现多次时按顺序一一配）
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

print(f"REF={sys.argv[1]}")
print(f"ACT={sys.argv[2]}")
print(f"ref_text_nodes={len(ref)} act_text_nodes={len(act)} matched={len(matched)} missing={len(missing)} extra={len(extra)}")
print(f"max_bounds_delta={maxd}")
if missing:
    print("MISSING_TEXTS=" + " | ".join(x[0] for x in missing[:12]))
if bad:
    print("OVER2PX=" + " | ".join(f"{t}({d[0]},{d[1]},{d[2]},{d[3]})" for t, d, _ in bad[:12]))
if extra:
    print("EXTRA_TEXTS=" + " | ".join(a['t'] for a in extra[:12]))
if '--step' in sys.argv:
    ys = sorted(((x[2], x[3]) for x in matched), key=lambda p: p[1])
    diffs = [ys[i + 1][1] - ys[i][1] for i in range(len(ys) - 1)]
    diffs = [d for d in diffs if d > 0]
    if diffs:
        import statistics
        print(f"step_y_median={statistics.median(diffs)} min={min(diffs)} max={max(diffs)}")
print("VERDICT=" + ("PASS" if (not missing and maxd <= 2) else "FAIL"))
