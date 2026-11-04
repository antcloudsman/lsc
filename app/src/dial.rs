//! One dial renderer for both ends of the desktop app.
//!
//! The interactive eframe UI (`main.rs`) and the headless artifact renderer
//! (`software.rs`) both call [`draw_dial`] through the [`DialCanvas`] trait, so the
//! craft screenshot shows exactly the same elements (rings, labels, plates, bagua,
//! pointer, centre readout) as the window the user sees. Only the painting backend
//! differs (egui Painter vs. the fontdue pixel canvas), hence anti-aliasing and font
//! hinting differences are expected and listed in `docs/design/round-c-diff.md`.

use eframe::egui::{Color32, Pos2};

use crate::{
    gua_color, gua_wuxing, reading_for, screen_pos, wuxing_color, RINGS,
    BAGUA_STEP, DRAGON60_OFFSET, DRAGON60_STEP, DRAGON72_STEP, EARTH_OFFSET, FENJIN_STEP,
    HEAVEN_OFFSET, HEXAGRAM64_STEP, HUMAN_OFFSET, JIAZI, MOUNTAIN_STEP, MOUNTAINS, WUXING,
    YINYANG,
};

/// Bagua ring mode (先天 / 后天).
#[derive(Clone, Copy, PartialEq)]
pub enum BaguaMode {
    XianTian,
    HouTian,
}

/// Which rings are drawn.
pub struct ShowRings {
    pub earth: bool,
    pub human: bool,
    pub heaven: bool,
    pub fenjin: bool,
    pub dragon72: bool,
    pub dragon60: bool,
    pub bagua: bool,
}

impl Default for ShowRings {
    fn default() -> Self {
        Self {
            earth: true,
            human: true,
            heaven: true,
            fenjin: true,
            dragon72: true,
            dragon60: true,
            bagua: true,
        }
    }
}

/// Everything the dial needs from the app state.
pub struct DialState<'a> {
    pub heading: f32,
    pub bagua_mode: BaguaMode,
    pub rings: &'a ShowRings,
}

/// Fixed chrome sizes shared with the headless artifact renderer so both put the
/// dial in exactly the same place.
pub const TOP_H: f32 = 38.0;
pub const LEFT_W: f32 = 300.0;
pub const RIGHT_W: f32 = 320.0;

/// Dial centre and radius for a `w × h` window (the central panel after chrome).
pub fn central_dial(w: f32, h: f32) -> (f32, f32, f32) {
    let avail_w = w - LEFT_W - RIGHT_W;
    let avail_h = h - TOP_H;
    let size = avail_w.min(avail_h).max(320.0);
    (LEFT_W + avail_w * 0.5, TOP_H + avail_h * 0.5, size * 0.5 - 8.0)
}

/// Minimal painting surface shared by egui and the software canvas.
pub trait DialCanvas {
    fn circle_filled(&mut self, c: Pos2, r: f32, color: Color32);
    fn circle_stroke(&mut self, c: Pos2, r: f32, color: Color32, width: f32);
    fn line(&mut self, a: Pos2, b: Pos2, color: Color32, width: f32);
    /// Centre-aligned text (both axes).
    fn text_centered(&mut self, p: Pos2, s: &str, size: f32, color: Color32);
    fn polygon(&mut self, points: &[Pos2], color: Color32);
}

fn ring<C: DialCanvas>(cv: &mut C, cx: f32, cy: f32, r: f32, frac0: f32, frac1: f32, color: Color32, width: f32) {
    cv.circle_stroke(Pos2::new(cx, cy), r * frac0, color, width);
    cv.circle_stroke(Pos2::new(cx, cy), r * frac1, color, width);
}

fn plate<C: DialCanvas>(cv: &mut C, cx: f32, cy: f32, radius: f32, offset: f32, font: f32, color: Color32, heading: f32) {
    for (m, name) in MOUNTAINS.iter().enumerate() {
        let deg = m as f32 * MOUNTAIN_STEP + offset;
        let p = screen_pos(cx, cy, radius, deg, heading);
        let c = wuxing_color(WUXING[m]).gamma_multiply(1.0).to_opaque().lerp_to_gamma(color, 0.25);
        cv.text_centered(p, name, font, c);
    }
}

