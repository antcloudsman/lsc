#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""离线瓦片缓存复现用的本地假瓦片服务（网络不通时用）。

- 把仓库根目录当静态站（map.html 及相邻资源照常加载）；
- 任何 `/tile/<layer>/<z>/<x>/<y>.png` 都返回同一张 256×256 实心 PNG（带 CORS *），
  这样瓦片请求既快又确定，便于数 put/hit/miss；
- 不做任何缓存/状态，避免干扰被测的 IndexedDB 层。

用法：python tools/map/fake-tiles.py [port]   （默认 8811，根目录=仓库根）
"""
import http.server
import os
import struct
import sys
import zlib

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8811
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))


def make_png(size=256, rgb=(70, 90, 60)):
    """生成一张 size×size 的实心 PNG（纯标准库，无需 Pillow）。"""
    raw = b'\x00' + bytes(rgb) * size
    raw = raw * size
    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)
    ihdr = struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr)
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


TILE = make_png()


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def do_GET(self):
        if self.path.startswith('/tile/'):
            self.send_response(200)
            self.send_header('Content-Type', 'image/png')
            self.send_header('Content-Length', str(len(TILE)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(TILE)
            return
        super().do_GET()

    def log_message(self, *a):
        pass


if __name__ == '__main__':
    print('fake tiles on http://127.0.0.1:%d  root=%s' % (PORT, ROOT))
    http.server.ThreadingHTTPServer(('127.0.0.1', PORT), Handler).serve_forever()
