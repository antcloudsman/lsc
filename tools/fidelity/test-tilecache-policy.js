/* 离线瓦片缓存策略单测（Node，不依赖浏览器）。
 * 被测文件：app/android/app/src/main/assets/map/tilecache-policy.js（运行时也用它，同一份代码）
 * 用法：node tools/fidelity/test-tilecache-policy.js
 */
"use strict";
var path = require("path");
var P = require(path.join(__dirname,
  "..", "..", "app", "android", "app", "src", "main", "assets", "map", "tilecache-policy.js"));

var fails = 0;
function ok(name, cond, detail) {
  if (!cond) fails++;
  console.log((cond ? "[PASS] " : "[FAIL] ") + name + (detail ? "  " + detail : ""));
}

/* case 1：容量淘汰 —— 5 块各 300B，上限 1000B ⇒ 删最旧 2 块，剩 900B */
var e1 = [
  { key: "a", size: 300, ts: 5 }, { key: "b", size: 300, ts: 4 },
  { key: "c", size: 300, ts: 3 }, { key: "d", size: 300, ts: 2 },
  { key: "e", size: 300, ts: 1 }
];
var r1 = P.decide(e1, { capBytes: 1000, ttlMs: 0, nowMs: 100 });
ok("cap 1000/1500B -> 删 2 块", r1.evict.length === 2, "evict=" + JSON.stringify(r1.evict));
ok("cap 淘汰的是最旧两块 e,d", r1.evict.join(",") === "e,d", "evict=" + r1.evict.join(","));
ok("淘汰后 900B <= cap", r1.bytesAfter === 900, "bytesAfter=" + r1.bytesAfter);

/* case 2：TTL —— now=5000、ttl=1000 ⇒ ts<=3999 过期（1000/3000 过期，4500/4900 保留） */
var e2 = [
  { key: "old1", size: 100, ts: 1000 }, { key: "old2", size: 100, ts: 3000 },
  { key: "new1", size: 100, ts: 4500 }, { key: "new2", size: 100, ts: 4900 }
];
var r2 = P.decide(e2, { capBytes: 10000, ttlMs: 1000, nowMs: 5000 });
ok("TTL 删 2 块", r2.expiredCount === 2, "expired=" + r2.expiredCount);
ok("TTL 未误删新块", r2.evict.indexOf("new1") < 0 && r2.evict.indexOf("new2") < 0, "evict=" + JSON.stringify(r2.evict));
ok("TTL 删后 200B", r2.bytesAfter === 200, "bytesAfter=" + r2.bytesAfter);

/* case 3：TTL + 容量同时命中 —— 过期先删，再按容量删 */
var e3 = [
  { key: "x1", size: 400, ts: 10 }, { key: "x2", size: 400, ts: 20 },
  { key: "x3", size: 400, ts: 4800 }, { key: "x4", size: 400, ts: 4900 }
];
var r3 = P.decide(e3, { capBytes: 500, ttlMs: 4000, nowMs: 5000 });
ok("TTL 删 2（x1,x2）", r3.expiredCount === 2, "expired=" + r3.expiredCount);
ok("容量再删 1（剩 400B>? cap500 不删）", r3.capacityCount === 1, "capacity=" + r3.capacityCount);
ok("最终 400B <= cap", r3.bytesAfter === 400, "bytesAfter=" + r3.bytesAfter);

/* case 4：边界 —— 空表 / cap 0 / ttl 0（不启用过期） */
var r4 = P.decide([], { capBytes: 1000, ttlMs: 1000, nowMs: 1 });
ok("空表不删", r4.evict.length === 0);
var r5 = P.decide([{ key: "k", size: 10, ts: 1 }], { capBytes: 0, ttlMs: 0, nowMs: 100 });
ok("cap=0 ⇒ 全删", r5.evict.length === 1 && r5.bytesAfter === 0);
var r6 = P.decide([{ key: "k", size: 10, ts: 1 }], { capBytes: 100, ttlMs: 0, nowMs: 999999 });
ok("ttl=0 ⇒ 不过期", r6.evict.length === 0);

