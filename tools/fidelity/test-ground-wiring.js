/* 贴地接线断言（M8 第二阶段）：产品路径能进 canvas 档 + diag 生命周期 + 失败注入可构造。
 * 用法：node tools/fidelity/test-ground-wiring.js
 *       node tools/fidelity/test-ground-wiring.js --selftest   跑反证：每条断言故意改坏必须变红
 *
 * 为什么要有这个文件：11-08 那次「贴地按钮只切 pitch、从不切 groundRenderer」,
 * 屏幕上看不出错（原生盘面还在），只有读码/读 diag 才分得出来。静态断言把它钉住。
 * 断言对象是 map.html 源码；状态机部分直接把函数抽出来在 Node 里执行（带 stub），
 * 不是「看着像对」而是真的跑一遍状态迁移。
 */
"use strict";
var fs = require("fs");
var path = require("path");

var MAP = path.join(__dirname, "..", "..",
  "app", "android", "app", "src", "main", "assets", "map.html");
var SRC = fs.readFileSync(MAP, "utf8");

var fails = 0;
function ok(name, cond, detail) {
  if (!cond) fails++;
  console.log((cond ? "[PASS] " : "[FAIL] ") + name + (detail ? "  " + detail : ""));
}

/* 取 function 源码：从 "function NAME(" 起配对花括号，跳过字符串与注释 */
function fnSource(src, name) {
  var start = src.indexOf("function " + name + "(");
  if (start < 0) return "";
  var i = src.indexOf("{", start + ("function " + name).length);
  if (i < 0) return "";
  var depth = 0, inS = null, inLine = false, inBlock = false;
  for (; i < src.length; i++) {
    var c = src[i], n = src[i + 1];
    if (inLine) { if (c === "\n") inLine = false; continue; }
    if (inBlock) { if (c === "*" && n === "/") { inBlock = false; i++; } continue; }
    if (inS) {
      if (c === "\\") { i++; continue; }
      if (c === inS) inS = null;
      continue;
    }
    if (c === "/" && n === "/") { inLine = true; i++; continue; }
    if (c === "/" && n === "*") { inBlock = true; i++; continue; }
    if (c === '"' || c === "'") { inS = c; continue; }
    if (c === "{") depth++;
    else if (c === "}") { depth--; if (depth === 0) return src.slice(start, i + 1); }
  }
  return "";
}
function stripComments(s) {
  return s.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
}

