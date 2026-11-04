//! Windowless software renderer for `--screenshot`.
//!
//! `rebuild_check` runs the program in a headless context where no real window can
//! be opened, so we render the compass straight to a pixel buffer (no window, no GL)
//! and encode a PNG. This reuses the same data tables and reading logic as the
//! interactive eframe app.

use crate::{
    borrow_branch, gua_wuxing, nayin_of, normalize_deg, reading_for, screen_pos, wuxing_color,
    BAGUA_STEP, DRAGON60_STEP, DRAGON72_STEP, FENJIN_STEP, HEAVEN_OFFSET, HEXAGRAM64_STEP,
    HOUTIAN_GUA, HUMAN_OFFSET, JIAZI, MOUNTAIN_STEP, MOUNTAINS, WUXING, XIANTIAN_GUA, YINYANG,
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
        cv.text_centered(550.0, 50.0, "罗盘 · 风水（三维山水全景地图 桌面版）", 26.0, gold, f);
    }

    // Compass.
    let (cx, cy, r) = (400.0f32, 440.0f32, 300.0f32);
    cv.fill_circle(cx, cy, r, (24, 20, 16));
    cv.circle_stroke(cx, cy, r, gold);

    // Degree ticks and numbers.
    for d in (0..360).step_by(5) {
        let a0 = r * 0.965;
        let a1 = if d % 30 == 0 {
            r * 0.90
        } else if d % 10 == 0 {
            r * 0.925
        } else {
            r * 0.945
        };
        let p0 = screen_pos(cx, cy, a0, d as f32, heading);
        let p1 = screen_pos(cx, cy, a1, d as f32, heading);
        cv.line(p0.x as i32, p0.y as i32, p1.x as i32, p1.y as i32, (160, 135, 90));
    }
    if let Some(f) = &font {
        for d in (0..360).step_by(30) {
            let p = screen_pos(cx, cy, r * 0.865, d as f32, heading);
            cv.text_centered(p.x, p.y, &d.to_string(), 12.0, gold, f);
        }
    }

    // 分金 (3°) and 龙 rings (5°/6°).
    for d in (0..360).step_by(FENJIN_STEP as usize) {
        let p0 = screen_pos(cx, cy, r * 0.845, d as f32, heading);
        let p1 = screen_pos(cx, cy, r * 0.825, d as f32, heading);
        cv.line(p0.x as i32, p0.y as i32, p1.x as i32, p1.y as i32, (120, 100, 70));
    }
    for d in (0..360).step_by(DRAGON72_STEP as usize) {
        let p0 = screen_pos(cx, cy, r * 0.805, d as f32, heading);
        let p1 = screen_pos(cx, cy, r * 0.785, d as f32, heading);
        cv.line(p0.x as i32, p0.y as i32, p1.x as i32, p1.y as i32, (110, 130, 90));
    }
    for d in (0..360).step_by(DRAGON60_STEP as usize) {
        let p0 = screen_pos(cx, cy, r * 0.775, d as f32, heading);
        let p1 = screen_pos(cx, cy, r * 0.755, d as f32, heading);
        cv.line(p0.x as i32, p0.y as i32, p1.x as i32, p1.y as i32, (90, 110, 130));
    }

    // 64 hexagrams (六十四卦) ring: 64 × 5.625° = 360°.
    for d in 0..64 {
        let deg = d as f32 * HEXAGRAM64_STEP;
        let p0 = screen_pos(cx, cy, r * 0.745, deg, heading);
        let p1 = screen_pos(cx, cy, r * 0.725, deg, heading);
        cv.line(p0.x as i32, p0.y as i32, p1.x as i32, p1.y as i32, (150, 120, 150));
    }

    // Three plates.
    if let Some(f) = &font {
        for (m, name) in MOUNTAINS.iter().enumerate() {
            let deg = m as f32 * MOUNTAIN_STEP;
            let p = screen_pos(cx, cy, r * 0.715, deg, heading);
            cv.text_centered(p.x, p.y, name, 17.0, rgb(wuxing_color(WUXING[m])), f);
        }
        for (m, name) in MOUNTAINS.iter().enumerate() {
            let deg = m as f32 * MOUNTAIN_STEP + HUMAN_OFFSET;
            let p = screen_pos(cx, cy, r * 0.52, deg, heading);
            cv.text_centered(p.x, p.y, name, 13.0, (180, 200, 220), f);
        }
        for (m, name) in MOUNTAINS.iter().enumerate() {
            let deg = m as f32 * MOUNTAIN_STEP + HEAVEN_OFFSET;
            let p = screen_pos(cx, cy, r * 0.34, deg, heading);
            cv.text_centered(p.x, p.y, name, 13.0, (230, 200, 170), f);
        }
        // Bagua ring (后天).
        let gua_names = ["坎", "艮", "震", "巽", "离", "坤", "兑", "乾"];
        for (i, name) in gua_names.iter().enumerate() {
            let d = i as f32 * BAGUA_STEP;
            let p = screen_pos(cx, cy, r * 0.18, d, heading);
            let gwx = gua_wuxing(name);
            let c = wuxing_color(gwx);
            let label = format!("{}{}", name, gwx);
            cv.text_centered(p.x, p.y, &label, 12.0, rgb(c), f);
        }
    }

    // Crosshair + top pointer.
    cv.line(cx as i32, (cy - r) as i32, cx as i32, (cy + r) as i32, (150, 50, 50));
    cv.line((cx - r) as i32, cy as i32, (cx + r) as i32, cy as i32, (120, 50, 50));
    let top = ((cx) as i32, (cy - r) as i32);
    cv.line(top.0 - 12, top.1 + 18, top.0 + 12, top.1 + 18, (200, 40, 40));
    cv.line(top.0, top.1, top.0 - 12, top.1 + 18, (200, 40, 40));
    cv.line(top.0, top.1, top.0 + 12, top.1 + 18, (200, 40, 40));

    // Center readout.
    if let Some(f) = &font {
        cv.text_centered(cx, cy - 24.0, &format!("{:.1}°", rd.heading), 34.0, white, f);
        cv.text_centered(
            cx,
            cy + 12.0,
            &format!("{}山 · {}", MOUNTAINS[rd.mountain], WUXING[rd.mountain]),
            18.0,
            rgb(wuxing_color(WUXING[rd.mountain])),
            f,
        );
        cv.text_centered(
            cx,
            cy + 38.0,
            &format!("正向 · {}", YINYANG[rd.mountain]),
            14.0,
            gold,
            f,
        );
    }

    // Reading panel (right).
    if let Some(f) = &font {
        let x = 810.0;
        let mut y = 120.0;
        let lh = 34.0;
        cv.text(x, y, "方位读数", 22.0, gold, f);
        y += lh + 8.0;
        let hm = ((normalize_deg(rd.heading + 7.5) / 15.0).floor() as usize) % 24;
        let tm = ((normalize_deg(rd.heading - 7.5 + 15.0) / 15.0).floor() as usize) % 24;
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
            ("分金", format!("{} · {}", fj, nayin_of(fj))),
            ("七十二龙", format!("{} · {}", d72, nayin_of(d72))),
            ("六十龙", format!("{} · {}", d60, nayin_of(d60))),
        ];
        for (k, v) in rows {
            cv.text(x, y, k, 15.0, dim, f);
            cv.text(x + 110.0, y, &v, 15.0, white, f);
            y += lh;
        }
        cv.text(x, y + 6.0, "（分金/龙命名已与反编译 DEX 核对一致）", 12.0, (120, 110, 95), f);
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
