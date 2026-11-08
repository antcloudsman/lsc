/* 贴地平面四角的离线预期值（纯算术，不需要设备/浏览器）。
 * 复刻 map.html 里 render3D 的相机与矩阵：
 *   FOV 50°、D = (hCss*zMpp)/(2*tan(FOV/2))、eye 由 pitch/bearing 决定、lookAt、perspective、mul
 * 用于回答一个判据问题：pitch 60 时贴地四边形在屏幕上应被压成多扁（aspect = 高/宽）。
 * 用法：node tools/fidelity/ground-quad-expect.js
 * 出处：map.html L2045(FOV) / L3239(D) / L2972-3003(lookAt/perspective/mul) /
 *       L2351-2355(groundRadiusMeters) / L2480-2487(四角世界坐标)
 */
"use strict";
var FOV = 50 * Math.PI / 180;
var LAT = 39.9042, ZOOM = 12, CSSW = 364, CSSH = 763, DPR = 3.375;

function sub(a, b) { return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z }; }
function cross(a, b) { return { x: a.y * b.z - a.z * b.y, y: a.z * b.x - a.x * b.z, z: a.x * b.y - a.y * b.x }; }
function dot(a, b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
function norm(a) { var l = Math.sqrt(dot(a, a)) || 1; return { x: a.x / l, y: a.y / l, z: a.z / l }; }

function lookAt(eye, target, up) {
  var f = norm(sub(target, eye));
  var s = norm(cross(f, up));
  var u = cross(s, f);
  return [s.x, u.x, -f.x, 0, s.y, u.y, -f.y, 0, s.z, u.z, -f.z, 0,
    -dot(s, eye), -dot(u, eye), dot(f, eye), 1];
}
function perspective(fovy, aspect, near, far) {
  var f = 1 / Math.tan(fovy / 2);
  return [f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) / (near - far), -1, 0, 0, 2 * far * near / (near - far), 0];
}
function mul(a, b) {
  var o = new Array(16);
  for (var c = 0; c < 4; c++) for (var r = 0; r < 4; r++) {
    o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
  }
  return o;
}
function proj(vp, p) {
  var v = [
    vp[0] * p[0] + vp[4] * p[1] + vp[8] * p[2] + vp[12] * p[3],
    vp[1] * p[0] + vp[5] * p[1] + vp[9] * p[2] + vp[13] * p[3],
    vp[2] * p[0] + vp[6] * p[1] + vp[10] * p[2] + vp[14] * p[3],
    vp[3] * p[0] + vp[7] * p[1] + vp[11] * p[2] + vp[15] * p[3]
  ];
  if (!(v[3] > 1e-6)) return null;
  return { x: (v[0] / v[3] * 0.5 + 0.5) * CSSW, y: (0.5 - v[1] / v[3] * 0.5) * CSSH };
}
function len(a, b) { return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)); }

function quad(pitch, bearing) {
  var zMpp = 156543.03392 * Math.cos(LAT * Math.PI / 180) / Math.pow(2, ZOOM);
  var D = (CSSH * zMpp) / (2 * Math.tan(FOV / 2));
  var b = bearing * Math.PI / 180, p = pitch * Math.PI / 180;
  var fh = { x: Math.sin(b), y: 0, z: -Math.cos(b) };
  var sp = Math.sin(p), cp = Math.cos(p);
  var target = { x: 0, y: 0, z: 0 };
  var eye = { x: target.x - fh.x * D * sp, y: target.y + D * cp, z: target.z - fh.z * D * sp };
  var up = (pitch < 0.5) ? { x: Math.sin(b), y: 0, z: -Math.cos(b) } : { x: 0, y: 1, z: 0 };
  var view = lookAt(eye, target, up);
  var near = Math.max(1, D * 0.01), far = D * 6;
  var vp = mul(perspective(FOV, CSSW / CSSH, near, far), view);

  var r = Math.floor(CSSW * zMpp);                  /* groundRadiusMeters() */
  var y = 0.5;
  var w = [[-r, y, -r], [r, y, -r], [r, y, r], [-r, y, r]].map(function (p3) {
    return proj(vp, [p3[0], p3[1], p3[2], 1]);
  });
  var top = len(w[0], w[1]), right = len(w[1], w[2]);
  return {
    pitch: pitch, bearing: bearing,
    radiusMeters: r, sideMeters: 2 * r,
    topCss: +top.toFixed(1), rightCss: +right.toFixed(1),
    topDevicePx: +(top * DPR).toFixed(0),
    aspect: +(right / top).toFixed(3),
    cosPitch: +Math.cos(p).toFixed(3)
  };
}

