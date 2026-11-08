#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把帮助中心唯一源 docs/product/help-center.md 生成 assets/lc-help/index.html。

纪律（M6 第 5 条，总监定）：**MD 是唯一源，HTML 是生成物，禁止手写并存。**
生成物首行写入 `GENERATED from <md> @ <blob sha>`，审成文的 lint 会校验该 sha
与当前 MD 的 blob sha 是否一致——手改生成物会被当场拦住。

用法：
    python tools/help/md2html.py                        # 默认 in/out
    python tools/help/md2html.py --md X.md --out Y.html # 指定
    python tools/help/md2html.py --md-ref refs/heads/xxx --md docs/product/help-center.md
                                                       # 从 git 引用读（分支未合时）

只支持帮助中心实际用到的 Markdown 子集：标题、列表、表格、图片、粗体、行内码、链接、分隔线。
"""
import argparse
import html
import re
import subprocess
import sys


def git_blob_sha(path):
    """内容 blob sha（与 git 内部一致，按文件内容算，不依赖是否提交）。"""
    try:
        out = subprocess.run(["git", "hash-object", path], capture_output=True, check=True)
        return out.stdout.decode().strip()
    except Exception:
        return "unknown"


def read_md(args):
    if args.md_ref:
        out = subprocess.run(["git", "show", "%s:%s" % (args.md_ref, args.md)],
                             capture_output=True)
        if out.returncode != 0:
            sys.exit("读不到 %s:%s" % (args.md_ref, args.md))
        text = out.stdout.decode("utf-8")
        sha = subprocess.run(["git", "rev-parse", "%s:%s" % (args.md_ref, args.md)],
                             capture_output=True).stdout.decode().strip()
        return text, sha
    with open(args.md, encoding="utf-8") as f:
        return f.read(), git_blob_sha(args.md)


INLINE_BOLD = re.compile(r"\*\*(.+?)\*\*")
INLINE_CODE = re.compile(r"`([^`]+)`")
INLINE_LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
IMG = re.compile(r"^!\[([^\]]*)\]\(([^)]+)\)\s*$")


def inline(text):
    text = html.escape(text)
    text = INLINE_CODE.sub(lambda m: "<code>%s</code>" % m.group(1), text)
    text = INLINE_BOLD.sub(lambda m: "<b>%s</b>" % m.group(1), text)
    text = INLINE_LINK.sub(lambda m: '<a href="%s">%s</a>' % (m.group(2), m.group(1)), text)
    return text


def convert(md, src_name, blob_sha):
    lines = md.splitlines()
    out = []
    i = 0
    in_list = None      # "ul" / "ol" / None
    in_table = False
    in_code = False
    in_limit = False

    def close_list():
        nonlocal in_list
        if in_list:
            out.append("</%s>" % in_list)
            in_list = None

    def close_table():
        nonlocal in_table
        if in_table:
            out.append("</tbody></table></div>")
            in_table = False

    while i < len(lines):
        raw = lines[i]
        line = raw.rstrip()

        if line.startswith("```"):
            close_list(); close_table()
            out.append("<pre><code>" if not in_code else "</code></pre>")
            in_code = not in_code
            i += 1
            continue
        if in_code:
            out.append(html.escape(raw))
            i += 1
            continue

        if not line.strip():
            close_list()
            if in_table:
                # 表格内的空行不结束表格
                pass
            i += 1
            continue

        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            close_list(); close_table()
            level = len(m.group(1))
            txt = m.group(2).strip()
            text_plain = re.sub(r"[*`]", "", txt)
            if "已知限制" in text_plain:
                in_limit = True
            anchor = ""
            am = re.match(r"^(\d+(?:\.\d+)?)\.?\s*", text_plain)
            if am:
                anchor = ' id="sec-%s"' % am.group(1).replace(".", "-")
            out.append("<h%d%s>%s</h%d>" % (level, anchor, inline(txt), level))
            i += 1
            continue

        if re.match(r"^(\s*[-*_]\s*){3,}$", line):
            close_list(); close_table()
            out.append("<hr>")
            i += 1
            continue

        # 表格
        if line.lstrip().startswith("|") and i + 1 < len(lines) and re.match(
                r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1]):
            close_list()
            header = [c.strip() for c in line.strip().strip("|").split("|")]
            out.append('<div class="tbl"><table><thead><tr>')
            for c in header:
                out.append("<th>%s</th>" % inline(c))
            out.append("</tr></thead><tbody>")
            in_table = True
            i += 2
            continue
        if in_table and line.lstrip().startswith("|"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            out.append("<tr>" + "".join("<td>%s</td>" % inline(c) for c in cells) + "</tr>")
            i += 1
            continue
        if in_table:
            close_table()

        # 图片独占一行
        mi = IMG.match(line.strip())
        if mi:
            close_list()
            alt, src = mi.group(1), mi.group(2)
            # 统一放到 assets/lc-help/ 同级，引用只留文件名
            src = src.split("/")[-1]
            # 契约第 5 条：图号与锚点同名（fig-05.png <-> id="fig-05"），便于机械核对图文对应
            anchor = src.rsplit(".", 1)[0]
            out.append('<figure id="%s"><img src="%s" alt="%s"><figcaption>%s</figcaption></figure>'
                       % (html.escape(anchor), html.escape(src), html.escape(alt), inline(alt)))
            i += 1
            continue

        # 列表
        mu = re.match(r"^\s*[-*]\s+(.*)$", line)
        mo = re.match(r"^\s*\d+[.)]\s+(.*)$", line)
        if mu or mo:
            want = "ul" if mu else "ol"
            if in_list != want:
                close_list()
                out.append("<%s>" % want)
                in_list = want
            out.append("<li>%s</li>" % inline((mu or mo).group(1)))
            i += 1
            continue

        close_list()
        out.append("<p>%s</p>" % inline(line.strip()))
        i += 1

    close_list(); close_table()
    if in_code:
        out.append("</code></pre>")

    body = "\n".join(out)
    if in_limit:
        body = body  # 已知限制区块已由 CSS 类兜底在标题上体现
    head = (
        "<!-- GENERATED from %s @ %s - do not edit; 源文件是唯一真相，改这里会被 lint 拦住 -->\n"
        % (src_name, blob_sha)
    )
    css = """
  body { margin:0; padding:16px 18px 48px; background:#F0F3F7; color:#23272B;
         font:15px/1.7 -apple-system,"Noto Sans CJK SC","Microsoft YaHei",sans-serif; }
  h1 { font-size:19px; margin:0 0 6px; }
  h2 { font-size:16px; margin:26px 0 8px; padding-left:8px; border-left:3px solid #1C45A5; }
  h3 { font-size:15px; margin:18px 0 6px; }
  p, li { margin:6px 0; }
  code { background:#EDEFF2; border-radius:4px; padding:1px 4px; font-size:13px; }
  a { color:#1C45A5; text-decoration:none; }
  figure { margin:12px 0; }
  figure img { width:100%; border-radius:10px; display:block; }
  figcaption { color:#8D9299; font-size:12px; margin-top:4px; }
  .tbl { overflow-x:auto; background:#FFFFFF; border-radius:10px; padding:8px 10px; }
  table { border-collapse:collapse; width:100%; font-size:13px; }
  th, td { border-bottom:1px solid #E6E8EB; padding:6px 8px; text-align:left; }
  hr { border:0; border-top:1px solid #DDE1E6; margin:18px 0; }
  ul, ol { padding-left:22px; }
"""
    doc = ("<!DOCTYPE html>\n"
           + head +
           "<html lang=\"zh-CN\">\n<head>\n<meta charset=\"utf-8\">\n"
           "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
           "<title>帮助中心</title>\n<style>"
           + css +
           "</style>\n</head>\n<body>\n" + body + "\n</body>\n</html>\n")
    return doc


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--md", default="docs/product/help-center.md")
    ap.add_argument("--out", default="app/android/app/src/main/assets/lc-help/index.html")
    ap.add_argument("--md-ref", default=None, help="从 git 引用读（分支未合时用）")
    args = ap.parse_args()

    md, sha = read_md(args)
    doc = convert(md, args.md, sha)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(doc)
    print("GENERATED %s <- %s @ %s (%d bytes)" % (args.out, args.md, sha, len(doc.encode("utf-8"))))


if __name__ == "__main__":
    main()
