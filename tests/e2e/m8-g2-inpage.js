/* M8 G2（终版）：在 1456 画布空间比对，不在 549 裁切空间比
 *
 * 为什么：渲染器 groundRedraw 是 translate(453.5, 453.5) 后 drawImage(img, 0,0, 549,549)。
 * off=(1456-549)/2=453.5 是半像素；若在 549 上裁切比对，裁剪原点只能取 453 或 454，
 * 与渲染位置差 0.5px —— 细线稿上足以让大量描边像素超 ΔE2000>3（表现为 maxChannelDiff≈247）。
 * 所以基线也在 1456 画布上用同一 translate 复刻，然后与真实画布全图比。
 *
 * 输出 window.__g2（字符串）。注入要求：整串不含单引号/反引号。
 */
(function () {
  function log(o) { window.__g2 = "G2RESULT " + JSON.stringify(o); }
  try {
    var cv = window.groundCanvas;
    if (!cv || typeof cv.width !== "number") { log({ err: "no groundCanvas" }); return; }
    var st = getState(), dbg = __LC3D.getGroundDebug();
    var W = cv.width, H = cv.height;                       /* 实测 1456 */
    var dial = 549;                                        /* round(1456*0.37725) */
    var off = (W - dial) / 2;                              /* 453.5 */
    var cx = off + dial / 2, cy = off + dial / 2;          /* 728 */
    var R = dial / 2;                                      /* 274.5 */
    var u = dial / 922;
    var dialType = (typeof __LC3D.dialType === "number") ? __LC3D.dialType : 0;
    var dialAlpha = (typeof __LC3D.dialAlpha === "number") ? __LC3D.dialAlpha : 1;

    /* 真实画布 */
    var src = document.createElement("canvas");
    src.width = W; src.height = H;
    var sctx = src.getContext("2d");
    sctx.drawImage(cv, 0, 0);
    var act = sctx.getImageData(0, 0, W, H).data;

    var img = new Image();
    img.onload = function () {
      try {
        /* 基线：同尺寸画布 + 同一 translate + 同一 drawImage */
        var bcv = document.createElement("canvas");
        bcv.width = W; bcv.height = H;
        var bctx = bcv.getContext("2d");
        bctx.translate(off, off);
        bctx.drawImage(img, 0, 0, dial, dial);
        var bd = bctx.getImageData(0, 0, W, H).data;

        /* 合成基线（A2）：位图 + 树内同一份 drawOutlineRings，同一个 translate */
        var ccv = document.createElement("canvas");
        ccv.width = W; ccv.height = H;
        var cctx = ccv.getContext("2d");
        cctx.translate(off, off);
        cctx.globalAlpha = 1;
        cctx.drawImage(img, 0, 0, dial, dial);
        var compositeOk = false;
        try {
          if (typeof drawOutlineRings === "function") {
            drawOutlineRings(cctx, dial / 2, dial / 2, R, u);   /* 在 translate 后的局部坐标画 */
            compositeOk = true;
          }
        } catch (e) { compositeOk = false; }
        var cd = cctx.getImageData(0, 0, W, H).data;

        /* ΔE2000 */
        function s2l(c) { c /= 255; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
        function lab(r, g, b) {
          var Rr = s2l(r), Gg = s2l(g), Bb = s2l(b);
          var X = (Rr * 0.4124 + Gg * 0.3576 + Bb * 0.1805) / 0.95047;
          var Y = (Rr * 0.2126 + Gg * 0.7152 + Bb * 0.0722);
          var Z = (Rr * 0.0193 + Gg * 0.1192 + Bb * 0.9505) / 1.08883;
          function f(t) { return t > 0.008856 ? Math.pow(t, 1 / 3) : (7.787 * t + 16 / 116); }
          var fx = f(X), fy = f(Y), fz = f(Z);
          return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
        }
        function de(l1, l2) {
          var L1 = l1[0], a1 = l1[1], b1 = l1[2], L2 = l2[0], a2 = l2[1], b2 = l2[2];
          var C1 = Math.sqrt(a1 * a1 + b1 * b1), C2 = Math.sqrt(a2 * a2 + b2 * b2), Cb = (C1 + C2) / 2;
          var G = 0.5 * (1 - Math.sqrt(Math.pow(Cb, 7) / (Math.pow(Cb, 7) + Math.pow(25, 7))));
          var ap1 = (1 + G) * a1, ap2 = (1 + G) * a2;
          var cp1 = Math.sqrt(ap1 * ap1 + b1 * b1), cp2 = Math.sqrt(ap2 * ap2 + b2 * b2);
          function hp(bb, ap) { if (bb === 0 && ap === 0) return 0; var h = Math.atan2(bb, ap) * 180 / Math.PI; return h >= 0 ? h : h + 360; }
          var hp1 = hp(b1, ap1), hp2 = hp(b2, ap2);
          var dL = L2 - L1, dC = cp2 - cp1, dhp;
          if (cp1 * cp2 === 0) dhp = 0; else { dhp = hp2 - hp1; if (dhp > 180) dhp -= 360; else if (dhp < -180) dhp += 360; }
          var dH = 2 * Math.sqrt(cp1 * cp2) * Math.sin(dhp * Math.PI / 360);
          var Lb = (L1 + L2) / 2, Cbp = (cp1 + cp2) / 2, hbp;
          if (cp1 * cp2 === 0) hbp = hp1 + hp2;
          else { var dd = Math.abs(hp1 - hp2); if (dd <= 180) hbp = (hp1 + hp2) / 2; else if (hp1 + hp2 < 360) hbp = (hp1 + hp2 + 360) / 2; else hbp = (hp1 + hp2 - 360) / 2; }
          var T = 1 - 0.17 * Math.cos((hbp - 30) * Math.PI / 180) + 0.24 * Math.cos(2 * hbp * Math.PI / 180)
                  + 0.32 * Math.cos((3 * hbp + 6) * Math.PI / 180) - 0.20 * Math.cos((4 * hbp - 63) * Math.PI / 180);
          var dTh = 30 * Math.exp(-Math.pow((hbp - 275) / 25, 2));
          var Rc = 2 * Math.sqrt(Math.pow(Cbp, 7) / (Math.pow(Cbp, 7) + Math.pow(25, 7)));
          var Sl = 1 + (0.015 * Math.pow(Lb - 50, 2)) / Math.sqrt(20 + Math.pow(Lb - 50, 2));
          var Sc = 1 + 0.045 * Cbp, Sh = 1 + 0.015 * Cbp * T;
          var Rt = -Math.sin(2 * dTh * Math.PI / 180) * Rc;
          var t1 = dL / Sl, t2 = dC / Sc, t3 = dH / Sh;
          return Math.sqrt(t1 * t1 + t2 * t2 + t3 * t3 + Rt * t2 * t3);
        }

        var rings = [1.0, 0.91, 0.82, 0.73];
        function A(x, y) { return bd[(y * W + x) * 4 + 3]; }
        function core(x, y) {
          return A(x, y) > 250 && A(x - 1, y) > 250 && A(x + 1, y) > 250 && A(x, y - 1) > 250 && A(x, y + 1) > 250;
        }
        function rad(x, y) { var dx = (x + 0.5 - cx) / R, dy = (y + 0.5 - cy) / R; return Math.sqrt(dx * dx + dy * dy); }
        function ringDist(x, y) {
          var rr = rad(x, y), m = 1e9;
          for (var k = 0; k < 4; k++) { var d = Math.abs(rr - rings[k]) * R; if (d < m) m = d; }
          return m;
        }
        function tickBand(rr) { return rr >= 0.88 && rr <= 0.985; }

        var D = 0, E = 0;
        var zA1 = { n: 0, bad: 0, sum: 0 }, zA2 = { n: 0, bad: 0, sum: 0 }, zC = { n: 0 };
        var maskA1 = new Uint8Array(W * H);   /* 供变换搜索复用 */

        for (var y = 0; y < H; y++) {
          for (var x = 0; x < W; x++) {
            var i4 = (y * W + x) * 4;
            if (bd[i4 + 3] > 250) D++;
            if (!core(x, y)) continue;
            E++;
            var rr = rad(x, y);
            if (rr < 0.60) { zC.n++; continue; }
            if (act[i4 + 3] < 250) continue;
            var isA2 = (ringDist(x, y) <= 4) || tickBand(rr);
            var bb = isA2 ? [cd[i4], cd[i4 + 1], cd[i4 + 2]] : [bd[i4], bd[i4 + 1], bd[i4 + 2]];
            var d = de(lab(act[i4], act[i4 + 1], act[i4 + 2]), lab(bb[0], bb[1], bb[2]));
            var z = isA2 ? zA2 : zA1;
            z.n++; z.sum += d; if (d > 3) z.bad++;
            if (!isA2) maskA1[y * W + x] = 1;
          }
        }

        /* 变换搜索（只在 A1，步长 2 抽样；翻转围绕盘心） */
        function rate(fx, fy, dx, dy) {
          var n = 0, bad = 0;
          for (var yy = 0; yy < H; yy += 2) {
            for (var xx = 0; xx < W; xx += 2) {
              if (!maskA1[yy * W + xx]) continue;
              var sx, sy;
              if (fx) sx = Math.round(2 * cx - 1 - xx); else sx = xx;
              if (fy) sy = Math.round(2 * cy - 1 - yy); else sy = yy;
              sx += dx; sy += dy;
              if (sx < 0 || sy < 0 || sx >= W || sy >= H) continue;
              var i4b = (sy * W + sx) * 4;
              if (act[i4b + 3] < 250) continue;
              n++;
              var i4a = (yy * W + xx) * 4;
              if (de(lab(act[i4b], act[i4b + 1], act[i4b + 2]), lab(bd[i4a], bd[i4a + 1], bd[i4a + 2])) > 3) bad++;
            }
          }
          return { n: n, pct: n ? +(100 * bad / n).toFixed(2) : null };
        }
        var hyps = [];
        var idr = rate(false, false, 0, 0);
        hyps.push({ fx: false, fy: false, dx: 0, dy: 0, pct: idr.pct, n: idr.n });
        [[true, false], [false, true], [true, true]].forEach(function (f) {
          var r = rate(f[0], f[1], 0, 0); hyps.push({ fx: f[0], fy: f[1], dx: 0, dy: 0, pct: r.pct, n: r.n });
        });
        for (var ox = -3; ox <= 3; ox++) {
          for (var oy = -3; oy <= 3; oy++) {
            if (ox === 0 && oy === 0) continue;
            if (Math.abs(ox) + Math.abs(oy) > 3) continue;
            var r2 = rate(false, false, ox, oy);
            hyps.push({ fx: false, fy: false, dx: ox, dy: oy, pct: r2.pct, n: r2.n });
          }
        }
        hyps.sort(function (a, b) { return (a.pct === null ? 999 : a.pct) - (b.pct === null ? 999 : b.pct); });

        log({
          canvasW: W, canvasH: H, dial: dial, off: off, cx: cx, cy: cy,
          imgW: img.naturalWidth, imgH: img.naturalHeight,
          dialType: dialType, dialAlpha: dialAlpha,
          smooth: { baseEnabled: bctx.imageSmoothingEnabled, baseQuality: bctx.imageSmoothingQuality,
                    compEnabled: cctx.imageSmoothingEnabled, compQuality: cctx.imageSmoothingQuality },
          compositeOk: compositeOk, metric: "CIEDE2000>3",
          D: D, E: E,
          A1: { n: zA1.n, pct: zA1.n ? +(100 * zA1.bad / zA1.n).toFixed(2) : null, meanDE: zA1.n ? +(zA1.sum / zA1.n).toFixed(2) : null },
          A2: { n: zA2.n, pct: zA2.n ? +(100 * zA2.bad / zA2.n).toFixed(2) : null, meanDE: zA2.n ? +(zA2.sum / zA2.n).toFixed(2) : null },
          C: { n: zC.n },
          bestHyp: hyps[0], hypTop3: hyps.slice(0, 3),
          stateOk: st.groundCanvasOk, stateDiag: st.groundDiag, dbgOk: dbg.canvasOk, dbgDiag: dbg.diag
        });
      } catch (e) { log({ err: "compare:" + e.message }); }
    };
    img.onerror = function () { log({ err: "baseline load failed: compass/dial_full_" + dialType + ".webp" }); };
    img.src = "compass/dial_full_" + dialType + ".webp";
  } catch (e) { log({ err: "outer:" + e.message }); }
})();