/* ---------- A. 静态断言 ---------- */
function staticChecks(src) {
  var setGround = stripComments(fnSource(src, "setGround"));
  var setState = stripComments(fnSource(src, "groundStateSet"));
  var ls = [];

  ls.push(["setGround 里把 groundRenderer 切到 canvas（产品路径）",
    /groundRenderer\s*=\s*on\s*\?\s*'canvas'\s*:\s*'overlay'/.test(setGround),
    setGround ? "" : "setGround 函数没找到"]);

  ls.push(["setGround 进贴地时把 diag 置 pending（重试生命周期起点）",
    /groundStateSet\(\s*false\s*,\s*on\s*\?\s*'pending'/.test(setGround)]);

  ls.push(["diag 生命周期三态齐全：pending / fail:<原因> / drawn",
    /'pending'/.test(src) && /'drawn'/.test(src) && /'fail:glinit'/.test(src) &&
    /'fail:upload'/.test(src) && /'fail:rings'/.test(src)]);

  ls.push(["groundStateSet 不许把 diag 写成空串（粘滞的前提）",
    setState.length > 0 && !/groundDiag\s*=\s*''/.test(setState) && !/groundDiag\s*=\s*""/.test(setState)]);

  ls.push(["groundStateSet 不许无条件写入形参（空串会擦掉真实原因）",
    setState.length > 0 && !/groundDiag\s*=\s*diag\s*;/.test(setState)]);

  ls.push(["groundStateSet 向原生报 groundstate 事件（带 ok/diag/reason）",
    /type:\s*'groundstate'/.test(setState) && /ok:\s*ok/.test(setState) &&
    /diag:\s*diag/.test(setState) && /reason:\s*reason/.test(setState)]);

  ls.push(["groundStateSet 有去重键（逐帧调用不会刷桥）",
    /groundStateKey/.test(setState)]);

  ls.push(["失败注入 __forceGroundFail 已导出且限三种原因",
    /__forceGroundFail\s*:/.test(src) && /'glinit'\s*&&\s*k\s*!==\s*'upload'/.test(src.replace(/\s+/g, " "))]);

  ls.push(["注入失败写在真实失败分支之前（注入覆盖真实判定）",
    src.indexOf("groundFailInject === 'glinit'") > 0 &&
    src.indexOf("groundFailInject === 'glinit'") < src.indexOf("if (!groundInitGL())")]);

  ls.push(["getState() 暴露 groundRenderer / groundCanvasOk / groundDiag",
    /groundCanvasOk:\s*groundCanvasOk/.test(src) && /groundDiag:\s*groundDiag/.test(src) &&
    /groundRenderer:\s*groundRenderer/.test(src)]);

  ls.push(["getGroundDebug() 带上 canvasOk 与 failInject（判据可读）",
    /canvasOk:\s*groundCanvasOk/.test(src) && /failInject:\s*groundFailInject/.test(src)]);

  // 回退条件用页面 ground，不用原生镜像：页面侧只认 groundMode
  ls.push(["回退依据是页面状态（groundMode），不读原生 groundOn",
    !/groundOn/.test(setState) && !/groundOn/.test(stripComments(fnSource(src, "drawGroundCompass")))]);

  return ls;
}

/* ---------- B. 状态机行为（把函数抽出来真跑） ---------- */
function behaviourChecks(src) {
  var setStateSrc = fnSource(src, "groundStateSet");
  var ls = [];
  if (!setStateSrc) return [["groundStateSet 可抽取执行", false, "函数没找到"]];

  var harness = [
    "var groundDiag='idle', groundCanvasOk=false, groundStateKey='', groundRenderer='canvas', groundMode=true;",
    "var events=[];",
    "function emit(o){ events.push(o); }",
    setStateSrc,
    "return { set: groundStateSet, events: function(){return events;}, diag: function(){return groundDiag;},",
    "         okv: function(){return groundCanvasOk;} };"
  ].join("\n");
  var h = new Function(harness)();

  h.set(false, "pending");
  ls.push(["重试起点 pending ⇒ canvasOk=false", h.diag() === "pending" && h.okv() === false, h.diag()]);

  h.set(false, "fail:glinit", "injected");
  ls.push(["失败 ⇒ diag=fail:glinit、canvasOk=false",
    h.diag() === "fail:glinit" && h.okv() === false, h.diag()]);

  h.set(false, "");
  ls.push(["粘滞：空串不许覆盖失败原因", h.diag() === "fail:glinit", h.diag()]);

  h.set(true, "drawn");
  ls.push(["成功必须能清掉失败：drawn ⇒ canvasOk=true",
    h.diag() === "drawn" && h.okv() === true, h.diag()]);

  var before = h.events().length;
  h.set(true, "drawn");
  ls.push(["同状态重复调用不重复上报（去重）", h.events().length === before,
    "events " + before + " -> " + h.events().length]);

  ls.push(["上报事件带 type/ok/diag/reason",
    h.events().length > 0 && h.events()[0].type === "groundstate" &&
    "ok" in h.events()[0] && "diag" in h.events()[0] && "reason" in h.events()[0]]);

  return ls;
}

/* ---------- 反证：故意改坏，断言必须变红 ---------- */
var MUTATIONS = [
  ["删掉产品切档那行（回到 11-08 的死代码）",
    function (s) { return s.replace(/groundRenderer\s*=\s*on\s*\?\s*'canvas'\s*:\s*'overlay';/, ""); },
    "setGround 里把 groundRenderer 切到 canvas（产品路径）"],
  ["把 pending 那行删掉（失败后无重试起点）",
    function (s) { return s.replace(/groundStateSet\(false, on \? 'pending' : 'skip:ground=off'\);/, ""); },
    "setGround 进贴地时把 diag 置 pending"],
  ["让 diag 可被空串清空（静默失败复现）",
    function (s) { return s.replace("if (diag) groundDiag = String(diag);", "groundDiag = diag;"); },
    /* 目标是行为断言：静态断言只查字面空串，抓不住「无条件写入形参」这种漏洞——
     * 这正是第一次跑反证时暴露出来的（静态绿、行为红）。 */
    "粘滞：空串不许覆盖失败原因"],
  ["去掉注入分支（失败态不可构造）",
    function (s) { return s.replace("if (groundFailInject === 'glinit') { return groundStateSet(false, 'fail:glinit', 'injected'); }", ""); },
    "注入失败写在真实失败分支之前"],
  ["注入块挪到真实判定之后（注入失效）",
    function (s) {
      var inj = "if (groundFailInject === 'glinit') { return groundStateSet(false, 'fail:glinit', 'injected'); }";
      var real = "if (!groundInitGL()) { return groundStateSet(false, 'fail:glinit'); }";
      return s.replace(inj, real).replace(real + "\n", real + "\n" + inj + "\n");
    },
    "注入失败写在真实失败分支之前"],
  ["getState 丢掉三个新字段（原生读不到）",
    function (s) { return s.replace("groundCanvasOk: groundCanvasOk, groundDiag: groundDiag,", ""); },
    "getState() 暴露 groundRenderer / groundCanvasOk / groundDiag"]
];

if (process.argv.indexOf("--selftest") >= 0) {
  console.log("=== 反证：每条断言故意改坏一次，必须变红 ===");
  var bad = 0;
  MUTATIONS.forEach(function (m) {
    var mutated = m[1](SRC);
    if (mutated === SRC) { console.log("[FAIL] 变异没生效（找不到目标）: " + m[0]); bad++; return; }
    var ls = staticChecks(mutated).concat(behaviourChecks(mutated));
    /* 断言名可能带括号补充，用前缀匹配，避免改名就把反证变成空跑 */
  var target = ls.filter(function (x) { return x[0].indexOf(m[2]) === 0; })[0];
    var red = target && target[1] === false;
    if (!red) bad++;
    console.log((red ? "[PASS] " : "[FAIL] ") + "变异后「" + m[2] + "」变红  ← " + m[0]);
  });
  console.log("\nSELFTEST: " + (bad === 0 ? "PASS" : "FAIL") + " bad=" + bad);
  process.exit(bad === 0 ? 0 : 1);
}

console.log("=== A. 静态断言（map.html 源码）===");
staticChecks(SRC).forEach(function (x) { ok(x[0], x[1], x[2]); });
console.log("=== B. diag 状态机行为（抽函数在 Node 里跑）===");
behaviourChecks(SRC).forEach(function (x) { ok(x[0], x[1], x[2]); });
console.log("\nRESULT: " + (fails === 0 ? "PASS" : "FAIL") + " fails=" + fails);
process.exit(fails === 0 ? 0 : 1);
