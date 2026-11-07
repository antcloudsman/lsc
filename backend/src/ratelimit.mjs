/**
 * In-memory sliding-window rate limiter (LC v2 §6.4).
 *
 * Single-process only: the backend runs as one container, so a process-local
 * window is sufficient. Restarting the container resets the windows, which is
 * acceptable for a 127.0.0.1-only service.
 */
export class SlidingWindowLimiter {
  constructor({ now = () => Date.now() } = {}) {
    this.buckets = new Map();
    this.now = now;
    this.hitsSinceSweep = 0;
  }

  /**
   * Record one hit for `key` if under `limit` within `windowMs`.
   * Returns { allowed, retryAfter } with retryAfter in whole seconds.
   */
  hit(key, limit, windowMs) {
    const at = this.now();
    const cutoff = at - windowMs;
    let hits = this.buckets.get(key);
    if (!hits) {
      hits = [];
      this.buckets.set(key, hits);
    }
    while (hits.length > 0 && hits[0] <= cutoff) hits.shift();

    if (hits.length >= limit) {
      const retryAfterMs = Math.max(0, hits[0] + windowMs - at);
      this.sweep(at);
      return { allowed: false, retryAfter: Math.ceil(retryAfterMs / 1000) };
    }

    hits.push(at);
    this.sweep(at);
    return { allowed: true, retryAfter: 0 };
  }

  sweep(at = this.now()) {
    this.hitsSinceSweep += 1;
    if (this.hitsSinceSweep < 512) return;
    this.hitsSinceSweep = 0;
    const maxWindow = 60 * 60 * 1000;
    for (const [key, hits] of this.buckets) {
      if (hits.length === 0 || hits[hits.length - 1] <= at - maxWindow) this.buckets.delete(key);
    }
  }

  reset() {
    this.buckets.clear();
    this.hitsSinceSweep = 0;
  }
}
