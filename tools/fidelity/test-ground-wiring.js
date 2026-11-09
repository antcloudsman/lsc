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

  /* G5⑤（总监 11-09 裁决）：地图态贴地失败没有原生盘面可回退，必须有可见提示，
   * 且复用现有 offlineBar 而不是新造浮层。 */
  var banner = stripComments(fnSource(src, "tileOfflineBanner"));
  ls.push(["贴地失败有可见提示分支（地图态不静默）",
    /贴地盘面未能绘制/.test(banner) && /groundCanvasOk/.test(banner) && /\/\^fail:/.test(banner)]);
  /* 只判失败分支本身：分支里不许 createElement（那就是另造浮层了），必须写已有 el 的 textContent */
  var gb = banner.match(/if \(groundMode && !groundCanvasOk[\s\S]*?\n  \}/);
  ls.push(["提示复用 offlineBar（分支内不新造浮层）",
    !!gb && !/createElement/.test(gb[0]) && /el\.textContent/.test(gb[0]) && /el\.style\.bottom/.test(gb[0])]);
  ls.push(["状态变化时刷新提示条（否则失败不出现在屏幕上）",
    /tileOfflineBanner\(\)/.test(stripComments(fnSource(src, "groundStateSet")))]);
  /* G2 锁基线（审核验 11-09）：判据必须知道画布实际渲染的是哪套盘面、什么透明度 */
  /* getGroundDebug 是对象字面量里的匿名函数，fnSource 抓不到；改在全文里按对象字段形态匹配 */
  var gd = stripComments(src);
  ls.push(["getGroundDebug 暴露 dialType / dialAlpha（G2 锁基线）",
    /dialType:\s*\(typeof compassDialType/.test(gd) && /dialAlpha:\s*\(typeof compassDialAlpha/.test(gd)]);
  ls.push(["__LC3D 用 getter 暴露 dialType / dialAlpha（只读、实时）",
    /Object\.defineProperties\(window\.__LC3D[\s\S]*?dialType:[\s\S]*?get:/.test(src) &&
    /Object\.defineProperties\(window\.__LC3D[\s\S]*?dialAlpha:[\s\S]*?get:/.test(src)]);
  ls.push(["dial 字段没有 setter（只读，判据改不动它）",
    !/Object\.defineProperties\(window\.__LC3D[\s\S]{0,600}?set:/.test(src)]);
  /* 「两个读数源打架」的加固（11-09）：页面只有一个来源，但两次独立求值会夹住一帧状态迁移。
   * 给出单调 seq 后，单次求值把两边一起读回、seq 相同即不该有差异。 */
  ls.push(["getState / getGroundDebug 都暴露同一个 groundSeq（单来源可自证）",
    /groundSeq:\s*groundSeq/.test(stripComments(src)) && /seq:\s*groundSeq/.test(stripComments(src))]);
  ls.push(["状态变化才递增 seq（不是每次调用都加）",
    /if \(key !== groundStateKey\) \{[\s\S]{0,220}?groundSeq\+\+/.test(src)]);
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
    "var groundSeq=0, groundChangedAt=0;",
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

/* ---------- C. 盘面数据加载失败的可观测性（真跑加载器，不是读字面量） ---------- */
/* 为什么要有这一段：`loading:rings` 与 `fail:rings` 在屏幕上都不画盘面、都回退原生盘面，
 * 差别只在「原因说不说得出来」。只做静态断言证明不了「加载真失败时会不会说谎」，
 * 所以把 compassDataLoad 抽出来，用桩 fetch 真跑三种情形。 */
function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

function loaderSandbox(src, search, inlineRings, fetchImpl) {
  var loadSrc = fnSource(src, "compassDataLoad");
  if (!loadSrc) return null;
  var body = [
    'var window = { location: { search: search } };',
    'if (extra) window.__LC_COMPASS_RINGS = extra;',
    'var COMPASS_RINGS = null, compassRingsFailed = false, groundDiag = "idle", calls = [];',
    'var fetch = fetchImpl;',
    'var console = { log: function () { } };',
    'function groundStateSet(ok, diag, reason) { calls.push([ok, diag, reason]); if (diag) groundDiag = diag; return diag; }',
    loadSrc,
    'return { load: compassDataLoad, snap: function () {',
    '  return { failed: compassRingsFailed, diag: groundDiag, calls: calls.slice(), rings: COMPASS_RINGS }; } };'
  ].join("\n");
  return (new Function("search", "extra", "fetchImpl", body))(search, inlineRings, fetchImpl);
}

async function loaderChecks(src) {
  var ls = [];
  var rejecting = function () { return Promise.reject(new Error("no-such-file")); };

  /* C1 加载彻底失败（两条路都没拿到）：必须变成 fail:rings，不许停在 loading:rings */
  var sb1 = loaderSandbox(src, "", null, rejecting);
  if (!sb1) return [["compassDataLoad 可抽取执行", false, "函数没找到"]];
  var cb1 = 0;
  sb1.load(function () { cb1++; });
  await sleep(30);
  var s1 = sb1.snap();
  ls.push(["加载彻底失败 ⇒ diag=fail:rings（不是 loading:rings）", s1.diag === "fail:rings", s1.diag]);
  ls.push(["失败原因可读 ⇒ reason=loader-missing",
    s1.calls.length > 0 && s1.calls[s1.calls.length - 1][2] === "loader-missing",
    JSON.stringify(s1.calls)]);
  ls.push(["失败时回调被触发（旧写法永不触发）", cb1 === 1, "cb=" + cb1]);
  ls.push(["失败标记 compassRingsFailed=true（draw 直接进 fail 分支）", s1.failed === true]);

  /* C2 ?nofrings=1：立即失败、且一次 fetch 都不发（复现台专用开关） */
  var fetched = 0;
  var counting = function () { fetched++; return Promise.reject(new Error("should-not-run")); };
  var sb2 = loaderSandbox(src, "?nofrings=1", null, counting);
  sb2.load(function () { });
  await sleep(10);
  var s2 = sb2.snap();
  ls.push(["?nofrings=1 ⇒ 直接 fail:rings", s2.diag === "fail:rings", s2.diag]);
  ls.push(["?nofrings=1 ⇒ 不发起任何 fetch", fetched === 0, "fetch=" + fetched]);

  /* C3 正常路径（inline 脚本已注入盘面数据）⇒ 不受开关影响，diag 不动 */
  var sb3 = loaderSandbox(src, "", { rings: [{ id: "TYPE_1" }] }, rejecting);
  var got3 = null;
  sb3.load(function (r) { got3 = r; });
  await sleep(10);
  var s3 = sb3.snap();
  ls.push(["正常路径 ⇒ 取到盘面数据", s3.rings !== null && got3 !== null]);
  ls.push(["正常路径 ⇒ diag 不被改写（开关没污染）", s3.diag === "idle", s3.diag]);
  ls.push(["正常路径 ⇒ 不置失败标记", s3.failed === false]);

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
  ["删掉地图态贴地失败提示（盘面凭空消失）",
    function (s) { return s.replace(/  if \(groundMode && !groundCanvasOk && \/\^fail:\/\.test\(groundDiag \|\| ''\)\) \{[\s\S]*?\n  \}\n/, ""); },
    "贴地失败有可见提示分支"],
  ["状态变化不再刷提示条（失败不上屏）",
    function (s) { return s.replace("    try { tileOfflineBanner(); } catch (e) { }\n", ""); },
    "状态变化时刷新提示条"],  ["seq 不再递增（跨次读取无法判新旧）",
    function (s) { return s.replace("    groundSeq++;\n", ""); },
    "状态变化才递增 seq"],  ["dial 字段不暴露（G2 锁不住基线）",
    function (s) { return s.replace(/\s*dialType: \(typeof compassDialType === 'number'\) \? compassDialType : 0,\n/, "\n"); },
    "getGroundDebug 暴露 dialType / dialAlpha"],  ["getState 丢掉三个新字段（原生读不到）",
    function (s) { return s.replace("groundCanvasOk: groundCanvasOk, groundDiag: groundDiag,", ""); },
    "getState() 暴露 groundRenderer / groundCanvasOk / groundDiag"],
  ["终端失败不报 fail:rings（退回「永远 loading」）",
    function (s) { return s.replace("groundStateSet(false, 'fail:rings', 'loader-missing');\n      if (typeof cb", "if (typeof cb"); },
    "加载彻底失败 ⇒ diag=fail:rings"],
  ["?nofrings=1 开关失效（复现台造不出加载失败）",
    function (s) { return s.replace("return /(?:^|[?&])nofrings=1(?:&|$)/.test(", "return /(?:^|[?&])nevermatch=1(?:&|$)/.test("); },
    "?nofrings=1 ⇒ 不发起任何 fetch"]
];

async function runSelftest() {
  console.log("=== 反证：每条断言故意改坏一次，必须变红 ===");
  var bad = 0;
  for (var i = 0; i < MUTATIONS.length; i++) {
    var m = MUTATIONS[i];
    var mutated = m[1](SRC);
    if (mutated === SRC) { console.log("[FAIL] 变异没生效（找不到目标）: " + m[0]); bad++; continue; }
    var ls = staticChecks(mutated).concat(behaviourChecks(mutated)).concat(await loaderChecks(mutated));
    var target = ls.filter(function (x) { return x[0].indexOf(m[2]) === 0; })[0];
    var red = target && target[1] === false;
    if (!red) bad++;
    console.log((red ? "[PASS] " : "[FAIL] ") + "变异后「" + m[2] + "」变红  ← " + m[0]);
  }
  console.log("\nSELFTEST: " + (bad === 0 ? "PASS" : "FAIL") + " bad=" + bad);
  process.exit(bad === 0 ? 0 : 1);
}

async function runAll() {
  console.log("=== A. 静态断言（map.html 源码）===");
  staticChecks(SRC).forEach(function (x) { ok(x[0], x[1], x[2]); });
  console.log("=== B. diag 状态机行为（抽函数在 Node 里跑）===");
  behaviourChecks(SRC).forEach(function (x) { ok(x[0], x[1], x[2]); });
  console.log("=== C. 盘面数据加载失败的可观测性（桩 fetch 真跑）===");
  (await loaderChecks(SRC)).forEach(function (x) { ok(x[0], x[1], x[2]); });
  console.log("\nRESULT: " + (fails === 0 ? "PASS" : "FAIL") + " fails=" + fails);
  process.exit(fails === 0 ? 0 : 1);
}

if (process.argv.indexOf("--selftest") >= 0) runSelftest();
else runAll();