/// Draw the whole compass face. `cx,cy,r` is the dial circle in screen space.
pub fn draw_dial<C: DialCanvas>(cv: &mut C, st: &DialState<'_>, cx: f32, cy: f32, r: f32) {
    let heading = st.heading;
    let fg = Color32::from_rgb(210, 178, 110); // antique gold

    // Outer face.
    cv.circle_filled(Pos2::new(cx, cy), r, Color32::from_rgb(24, 20, 16));
    cv.circle_stroke(Pos2::new(cx, cy), r, fg, 2.0_f32);

    // Degree ring: ticks every 5°, labels every 30°.
    for d in (0..360).step_by(5) {
        let a0 = r * 0.965;
        let a1 = if d % 30 == 0 { r * 0.90 } else if d % 10 == 0 { r * 0.925 } else { r * 0.945 };
        let p0 = screen_pos(cx, cy, a0, d as f32, heading);
        let p1 = screen_pos(cx, cy, a1, d as f32, heading);
        cv.line(p0, p1, fg.gamma_multiply(0.6), 1.0);
    }
    for d in (0..360).step_by(30) {
        let p = screen_pos(cx, cy, r * 0.865, d as f32, heading);
        cv.text_centered(p, &d.to_string(), 11.0, fg);
    }

    // 120 分金 ring (3° ticks) + 甲子 labels.
    if st.rings.fenjin {
        for d in (0..360).step_by(FENJIN_STEP as usize) {
            let p0 = screen_pos(cx, cy, r * 0.845, d as f32, heading);
            let p1 = screen_pos(cx, cy, r * 0.825, d as f32, heading);
            cv.line(p0, p1, Color32::from_rgb(140, 120, 90), 1.0);
        }
        ring(cv, cx, cy, r, 0.845, 0.82, Color32::from_rgb(120, 100, 70), 1.0);
        // B2: 120 cells x 5-char labels cannot fit r=232 (3 deg/cell); the table
        // text is shown in the reading panel, the ring keeps ticks for orientation.
    }

    // 72 dragons ring (5° ticks) + 龙 labels.
    if st.rings.dragon72 {
        for d in (0..360).step_by(DRAGON72_STEP as usize) {
            let p0 = screen_pos(cx, cy, r * 0.81, d as f32, heading);
            let p1 = screen_pos(cx, cy, r * 0.79, d as f32, heading);
            cv.line(p0, p1, Color32::from_rgb(120, 140, 100), 1.0);
        }
        ring(cv, cx, cy, r, 0.81, 0.79, Color32::from_rgb(90, 110, 80), 1.0);
        // B2: 72 labels come from rings[4]; the panel shows the current one.
    }

    // 60 dragons ring (TYPE_7 = interval 6.0 / offset 1.5):
    // cell k covers [1.5+6k, 7.5+6k), tick on the boundary, label at the centre 4.5+6k.
    if st.rings.dragon60 {
        for k in 0..60 {
            let d = DRAGON60_OFFSET + k as f32 * DRAGON60_STEP;
            let p0 = screen_pos(cx, cy, r * 0.78, d, heading);
            let p1 = screen_pos(cx, cy, r * 0.76, d, heading);
            cv.line(p0, p1, Color32::from_rgb(100, 120, 140), 1.0);
        }
        ring(cv, cx, cy, r, 0.78, 0.76, Color32::from_rgb(80, 100, 120), 1.0);
        // B2: 60 labels are 5 chars (山名+干支) -> panel carries the current one.
    }

    // 64 hexagrams (六十四卦) ring: 64 × 5.625° = 360°.
    for d in 0..64 {
        let deg = d as f32 * HEXAGRAM64_STEP;
        let p0 = screen_pos(cx, cy, r * 0.745, deg, heading);
        let p1 = screen_pos(cx, cy, r * 0.725, deg, heading);
        cv.line(p0, p1, Color32::from_rgb(150, 120, 150), 1.0);
    }

    // Three 24-mountain plates.
    if st.rings.earth {
        plate(cv, cx, cy, r * 0.715, EARTH_OFFSET, MOUNTAIN_STEP, Color32::WHITE, heading);
        ring(cv, cx, cy, r, 0.775, 0.655, fg, 1.5);
    }
    if st.rings.human {
        plate(cv, cx, cy, r * 0.58, HUMAN_OFFSET, 12.5, Color32::from_rgb(180, 200, 220), heading);
        ring(cv, cx, cy, r, 0.64, 0.52, fg.gamma_multiply(0.7), 1.0);
    }
    if st.rings.heaven {
        plate(cv, cx, cy, r * 0.44, HEAVEN_OFFSET, 12.5, Color32::from_rgb(230, 200, 170), heading);
        ring(cv, cx, cy, r, 0.50, 0.38, fg.gamma_multiply(0.7), 1.0);
    }

    // Bagua ring.
    if st.rings.bagua {
        // B2: text straight from mechanics.json rings (TYPE_2 后天 / TYPE_1 先天).
        let ringdef = match st.bagua_mode {
            BaguaMode::HouTian => &RINGS[1],
            BaguaMode::XianTian => &RINGS[0],
        };
        for (i, name) in ringdef.labels.iter().enumerate() {
            let d = ringdef.offset + (i as f32 + 0.5) * ringdef.interval;
            let p = screen_pos(cx, cy, r * 0.30, d, heading);
            let plain: String = name.chars().filter(|c| *c != '\u{200c}').collect();
            let gwx = gua_wuxing(&plain);
            cv.text_centered(p, &plain, 12.0, gua_color(&gwx));
        }
        ring(cv, cx, cy, r, 0.35, 0.24, fg.gamma_multiply(0.6), 1.0);
    }

    // Fixed crosshair + reading pointer (screen space, not rotating).
    cv.line(
        Pos2::new(cx, cy - r),
        Pos2::new(cx, cy + r),
        Color32::from_rgb(180, 50, 50).gamma_multiply(0.5),
        1.0,
    );
    cv.line(
        Pos2::new(cx - r, cy),
        Pos2::new(cx + r, cy),
        Color32::from_rgb(180, 50, 50).gamma_multiply(0.3),
        1.0,
    );
    // Red triangle at top = facing direction (向).
    let top = Pos2::new(cx, cy - r + 2.0);
    let tri = [
        top,
        Pos2::new(cx - 10.0, cy - r + 18.0),
        Pos2::new(cx + 10.0, cy - r + 18.0),
    ];
    cv.polygon(&tri, Color32::from_rgb(200, 40, 40));
    cv.text_centered(
        Pos2::new(cx, cy - r + 26.0),
        "向",
        11.0,
        Color32::from_rgb(200, 40, 40),
    );
    cv.text_centered(
        Pos2::new(cx, cy + r - 20.0),
        "坐",
        11.0,
        Color32::from_rgb(150, 90, 90),
    );

    // Center readout.
    let rd = reading_for(heading);
    cv.text_centered(Pos2::new(cx, cy - 22.0), &format!("{:.1}°", rd.heading), 30.0, Color32::WHITE);
    cv.text_centered(
        Pos2::new(cx, cy + 10.0),
        &format!("{} 山 · {}", MOUNTAINS[rd.mountain], WUXING[rd.mountain]),
        17.0,
        wuxing_color(WUXING[rd.mountain]),
    );
    cv.text_centered(
        Pos2::new(cx, cy + 34.0),
        &format!("{} · {}", crate::rd_offset_label(&rd), YINYANG[rd.mountain]),
        13.0,
        fg.gamma_multiply(0.8),
    );
}
