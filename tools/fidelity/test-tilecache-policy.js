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

console.log("\nRESULT: " + (fails === 0 ? "PASS" : "FAIL") + " fails=" + fails);
process.exit(fails === 0 ? 0 : 1);
