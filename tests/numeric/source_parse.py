"""被测源码的读取与表达式翻译（只读，不修改业务代码）。

- Rust：从 `app/src/main.rs` 提取 `let x = ...;` 表达式，翻译成可求值的 Python 表达式；
- Java：从 `CompassData.java` / `MainActivity.java` 提取数组表与 `plate(...)` 调用；
- 表达式翻译失败会直接报错，避免「镜像与源码脱节」时静默通过。
"""
from __future__ import annotations

import math
import re

import oracle as O

_FLOOR_CALL = re.compile(r"\(((?:[^()]|\([^()]*\))*)\)\.floor\(\)")
_MIN_CALL = re.compile(r"\(((?:[^()]|\([^()]*\))*)\)\.min\((\d+)\)")


def read_text(path) -> str:
    return path.read_text(encoding="utf-8")


def rust_expr_to_py(expr: str, where: str = "", consts: dict | None = None) -> str:
    """把 main.rs 中的纯算术表达式翻译为 Python 表达式。

    支持：rd.heading、normalize_deg、`as usize`/`as f32`、`(expr).floor()`、
    `(expr).min(n)`，以及生成块里的 `const NAME: f32 = ...` 常量替换。
    """
    s = expr.strip()
    s = s.replace("rd.heading", "h")
    s = s.replace("normalize_deg", "normalize")
    s = re.sub(r"\s+as\s+(?:usize|f32|f64)", "", s)
    for name, value in (consts or {}).items():
        s = re.sub(rf"\b{re.escape(name)}\b", repr(value), s)
    for _ in range(8):
        new = _FLOOR_CALL.sub(r"math.floor(\1)", s)
        if new == s:
            break
        s = new
    for _ in range(8):
        new = _MIN_CALL.sub(r"min(\1, \2)", s)
        if new == s:
            break
        s = new
    if "floor()" in s or ".min(" in s or " as usize" in s or " as f32" in s:
        raise AssertionError(f"无法翻译 Rust 表达式{where}: {expr!r} -> {s!r}")
    return s


def rust_consts(source: str) -> dict:
    """生成块中的数值常量：`const NAME: f32 = 15.0;`。"""
    return {name: float(value) for name, value in
            re.findall(r"const\s+([A-Z0-9_]+):\s*f32\s*=\s*([-\d.]+);", source)}


def rust_let_expr(source: str, name: str, consts: dict | None = None) -> str:
    m = re.search(rf"let\s+{re.escape(name)}\s*=\s*(.+?);", source, re.S)
    if not m:
        raise AssertionError(f"main.rs 中找不到 `let {name} = ...;`")
    return rust_expr_to_py(m.group(1), where=f" (let {name})",
                           consts=rust_consts(source) if consts is None else consts)


def rust_ring_label_expr(source: str, ring_key: str) -> str:
    """取某个环绘制分支里的标签角度表达式（`if self.rings.<ring_key> { ... let deg = ... }`）。"""
    m = re.search(rf"if self\.rings\.{re.escape(ring_key)}\s*\{{(.{{0,1600}})", source, re.S)
    if not m:
        raise AssertionError(f"main.rs 中找不到 `if self.rings.{ring_key} {{` 分支")
    deg = re.search(r"let deg = (.+?);", m.group(1), re.S)
    if not deg:
        raise AssertionError(f"rings.{ring_key} 分支中找不到 `let deg = ...;`")
    return rust_expr_to_py(deg.group(1), where=f" (rings.{ring_key} deg)",
                           consts=rust_consts(source))


def rust_array(source: str, name: str, length: int) -> list:
    m = re.search(rf"const\s+{re.escape(name)}:\s*\[&str;\s*{length}\]\s*=\s*\[(.*?)\];",
                  source, re.S)
    if not m:
        raise AssertionError(f"main.rs 中找不到 const {name}: [&str; {length}]")
    return re.findall(r'"([^"]*)"', m.group(1))


def java_array(source: str, name: str) -> list:
    m = re.search(rf"String\[\]\s+{re.escape(name)}\s*=\s*\{{(.*?)\}};", source, re.S)
    if not m:
        raise AssertionError(f"CompassData.java 中找不到 String[] {name}")
    return re.findall(r'"([^"]*)"', m.group(1))


def java_plate_exprs(source: str) -> list:
    """提取 `CompassData.MOUNTAINS[plate(<expr>)]` 中的 <expr>（MainActivity 三盘读数）。"""
    exprs = re.findall(r"MOUNTAINS\[plate\(([^()]*)\)\]", source)
    if not exprs:
        raise AssertionError("MainActivity.java 中找不到 MOUNTAINS[plate(...)] 调用")
    out = []
    for e in exprs:
        s = e.replace("r.heading", "h")
        s = re.sub(r"(\d)f\b", r"\1", s)  # Java float 后缀 7.5f -> 7.5
        if "heading" in s or "f " in s:
            raise AssertionError(f"无法翻译 Java plate 表达式: {e!r} -> {s!r}")
        out.append(s)
    return out


def java_plate(h: float, expr: str) -> int:
    """按 MainActivity.plate(float) 的定义求值：floor(norm(x)/15) % 24。"""
    return int(math.floor(O.normalize(eval(expr, {"__builtins__": {}}, {"h": h})) / 15.0)) % 24


def rust_reading(source: str, heading: float) -> dict:
    """按 main.rs `reading_for` 的表达式逐句求值，得到真实桌面读数。"""
    exprs = {
        "m": rust_let_expr(source, "m"),
        "offset": rust_let_expr(source, "offset"),
        "from_start": rust_let_expr(source, "from_start"),
        "fenjin_slot": rust_let_expr(source, "fenjin_slot"),
        "dragon72_slot": rust_let_expr(source, "dragon72_slot"),
        "dragon60": rust_let_expr(source, "dragon60"),
        "sitting": rust_let_expr(source, "sitting"),
    }
    h = O.normalize(heading)
    env = {"h": h, "math": math, "min": min, "normalize": O.normalize}
    m = eval(exprs["m"], {"__builtins__": {}}, env)
    env.update({"m": m, "center": m * 15.0})
    offset = eval(exprs["offset"], {"__builtins__": {}}, env)
    from_start = eval(exprs["from_start"], {"__builtins__": {}}, env)
    env.update({"offset": offset, "from_start": from_start})
    return {
        "h": h, "m": m, "off": offset,
        "fs": eval(exprs["fenjin_slot"], {"__builtins__": {}}, env),
        "d72": eval(exprs["dragon72_slot"], {"__builtins__": {}}, env),
        "d60": eval(exprs["dragon60"], {"__builtins__": {}}, env),
        "sit": eval(exprs["sitting"], {"__builtins__": {}}, env),
        "exprs": exprs,
    }


def rust_plate(source: str, heading: float, which: str) -> int:
    """按 main.rs `reading_panel` 内 hm/tm 表达式求值（桌面三盘读数）。

    `rd.heading` 已在 reading_for 中归一化，故这里传入 normalize(heading)。
    """
    expr = rust_let_expr(source, which)
    return int(eval(expr, {"__builtins__": {}},
                    {"h": O.normalize(heading), "math": math, "min": min,
                     "normalize": O.normalize})) % 24
