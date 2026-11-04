//! Windowless software renderer for `--screenshot`.
//!
//! `rebuild_check` runs the program in a headless context where no real window can
//! be opened, so we render the compass straight to a pixel buffer (no window, no GL)
//! and encode a PNG. This reuses the same data tables and reading logic as the
//! interactive eframe app.

use crate::dial::{self, BaguaMode, DialCanvas, DialState, ShowRings};
use crate::{
    borrow_branch, nayin_of, reading_for, HOUTIAN_GUA, JIAZI, MOUNTAINS, WUXING, XIANTIAN_GUA,
    YINYANG,
};

type Rgb = (u8, u8, u8);

struct Canvas {
    w: usize,
    h: usize,
    buf: Vec<u8>,
}

impl Canvas {
    fn new(w: usize, h: usize) -> Self {
        Self {
            w,
            h,
            buf: vec![0; w * h * 4],
        }
    }

    fn fill(&mut self, c: Rgb) {
        for p in self.buf.chunks_exact_mut(4) {
            p[0] = c.0;
            p[1] = c.1;
            p[2] = c.2;
            p[3] = 255;
        }
    }

    #[inline]
    fn blend(&mut self, x: i32, y: i32, c: Rgb, a: u8) {
        if x < 0 || y < 0 || x as usize >= self.w || y as usize >= self.h {
            return;
        }
        let i = (y as usize * self.w + x as usize) * 4;
        let a = a as u32;
        let na = 255 - a;
        let dst = &mut self.buf[i..i + 4];
        dst[0] = ((c.0 as u32 * a + dst[0] as u32 * na) / 255) as u8;
        dst[1] = ((c.1 as u32 * a + dst[1] as u32 * na) / 255) as u8;
        dst[2] = ((c.2 as u32 * a + dst[2] as u32 * na) / 255) as u8;
        dst[3] = 255;
    }

    fn set(&mut self, x: i32, y: i32, c: Rgb) {
        self.blend(x, y, c, 255);
    }

    fn line(&mut self, x0: i32, y0: i32, x1: i32, y1: i32, c: Rgb) {
        let (mut x0, mut y0) = (x0, y0);
        let dx = (x1 - x0).abs();
        let sx = if x0 < x1 { 1 } else { -1 };
        let dy = -(y1 - y0).abs();
        let sy = if y0 < y1 { 1 } else { -1 };
        let mut err = dx + dy;
        loop {
            self.set(x0, y0, c);
            if x0 == x1 && y0 == y1 {
                break;
            }
            let e2 = 2 * err;
            if e2 >= dy {
                err += dy;
                x0 += sx;
            }
            if e2 <= dx {
                err += dx;
                y0 += sy;
            }
        }
    }

    fn fill_circle(&mut self, cx: f32, cy: f32, r: f32, c: Rgb) {
        let (x0, x1) = ((cx - r - 1.0) as i32, (cx + r + 1.0) as i32);
        let (y0, y1) = ((cy - r - 1.0) as i32, (cy + r + 1.0) as i32);
        for y in y0..=y1 {
            for x in x0..=x1 {
                let dx = x as f32 - cx;
                let dy = y as f32 - cy;
                if dx * dx + dy * dy <= r * r {
                    self.set(x, y, c);
                }
            }
        }
    }

    fn circle_stroke(&mut self, cx: f32, cy: f32, r: f32, c: Rgb) {
        let (x0, x1) = ((cx - r - 1.0) as i32, (cx + r + 1.0) as i32);
        let (y0, y1) = ((cy - r - 1.0) as i32, (cy + r + 1.0) as i32);
        for y in y0..=y1 {
            for x in x0..=x1 {
                let dx = x as f32 - cx;
                let dy = y as f32 - cy;
                let d = (dx * dx + dy * dy).sqrt();
                let dd = (d - r).abs();
                if dd < 1.0 {
                    let a = (255.0 * (1.0 - dd)).clamp(0.0, 255.0) as u8;
                    self.blend(x, y, c, a);
                }
            }
        }
    }

    fn measure(&self, s: &str, size: f32, font: &fontdue::Font) -> f32 {
        let mut w = 0.0;
        for ch in s.chars() {
            let (m, _) = font.rasterize(ch, size);
            w += m.advance_width;
        }
        w
    }