/* case 5：命中率口径 —— (0,0) 必须为 null，不能是 0 */
ok("hitRate(0,0) = null", P.hitRate(0, 0) === null);
ok("hitRate(30,10) = 0.75", Math.abs(P.hitRate(30, 10) - 0.75) < 1e-12, String(P.hitRate(30, 10)));

/* case 6：降级文案三态（总监 11-09 裁定：stale 不许与 miss 互斥）
 * 旧写法 staleOnly=(miss===0 && stale>0) 在真实视野里几乎永不成立（实测 stale=200/miss=20
 * 却只出缺块文案），等于死代码。这里把「并存」写进单测，避免再靠上机才发现。 */
var DAY = 86400000;
var b1 = P.bannerText({ hit: 240, miss: 20, stale: 200 }, { anyEnabled: true, offline: true, ttlMs: 7 * DAY });
ok("miss>0 且 stale>0 ⇒ 显示", b1.show === true);
ok("miss>0 且 stale>0 ⇒ 必须含「可能已过期」", b1.text.indexOf("可能已过期") >= 0, b1.text);
ok("miss>0 且 stale>0 ⇒ 追加「其中 200 块」", b1.text.indexOf("其中 200 块数据可能已过期") >= 0, b1.text);
ok("miss>0 且 stale>0 ⇒ 保留「已命中 240 块 / 缺 20 块」",
  b1.text.indexOf("已命中 240 块 / 缺 20 块") >= 0, b1.text);

var b2 = P.bannerText({ hit: 30, miss: 0, stale: 30 }, { anyEnabled: true, offline: true, ttlMs: 7 * DAY });
ok("miss===0 且 stale>0 ⇒ 单出过期句",
  b2.show === true && b2.text === "已缓存，但数据可能已过期（缓存超 7 天）", b2.text);

/* 回归：stale===0 时文本必须与改动前逐字一致 */
var b3 = P.bannerText({ hit: 24, miss: 28, stale: 0 }, { anyEnabled: true, offline: true, ttlMs: 7 * DAY });
ok("stale===0 回归：文本与旧版逐字一致",
  b3.text === "当前区域未缓存，联网后可查看（已命中 24 块 / 缺 28 块）", b3.text);

var b4 = P.bannerText({ hit: 0, miss: 5, stale: 0 }, { anyEnabled: false, offline: false, ttlMs: 7 * DAY });
ok("未启用源 ⇒ 仍提示并追加「离线缓存未启用」",
  b4.text === "当前区域未缓存，联网后可查看（已命中 0 块 / 缺 5 块；离线缓存未启用）", b4.text);

var b5 = P.bannerText({ hit: 10, miss: 0, stale: 0 }, { anyEnabled: true, offline: false, ttlMs: 7 * DAY });
ok("无缺块且无过期 ⇒ 不显示", b5.show === false && b5.text === "");

var b6 = P.bannerText({ hit: 5, miss: 0, stale: 5 }, { anyEnabled: true, offline: false, ttlMs: DAY });
ok("ttl 注入 1 天 ⇒ 过期句天数跟着变", b6.text === "已缓存，但数据可能已过期（缓存超 1 天）", b6.text);

/* case 7：在线且已启用且有命中、但无缺块 ⇒ 不显示（避免把「正在联网加载」误报成降级） */
var b7 = P.bannerText({ hit: 3, miss: 0, stale: 0 }, { anyEnabled: true, offline: false, ttlMs: 7 * DAY });
ok("在线·已启用·无缺块 ⇒ 不显示", b7.show === false);

console.log("\nRESULT: " + (fails === 0 ? "PASS" : "FAIL") + " fails=" + fails);
process.exit(fails === 0 ? 0 : 1);