var rows = [];
[30, 45, 60].forEach(function (p) { rows.push(quad(p, 0)); });
rows.push(quad(60, 90));
console.log("state: lat=" + LAT + " zoom=" + ZOOM + " css=" + CSSW + "x" + CSSH + " dpr=" + DPR);
console.log("pitch  bearing  quadSideCss  topCss  rightCss  topDevicePx  aspect  cos(pitch)");
rows.forEach(function (r) {
  console.log([r.pitch, r.bearing, (2 * r.radiusMeters), r.topCss, r.rightCss, r.topDevicePx, r.aspect, r.cosPitch].join("   \t"));
});
console.log("\n判据：aspect≈cos(pitch) ⇒ 真·地面四角投影；aspect=1.000 ⇒ 屏幕空间（billboard）。");


/* ---- 反解：要让盘面在屏幕上宽 273 CSS（=0.75×屏宽），四边形的地面半径该多大？ ----
 * 两种口径：A) 盘面画满纹理（当前实现）B) 盘面只占纹理 273/728（原版 c=0.75×屏宽） */
function quadWidthForRadius(pitch, r) {
  var zMpp = 156543.03392 * Math.cos(LAT * Math.PI / 180) / Math.pow(2, ZOOM);
  var D = (CSSH * zMpp) / (2 * Math.tan(FOV / 2));
  var p = pitch * Math.PI / 180, b = 0;
  var fh = { x: Math.sin(b), y: 0, z: -Math.cos(b) };
  var eye = { x: -fh.x * D * Math.sin(p), y: D * Math.cos(p), z: -fh.z * D * Math.sin(p) };
  var view = lookAt(eye, { x: 0, y: 0, z: 0 }, { x: 0, y: 1, z: 0 });
  var vp = mul(perspective(FOV, CSSW / CSSH, Math.max(1, D * 0.01), D * 6), view);
  var w = [[-r, 0.5, -r], [r, 0.5, -r], [r, 0.5, r], [-r, 0.5, r]].map(function (q) {
    return proj(vp, [q[0], q[1], q[2], 1]);
  });
  return len(w[0], w[1]);
}
function solveRadius(pitch, f) {
  var target = 273 / f, lo = 100, hi = 200000;
  for (var i = 0; i < 60; i++) {
    var mid = (lo + hi) / 2;
    if (quadWidthForRadius(pitch, mid) < target) lo = mid; else hi = mid;
  }
  return (lo + hi) / 2;
}
var zMpp0 = 156543.03392 * Math.cos(LAT * Math.PI / 180) / Math.pow(2, ZOOM);
console.log("\n--- 反解半径（pitch 60，目标：盘面屏幕宽 273 CSS = 921 device px）---");
console.log("原公式 r = floor(screen.width*mpp) = " + Math.floor(CSSW * zMpp0) + " m");
console.log("A) 盘面画满纹理 f=1.000   -> r = " + Math.round(solveRadius(60, 1.0)) + " m");
console.log("B) 盘面占 273/728 f=0.375 -> r = " + Math.round(solveRadius(60, 0.375)) + " m");
/* ---- 盘面屏宽预期（口径：盘面占四边形 0.375，逆向 aa2024c） ----
 * up 向量按 render3D 的规则（pitch<0.5 用 bearing 方向，否则 +Y），否则 pitch 0 会退化。 */
function dialWidthDevice(pitch) {
  var zMpp = 156543.03392 * Math.cos(LAT * Math.PI / 180) / Math.pow(2, ZOOM);
  var D = (CSSH * zMpp) / (2 * Math.tan(FOV / 2));
  var p = pitch * Math.PI / 180, b = 0;
  var fh = { x: Math.sin(b), y: 0, z: -Math.cos(b) };
  var eye = { x: -fh.x * D * Math.sin(p), y: D * Math.cos(p), z: -fh.z * D * Math.sin(p) };
  var up = (pitch < 0.5) ? { x: Math.sin(b), y: 0, z: -Math.cos(b) } : { x: 0, y: 1, z: 0 };
  var view = lookAt(eye, { x: 0, y: 0, z: 0 }, up);
  var vp = mul(perspective(FOV, CSSW / CSSH, Math.max(1, D * 0.01), D * 6), view);
  var r = Math.floor(CSSW * zMpp), y = 0.5;
  var w = [[-r, y, -r], [r, y, -r], [r, y, r], [-r, y, r]].map(function (q) {
    return proj(vp, [q[0], q[1], q[2], 1]);
  });
  return (len(w[0], w[1]) * 0.375 * DPR);
}
console.log("\n--- 盘面屏宽预期（0.375×四边形，device px）---");
[0, 1, 5, 30, 45, 60].forEach(function (p) {
  console.log("pitch " + p + " -> " + dialWidthDevice(p).toFixed(0) + " px   (目标 921 @pitch0)");
});