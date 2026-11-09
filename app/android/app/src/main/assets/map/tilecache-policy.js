/* 离线瓦片缓存 · 淘汰策略（纯函数，无 DOM / 无 IndexedDB 依赖）
 * 目的：把「容量上限 / 过期 / LRU」三条策略独立出来，既能被 map.html 直接调用，
 * 也能在 Node 下用 tools/fidelity/test-tilecache-policy.js 单测——策略错不该靠上机才发现。
 * 载入方式：<script src="map/tilecache-policy.js"></script>（浏览器）；require()（Node）。
 */
(function (root, factory) {
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.LCTileCachePolicy = api;
})(typeof window !== 'undefined' ? window : globalThis, function () {
  'use strict';

  function sizeOf(e) { return Math.max(0, Number(e && e.size) || 0); }
  function tsOf(e) { return Number(e && e.ts) || 0; }

  /* decide(entries, opts) -> { evict, expiredCount, capacityCount, bytesAfter, evictBytes }
   * entries: [{ key, size, ts }]（ts = 写入/最后访问时间，毫秒）
   * opts:    { capBytes, ttlMs, nowMs }
   * 规则：先按 TTL 删过期，再按容量从「最旧」删到 ≤ cap（最旧 = LRU）。
   * 只算「该删谁」，真正 delete 由调用方做（策略可单测就靠这一层分离）。
   */
  function decide(entries, opts) {
    var cap = Math.max(0, Number(opts && opts.capBytes) || 0);
    var ttl = Math.max(0, Number(opts && opts.ttlMs) || 0);
    var now = Number(opts && opts.nowMs) || 0;

    var list = (entries || []).slice().sort(function (a, b) { return tsOf(a) - tsOf(b); });
    var expired = [], keep = [], i;
    for (i = 0; i < list.length; i++) {
      if (ttl > 0 && now - tsOf(list[i]) > ttl) expired.push(list[i]);
      else keep.push(list[i]);
    }

    var evictBytes = 0;
    for (i = 0; i < expired.length; i++) evictBytes += sizeOf(expired[i]);

    var total = 0;
    for (i = 0; i < keep.length; i++) total += sizeOf(keep[i]);

    var capVictims = [];
    while (total > cap && keep.length) {
      var v = keep.shift();               /* 排序后最旧的在前面 */
      capVictims.push(v);
      total -= sizeOf(v);
      evictBytes += sizeOf(v);
    }

    return {
      evict: expired.concat(capVictims).map(function (e) { return e.key; }),
      expiredCount: expired.length,
      capacityCount: capVictims.length,
      bytesAfter: total,
      evictBytes: evictBytes
    };
  }

  /* 命中率：hit/(hit+miss)；两者都 0 时返回 null —— 不返回 0，
   * 否则「根本没发请求」会和「全不命中」在报表上长得一样。 */
  function hitRate(hit, miss) {
    var h = Number(hit) || 0, m = Number(miss) || 0;
    if (h + m === 0) return null;
    return h / (h + m);
  }

  /* bannerText(stats, opts) -> { show, text }
   * stats: { hit, miss, stale }（hit / stale 是实例计，miss 是唯一键计；文案只并列，不改写口径）
   * opts:  { anyEnabled, offline, ttlMs }
   * 规则（总监 11-09 定：stale 不许与 miss 互斥）：
   *   miss>0 且该显示      → 缺块文案；stale>0 时在同一对括号内追加「其中 Z 块数据可能已过期」
   *   miss===0 且 stale>0  → 单出过期文案（不带未缓存措辞）
   *   其余                 → 不显示
   * 「已命中 X 块 / 缺 Y 块」必须原样保留：验收要拿文本和 hit/miss 计数器交叉核对，
   * 改成自然语言描述就没法对账了。
   */
  function bannerText(stats, opts) {
    var hit = Math.max(0, Number(stats && stats.hit) || 0);
    var miss = Math.max(0, Number(stats && stats.miss) || 0);
    var stale = Math.max(0, Number(stats && stats.stale) || 0);
    var anyEnabled = !!(opts && opts.anyEnabled);
    var offline = !!(opts && opts.offline);
    var ttlMs = Number(opts && opts.ttlMs) || 0;
    var staleDays = Math.max(1, Math.round(ttlMs / 86400000) || 7);

    /* 未启用缓存时每个视野都有缺块，必须提示；启用后只在离线或已命中过时才提示，
     * 免得在线浏览时把「正在联网加载」误报成降级。 */
    var showMiss = miss > 0 && (offline || !anyEnabled || hit > 0);
    if (showMiss) {
      return {
        show: true,
        text: '当前区域未缓存，联网后可查看' +
          '（已命中 ' + hit + ' 块 / 缺 ' + miss + ' 块' +
          (anyEnabled ? '' : '；离线缓存未启用') +
          (stale > 0 ? '；其中 ' + stale + ' 块数据可能已过期' : '') + '）'
      };
    }
    if (stale > 0) {
      return { show: true, text: '已缓存，但数据可能已过期（缓存超 ' + staleDays + ' 天）' };
    }
    return { show: false, text: '' };
  }

  return { decide: decide, hitRate: hitRate, bannerText: bannerText };
});