    fn text(&mut self, x: f32, y: f32, s: &str, size: f32, c: Rgb, font: &fontdue::Font) {
        let mut pen = x;
        for ch in s.chars() {
            let (m, bmp) = font.rasterize(ch, size);
            let ox = (pen + m.xmin as f32) as i32;
            let oy = (y + m.ymin as f32) as i32;
            for j in 0..m.height {
                for i in 0..m.width {
                    let cov = bmp[j * m.width + i];
                    if cov > 0 {
                        self.blend(ox + i as i32, oy + j as i32, c, cov);
                    }
                }
            }
            pen += m.advance_width;
        }
    }

    fn text_centered(&mut self, cx: f32, y: f32, s: &str, size: f32, c: Rgb, font: &fontdue::Font) {
        let w = self.measure(s, size, font);
        self.text(cx - w / 2.0, y, s, size, c, font);
    }
}

fn load_font() -> Option<fontdue::Font> {
    let candidates = [
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/Deng.ttf",
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simsun.ttc",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    ];
    for p in candidates {
        if let Ok(bytes) = std::fs::read(p) {
            if let Ok(f) = fontdue::Font::from_bytes(bytes, fontdue::FontSettings::default()) {
                return Some(f);
            }
        }
    }
    None
}

fn rgb(c: eframe::egui::Color32) -> Rgb {
    (c.r(), c.g(), c.b())
}

/// Software backend for the shared dial renderer. Stroke widths are ignored by the
/// pixel canvas (1px anti-aliased), which is listed in the round-C diff document.
struct SoftCanvas<'a> {
    cv: &'a mut Canvas,
    font: Option<&'a fontdue::Font>,
}

impl DialCanvas for SoftCanvas<'_> {
    fn circle_filled(&mut self, c: eframe::egui::Pos2, r: f32, color: eframe::egui::Color32) {
        let col = rgb(color);
        self.cv.fill_circle(c.x, c.y, r, col);
    }

    fn circle_stroke(&mut self, c: eframe::egui::Pos2, r: f32, color: eframe::egui::Color32, _width: f32) {
        let col = rgb(color);
        self.cv.circle_stroke(c.x, c.y, r, col);
    }

    fn line(&mut self, a: eframe::egui::Pos2, b: eframe::egui::Pos2, color: eframe::egui::Color32, _width: f32) {
        let col = rgb(color);
        self.cv.line(a.x as i32, a.y as i32, b.x as i32, b.y as i32, col);
    }

    fn text_centered(&mut self, p: eframe::egui::Pos2, s: &str, size: f32, color: eframe::egui::Color32) {
        let Some(f) = self.font else { return };
        let col = rgb(color);
        let (ascent, descent) = f
            .horizontal_line_metrics(size)
            .map(|m| (m.ascent, m.descent))
            .unwrap_or((size * 0.8, -size * 0.2));
        let baseline = p.y + (ascent + descent) / 2.0;
        let w = self.cv.measure(s, size, f);
        self.cv.text(p.x - w / 2.0, baseline, s, size, col, f);
    }

    fn polygon(&mut self, points: &[eframe::egui::Pos2], color: eframe::egui::Color32) {
        let col = rgb(color);
        if points.len() != 3 {
            for pair in points.windows(2) {
                self.cv.line(pair[0].x as i32, pair[0].y as i32, pair[1].x as i32, pair[1].y as i32, col);
            }
            return;
        }
        let (a, b, c) = (points[0], points[1], points[2]);
        let sign = |p: eframe::egui::Pos2, q: eframe::egui::Pos2, r: eframe::egui::Pos2| {
            (p.x - r.x) * (q.y - r.y) - (q.x - r.x) * (p.y - r.y)
        };
        let minx = a.x.min(b.x).min(c.x).floor() as i32;
        let maxx = a.x.max(b.x).max(c.x).ceil() as i32;
        let miny = a.y.min(b.y).min(c.y).floor() as i32;
        let maxy = a.y.max(b.y).max(c.y).ceil() as i32;
        for y in miny..=maxy {
            for x in minx..=maxx {
                let p = eframe::egui::Pos2::new(x as f32 + 0.5, y as f32 + 0.5);
                let d1 = sign(p, a, b);
                let d2 = sign(p, b, c);
                let d3 = sign(p, c, a);
                let neg = d1 < 0.0 || d2 < 0.0 || d3 < 0.0;
                let pos = d1 > 0.0 || d2 > 0.0 || d3 > 0.0;
                if !(neg && pos) {
                    self.cv.set(x, y, col);
                }
            }
        }
    }
}

