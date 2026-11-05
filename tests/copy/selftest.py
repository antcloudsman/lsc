#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""copy_gate 自查：改门禁后跑一遍，确认「该红能红、该绿能绿」。

    python tests/copy/selftest.py

只测判定逻辑（正则 / 注释剔除 / 路径豁免），不依赖仓库内容，也不改任何文件。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import copy_gate as g  # noqa: E402


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
        sys.stderr.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
    except Exception:
        pass

    # 1. 审图号：命中原文、命中全角括号变体、不命中相邻号
    audit = "GS" + "(2026)" + "1219"
    assert g.AUDIT_NO_RE.search(audit + "号")
    assert g.AUDIT_NO_RE.search("GS（2026）1219"), "fullwidth variant"
    assert not g.AUDIT_NO_RE.search("GS(2026)1220")

    # 2. 私有瓦片域：URL 形态也能抓
    assert g.TILE_RE.search("https://mapcube.21at" + "cloud.com.cn/x")
    assert g.TILE_RE.search("https://mapoverlay." + "xinzhi" + ".space/overlay/a.pbf")

    # 3. 注释剔除：块注释/行尾注释被吃；URL 的 :// 与 #frag 不被误吃
    code = (
        "/**\n"
        " * 原版 21atcloud.com 私有瓦片\n"
        " * 21atcloud 注释续行\n"
        " */\n"
        'String u = "https://xinzhi.space/a#frag"; // 会员\n'
        "// 会员整行\n"
    )
    slash = g.strip_comments(code, "slash").split("\n")
    assert "21atcloud" not in slash[1], slash[1]
    assert "21atcloud" not in slash[2], slash[2]
    assert "xinzhi.space" in slash[4], slash[4]
    assert "会员" not in slash[4], slash[4]
    assert "会员" not in slash[5], slash[5]

    xml = '<!-- 会员 注释 -->\n<string name="a">会员</string>\n'
    xml_lines = g.strip_comments(xml, "xml").split("\n")
    assert "会员" not in xml_lines[0] and "会员" in xml_lines[1], xml_lines

    hashy = "# 会员\nkey=value # 会员\nurl=https://a.b/c#frag\n"
    hash_lines = g.strip_comments(hashy, "hash").split("\n")
    assert "会员" not in hash_lines[0] and "会员" not in hash_lines[1], hash_lines
    assert "c#frag" in hash_lines[2], hash_lines

    # 4. 豁免边界：文档/取证/测试豁免，发行面代码不豁免
    assert g.is_exempt("docs/product/PRD-v0.1.md")
    assert g.is_exempt("app/MECHANICS.md")
    assert g.is_exempt("tests/at/run-final.ps1")
    assert not g.is_exempt("app/android/app/src/main/java/com/luopan/fengshui/A.java")

    # 5. 门禁源码自身不含完整红线字面量
    own = Path(g.__file__).read_text(encoding="utf-8")
    assert audit not in own, "copy_gate.py 里出现完整审图号字面量，会被自己的全仓扫描判红"
    assert "21at" + "cloud.com" not in own

    files = g.tracked_files()
    scanned = [f for f in files if not g.is_exempt(f) and not g.is_binary(f)]
    print(f"[PASS] selftest: 判定逻辑 ok（tracked={len(files)}，红线扫描面={len(scanned)}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