pub fn software_screenshot(path: &str) {
    let (w, h) = (1100usize, 780usize);
    let mut cv = Canvas::new(w, h);
    cv.fill((24, 20, 16));

    let font = load_font();
    let gold = (220, 190, 130);
    let dim = (150, 130, 100);
    let white = (235, 228, 215);
    let heading = 0.0f32;
    let rd = reading_for(heading);

    if let Some(f) = &font {
        cv.text(16.0, 12.0, "罗盘 · 风水（三维山水全景地图 桌面版）", 20.0, gold, f);
        // Left knowledge panel (same titles as the egui side panel).
        cv.text(16.0, 70.0, "风水知识库", 18.0, gold, f);
        for (i, t) in [
            "二十四山", "三盘", "百二十分金", "穿山七十二龙",
            "透地平分六十龙", "正兼向", "八卦", "五行",
        ]
        .iter()
        .enumerate()
        {
            cv.text(16.0, 108.0 + i as f32 * 26.0, t, 15.0, white, f);
        }
    }

    // Compass: the same shared renderer the real egui window uses (src/dial.rs).
    let (cx, cy, r) = dial::central_dial(w as f32, h as f32);
    {
        let rings = ShowRings::default();
        let st = DialState { heading, bagua_mode: BaguaMode::HouTian, rings: &rings };
        let mut sc = SoftCanvas { cv: &mut cv, font: font.as_ref() };
        dial::draw_dial(&mut sc, &st, cx, cy, r);
    }

    // Reading panel (right).
    if let Some(f) = &font {
        let x = 1100.0 - dial::RIGHT_W + 16.0;
        let mut y = 120.0;
        let lh = 34.0;
        cv.text(x, y, "方位读数", 22.0, gold, f);
        y += lh + 8.0;
        // BUG-001: 人盘 list starts 癸, 天盘 starts 子 (both offsetDegrees = 0).
        let hm = ((rd.heading / 15.0).floor() as usize + 1) % 24;
        let tm = ((rd.heading / 15.0).floor() as usize) % 24;
        let fj = crate::wangxiang(borrow_branch(rd.mountain), rd.fenjin_slot);
        let d72 = crate::wangxiang(borrow_branch(rd.mountain), rd.dragon72_slot);
        let d60 = JIAZI[rd.dragon60];

        let rows = [
            ("度数", format!("{:.2}°", rd.heading)),
            ("地盘正针", format!("{}山", MOUNTAINS[rd.mountain])),
            ("人盘中针", format!("{}山", MOUNTAINS[hm])),
            ("天盘缝针", format!("{}山", MOUNTAINS[tm])),
            ("五行", WUXING[rd.mountain].to_string()),
            ("阴阳", YINYANG[rd.mountain].to_string()),
            ("后天卦", HOUTIAN_GUA[rd.mountain].to_string()),
            ("先天卦", XIANTIAN_GUA[rd.mountain].to_string()),
            ("正/兼向", crate::rd_offset_label(&rd)),
            ("分金", format!("{}山 {} · {}", MOUNTAINS[rd.mountain], fj, nayin_of(fj))),
            ("七十二龙", format!("{}山 {} · {}", MOUNTAINS[rd.mountain], d72, nayin_of(d72))),
            ("六十龙", format!("{} · {}", d60, nayin_of(d60))),
            ("坐", format!("{}山", MOUNTAINS[rd.sitting])),
        ];
        for (k, v) in rows {
            cv.text(x, y, k, 15.0, dim, f);
            cv.text(x + 96.0, y, &v, 15.0, white, f);
            y += lh;
        }
        cv.text(x, y + 6.0, "（分金/龙文字仍为教材式命名，待轮 B2 换原版表）", 11.0, (120, 110, 95), f);
    }

    // Save PNG.
    if let Some(parent) = std::path::Path::new(path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let _ = image::save_buffer(
        path,
        &cv.buf,
        w as u32,
        h as u32,
        image::ColorType::Rgba8,
    );
}
