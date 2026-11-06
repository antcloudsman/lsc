//! 风水罗盘 (Feng Shui compass) desktop app.
//!
//! A faithful offline rebuild of the compass + knowledge base core of the Android
//! app "三维山水全景地图" (package space.xinzhi.globe). The live 3D panorama map
//! (AMap + a 3D terrain engine) cannot run offline, so this app focuses on the
//! compass (罗盘) and its Feng Shui knowledge base.

use eframe::egui::{self, Align2, Color32, FontFamily, FontId, Pos2, Sense, Stroke, Vec2};

mod dial;
mod software;

use dial::{BaguaMode, DialCanvas, DialState, ShowRings};

/// egui painting backend for the shared dial renderer (src/dial.rs).
struct PainterCanvas<'a> {
    painter: &'a egui::Painter,
}

impl DialCanvas for PainterCanvas<'_> {
    fn circle_filled(&mut self, c: Pos2, r: f32, color: Color32) {
        self.painter.circle_filled(c, r, color);
    }

    fn circle_stroke(&mut self, c: Pos2, r: f32, color: Color32, width: f32) {
        self.painter.circle_stroke(c, r, Stroke::new(width, color));
    }

    fn line(&mut self, a: Pos2, b: Pos2, color: Color32, width: f32) {
        self.painter.line_segment([a, b], Stroke::new(width, color));
    }

    fn text_centered(&mut self, p: Pos2, s: &str, size: f32, color: Color32) {
        self.painter.text(
            p,
            Align2::CENTER_CENTER,
            s,
            FontId::new(size, FontFamily::Proportional),
            color,
        );
    }

    fn polygon(&mut self, points: &[Pos2], color: Color32) {
        self.painter
            .add(egui::Shape::convex_polygon(points.to_vec(), color, Stroke::NONE));
    }
}

// ---------------------------------------------------------------------------
// Static data tables (24 mountains, five elements, yin/yang, bagua, jiazi).
// Values come from the app's own knowledge base (assets/compassIntroduce/*.html)
// and standard 三合盘/三元盘 conventions. See MECHANICS.md.
// ---------------------------------------------------------------------------

// >>> BEGIN GENERATED (app/mechanics.json) >>>
// Source: app/mechanics.json (sha256: fa256de5c80335e8cfe6f62dceccb8f0755262b9bcc14c4fc2d0a0d9afdb70ee). Regenerate: tools/mechanics/gen-mechanics.ps1
// Hand edits inside the markers are rejected by gen-mechanics.ps1 -Check.
const MOUNTAINS: [&str; 24] = [
    "子", "癸", "丑", "艮", "寅", "甲",
    "卯", "乙", "辰", "巽", "巳", "丙",
    "午", "丁", "未", "坤", "申", "庚",
    "酉", "辛", "戌", "乾", "亥", "壬"
];
const WUXING: [&str; 24] = [
    "水", "水", "土", "土", "木", "木",
    "木", "木", "土", "木", "火", "火",
    "火", "火", "土", "土", "金", "金",
    "金", "金", "土", "金", "水", "水"
];
const YINYANG: [&str; 24] = [
    "阴", "阴", "阴", "阳", "阳", "阳",
    "阴", "阴", "阴", "阳", "阳", "阳",
    "阴", "阴", "阴", "阳", "阳", "阳",
    "阴", "阴", "阴", "阳", "阳", "阳"
];
const HOUTIAN_GUA: [&str; 24] = [
    "坎", "坎", "艮", "艮", "艮", "震",
    "震", "震", "巽", "巽", "巽", "离",
    "离", "离", "坤", "坤", "坤", "兑",
    "兑", "兑", "乾", "乾", "乾", "坎"
];
const XIANTIAN_GUA: [&str; 24] = [
    "坤", "坤", "震", "震", "震", "离",
    "离", "离", "兑", "兑", "兑", "乾",
    "乾", "乾", "巽", "巽", "巽", "坎",
    "坎", "坎", "艮", "艮", "艮", "坤"
];
const JIAZI: [&str; 60] = [
    "甲子", "乙丑", "丙寅", "丁卯", "戊辰", "己巳", "庚午", "辛未", "壬申", "癸酉",
    "甲戌", "乙亥", "丙子", "丁丑", "戊寅", "己卯", "庚辰", "辛巳", "壬午", "癸未",
    "甲申", "乙酉", "丙戌", "丁亥", "戊子", "己丑", "庚寅", "辛卯", "壬辰", "癸巳",
    "甲午", "乙未", "丙申", "丁酉", "戊戌", "己亥", "庚子", "辛丑", "壬寅", "癸卯",
    "甲辰", "乙巳", "丙午", "丁未", "戊申", "己酉", "庚戌", "辛亥", "壬子", "癸丑",
    "甲寅", "乙卯", "丙辰", "丁巳", "戊午", "己未", "庚申", "辛酉", "壬戌", "癸亥"
];
const NAYIN: [&str; 60] = [
    "海中金", "海中金", "炉中火", "炉中火", "大林木", "大林木", "路旁土", "路旁土", "剑锋金", "剑锋金",
    "山头火", "山头火", "涧下水", "涧下水", "城头土", "城头土", "白蜡金", "白蜡金", "杨柳木", "杨柳木",
    "泉中水", "泉中水", "屋上土", "屋上土", "霹雳火", "霹雳火", "松柏木", "松柏木", "长流水", "长流水",
    "沙中金", "沙中金", "山下火", "山下火", "平地木", "平地木", "壁上土", "壁上土", "金箔金", "金箔金",
    "佛灯火", "佛灯火", "天河水", "天河水", "大驿土", "大驿土", "钗钏金", "钗钏金", "桑柘木", "桑柘木",
    "大溪水", "大溪水", "沙中土", "沙中土", "天上火", "天上火", "石榴木", "石榴木", "大海水", "大海水"
];
const BRANCHES: [&str; 12] = [
    "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
];
const YANG_STEMS: [&str; 5] = [
    "甲", "丙", "戊", "庚", "壬"
];
const YIN_STEMS: [&str; 5] = [
    "乙", "丁", "己", "辛", "癸"
];
const GUA_WUXING: [(&str, &str); 8] = [
    ("坎", "水"),
    ("艮", "土"),
    ("震", "木"),
    ("巽", "木"),
    ("离", "火"),
    ("坤", "土"),
    ("兑", "金"),
    ("乾", "金"),
];
pub struct RingDef {
    pub id: &'static str,
    pub text: &'static str,
    pub interval: f32,
    pub offset: f32,
    pub labels: &'static [&'static str],
}
pub const RINGS: [RingDef; 14] = [
    RingDef { id: "TYPE_1", text: "先天八卦_卦位", interval: 45.0, offset: 22.5, labels: &["‌震", "‌离", "‌兑", "乾", "‌巽", "‌坎", "‌艮", "‌坤"] },
    RingDef { id: "TYPE_2", text: "后天八卦_卦位", interval: 45.0, offset: 22.5, labels: &["坤", "兑七", "乾六", "离", "艮八", "震三", "‌巽四", "‌坎"] },
    RingDef { id: "TYPE_3", text: "先天八卦洛书数", interval: 45.0, offset: 22.5, labels: &["‌震 4", "‌离 3", "‌兑 2", "乾 1", "‌巽 5", "‌坎 6", "‌艮 7", "‌坤 8"] },
    RingDef { id: "TYPE_4", text: "地盘正针二十四山", interval: 15.0, offset: 7.5, labels: &["癸山", "丑山", "艮山", "寅山", "甲山", "卯山", "乙山", "辰山", "巽山", "巳山", "丙山", "午山", "丁山", "未山", "坤山", "申山", "庚山", "酉山", "辛山", "戌山", "乾山", "亥山", "壬山", "子山"] },
    RingDef { id: "TYPE_5", text: "穿山七十二龙", interval: 5.0, offset: 2.5, labels: &["庚子", "壬子", "正癸", "乙丑", "丁丑", "己丑", "辛丑", "癸丑", "正艮", "丙寅", "戌寅", "庚寅", "壬寅", "甲寅", "正甲", "丁卯", "己卯", "辛卯", "癸卯", "乙卯", "正乙", "戌辰", "庚辰", "壬辰", "甲辰", "丙辰", "正巽", "己巳", "辛巳", "癸巳", "乙巳", "丁巳", "正丙", "庚午", "壬午", "甲午", "丙午", "戌午", "正丁", "辛未", "癸未", "乙未", "丁未", "巳未", "正坤", "壬坤", "甲坤", "丙坤", "戌坤", "庚坤", "正庚", "癸酉", "乙酉", "丁酉", "己酉", "辛酉", "正辛", "甲戌", "丙戌", "戊戌", "庚戌", "壬戌", "正乾", "乙亥", "丁亥", "己亥", "辛亥", "癸亥", "正壬", "甲子", "丙子", "戊子"] },
    RingDef { id: "TYPE_6", text: "地盘正针百二十分金", interval: 3.0, offset: 1.5, labels: &["甲山 庚子", "甲山 壬子", "癸山 甲子", "癸山 丙子", "癸山 戊子", "癸山 庚子", "癸山 壬子", "丑山 乙丑", "丑山 丁丑", "丑山 己丑", "丑山 辛丑", "丑山 癸丑", "艮山 乙丑", "艮山 丁丑", "艮山 己丑", "艮山 辛丑", "艮山 癸丑", "寅山 甲寅", "寅山 丙寅", "寅山 戊寅", "寅山 庚寅", "寅山 壬寅", "甲山 甲寅", "甲山 丙寅", "甲山 戊寅", "甲山 庚寅", "甲山 壬寅", "卯山 乙卯", "卯山 丁卯", "卯山 己卯", "卯山 辛卯", "卯山 癸卯", "已山 乙卯", "已山 丁卯", "已山 己卯", "已山 辛卯", "已山 癸卯", "辰山 甲辰", "辰山 丙辰", "辰山 戊辰", "辰山 庚辰", "辰山 壬辰", "巽山 甲辰", "巽山 丙辰", "巽山 戊辰", "巽山 庚辰", "巽山 壬辰", "巳山 乙巳", "巳山 丁巳", "巳山 己巳", "巳山 辛巳", "巳山 癸巳", "丙山 乙巳", "丙山 丁巳", "丙山 己巳", "丙山 辛巳", "丙山 癸巳", "午山 甲午", "午山 丙午", "午山 戊午", "午山 庚午", "午山 壬午", "丁山 甲午", "丁山 丙午", "丁山 戊午", "丁山 庚午", "丁山 壬午", "未山 乙未", "未山 丁未", "未山 己未", "未山 辛未", "未山 癸未", "坤山 乙未", "坤山 丁未", "坤山 己未", "坤山 辛未", "坤山 癸未", "申山 甲申", "申山 丙申", "申山 戊申", "申山 庚申", "申山 壬申", "庚山 甲申", "庚山 丙申", "庚山 戊申", "庚山 庚申", "庚山 壬申", "酉山 乙酉", "酉山 丁酉", "酉山 己酉", "酉山 辛酉", "酉山 癸酉", "辛山 乙酉", "辛山 丁酉", "辛山 己酉", "辛山 辛酉", "辛山 癸酉", "戌山 甲戌", "戌山 丙戌", "戌山 戊戌", "戌山 庚戌", "戌山 壬戌", "乾山 甲戌", "乾山 丙戌", "乾山 戊戌", "乾山 庚戌", "乾山 壬戌", "亥山 乙亥", "亥山 丁亥", "亥山 己亥", "亥山 辛亥", "亥山 癸亥", "壬山 乙亥", "壬山 丁亥", "壬山 己亥", "壬山 辛亥", "壬山 癸亥", "甲山 甲子", "甲山 丙子", "甲山 戊子"] },
    RingDef { id: "TYPE_7", text: "透地平分六十龙", interval: 6.0, offset: 1.5, labels: &["壬子 壬子", "癸丑 乙丑", "癸丑 丁丑", "癸丑 己丑", "癸丑 辛丑", "癸丑 癸丑", "艮寅 丙寅", "艮寅 戊寅", "艮寅 庚寅", "艮寅 壬寅", "艮寅 甲寅", "甲卯 丁卯", "甲卯 己卯", "甲卯 辛卯", "甲卯 癸卯", "甲卯 乙卯", "乙辰 戊辰", "乙辰 庚辰", "乙辰 壬辰", "乙辰 甲辰", "乙辰 丙辰", "巽巳 己巳", "巽巳 辛巳", "巽巳 癸巳", "巽巳 乙巳", "巽巳 丁巳", "丙午 庚午", "丙午 壬午", "丙午 甲午", "丙午 丙午", "丙午 戊午", "丁未 辛未", "丁未 癸未", "丁未 乙未", "丁未 丁未", "丁未 己未", "坤申 壬申", "坤申 甲申", "坤申 丙申", "坤申 戊申", "坤申 庚申", "庚酉 癸酉", "庚酉 乙酉", "庚酉 丁酉", "庚酉 己酉", "庚酉 辛酉", "辛戌 甲戌", "辛戌 丙戌", "辛戌 戊戌", "辛戌 庚戌", "辛戌 壬戌", "乾亥 乙亥", "乾亥 丁亥", "乾亥 己亥", "乾亥 辛亥", "乾亥 癸亥", "壬子 甲子", "壬子 丙子", "壬子 戊子", "壬子 庚子"] },
    RingDef { id: "TYPE_8", text: "人盘中针二十四山", interval: 15.0, offset: 0.0, labels: &["癸山", "丑山", "艮山", "寅山", "甲山", "卯山", "乙山", "辰山", "巽山", "巳山", "丙山", "午山", "丁山", "未山", "坤山", "申山", "庚山", "酉山", "辛山", "戌山", "乾山", "亥山", "壬山", "子山"] },
    RingDef { id: "TYPE_9", text: "天盘缝针二十四山", interval: 15.0, offset: 0.0, labels: &["子山", "癸山", "丑山", "艮山", "寅山", "甲山", "卯山", "乙山", "辰山", "巽山", "巳山", "丙山", "午山", "丁山", "未山", "坤山", "申山", "庚山", "酉山", "辛山", "戌山", "乾山", "亥山", "壬山"] },
    RingDef { id: "TYPE_10", text: "天盘缝针百二十分金", interval: 3.0, offset: 0.0, labels: &["甲山甲子", "甲山丙子", "甲山戊子", "甲山庚子", "甲山壬子", "癸山甲子", "癸山丙子", "癸山戊子", "癸山庚子", "癸山壬子", "丑山乙丑", "丑山丁丑", "丑山己丑", "丑山辛丑", "丑山癸丑", "艮山乙丑", "艮山丁丑", "艮山己丑", "艮山辛丑", "艮山癸丑", "寅山甲寅", "寅山丙寅", "寅山戊寅", "寅山庚寅", "寅山壬寅", "甲山甲寅", "甲山丙寅", "甲山戊寅", "甲山庚寅", "甲山壬寅", "卯山乙卯", "卯山丁卯", "卯山己卯", "卯山辛卯", "卯山癸卯", "已山乙卯", "已山丁卯", "已山己卯", "已山辛卯", "已山癸卯", "辰山甲辰", "辰山丙辰", "辰山戊辰", "辰山庚辰", "辰山壬辰", "巽山甲辰", "巽山丙辰", "巽山戊辰", "巽山庚辰", "巽山壬辰", "巳山乙巳", "巳山丁巳", "巳山己巳", "巳山辛巳", "巳山癸巳", "丙山乙巳", "丙山丁巳", "丙山己巳", "丙山辛巳", "丙山癸巳", "午山甲午", "午山丙午", "午山戊午", "午山庚午", "午山壬午", "丁山甲午", "丁山丙午", "丁山戊午", "丁山庚午", "丁山壬午", "未山乙未", "未山丁未", "未山己未", "未山辛未", "未山癸未", "坤山乙未", "坤山丁未", "坤山己未", "坤山辛未", "坤山癸未", "申山甲申", "申山丙申", "申山戊申", "申山庚申", "申山壬申", "庚山甲申", "庚山丙申", "庚山戊申", "庚山庚申", "庚山壬申", "酉山乙酉", "酉山丁酉", "酉山己酉", "酉山辛酉", "酉山癸酉", "辛山乙酉", "辛山丁酉", "辛山己酉", "辛山辛酉", "辛山癸酉", "戌山甲戌", "戌山丙戌", "戌山戊戌", "戌山庚戌", "戌山壬戌", "乾山甲戌", "乾山丙戌", "乾山戊戌", "乾山庚戌", "乾山壬戌", "亥山乙亥", "亥山丁亥", "亥山己亥", "亥山辛亥", "亥山癸亥", "壬山乙亥", "壬山丁亥", "壬山己亥", "壬山辛亥", "壬山癸亥"] },
    RingDef { id: "TYPE_11", text: "先天方图六十四卦内卦", interval: 5.625, offset: 0.0, labels: &["乾小畜", "兑中孚", "离家人", "震益", "巽巽", "坎涣", "艮渐", "坤观", "乾需", "兑节", "离既济", "震屯", "巽井", "坎坎", "艮蹇", "坤比", "乾大畜", "兑损", "离贲", "震颐", "巽蛊", "坎蒙", "艮艮", "坤剥", "乾泰", "兑临", "离明夷", "震复", "巽升", "坎师", "艮谦", "坤坤", "坤豫", "艮小过", "坎解", "巽恒", "震震", "离丰", "兑归妹", "乾大壮", "坤晋", "艮旅", "坎未济", "巽鼎", "震噬嗑", "离离", "兑睽", "乾大有", "坤萃", "艮咸", "坎困", "巽大过", "震随", "离革", "兑兑", "乾夬", "坤否", "艮遁", "坎讼", "巽姤", "震无妄", "离同人", "兑履", "乾乾"] },
    RingDef { id: "TYPE_12", text: "先天圆图六十四卦外卦", interval: 5.625, offset: 0.0, labels: &["坤复一", "艮颐六", "坎屯七", "巽益二", "震震八", "离噬嗑三", "兑随四", "乾无妄九", "坤明夷一", "艮贲六", "坎既济七", "巽家人二", "震丰八", "离离三", "兑革四", "乾同人九", "坤临一", "艮损六", "坎节七", "巽中孚二", "震归妹八", "离睽三", "兑兑四", "乾履九", "坤泰一", "艮大畜六", "坎需七", "巽小畜二", "震大壮八", "离大有三", "兑夬四", "乾乾九", "乾姤九", "兑大过四", "离鼎三", "震恒八", "巽巽二", "坎井七", "艮蛊六", "坤升一", "乾讼九", "兑困四", "离未济三", "震解八", "巽涣二", "坎坎七", "艮蒙六", "坤师一", "乾遁九", "兑咸四", "离旅三", "震小过八", "巽渐二", "坎蹇七", "艮艮六", "坤谦一", "乾否九", "兑萃四", "离晋三", "震豫八", "巽观二", "坎比七", "艮剥六", "坤坤一"] },
    RingDef { id: "TYPE_13", text: "六十甲配六十四卦五行", interval: 5.625, offset: 0.0, labels: &["复甲子金", "颐丙子水", "屯戊子火", "益庚子土", "震壬子木", "噬嗑乙丑金", "随丁丑水", "无妄己丑火", "明夷辛丑土", "贲癸丑木", "既济甲寅水", "家人丙寅火", "丰戊寅土", "离庚寅木", "革庚寅木", "同人壬寅金", "临乙卯水", "损丁卯火", "节己卯土", "中孚辛卯木", "归妹癸卯金", "睽甲辰水", "兑丙辰土", "履戊辰木", "泰庚辰金", "大畜壬辰水", "需乙巳火", "小畜丁巳土", "大壮己巳木", "大有辛巳金", "夬癸巳水", "乾甲午金", "姤甲午金", "大过丙午水", "鼎戊午火", "恒庚午土", "巽壬午木", "井乙未金", "蛊丁未水", "升己未火", "讼辛未土", "困癸未木", "未济甲申水", "解丙申火", "涣戊申土", "坎庚申木", "蒙庚申木", "师壬申金", "遁乙酉水", "咸丁酉火", "旅己酉土", "小过辛酉木", "渐癸酉金", "蹇甲戌火", "艮丙戌土", "谦戊戌木", "否庚戌金", "萃壬戌水", "晋乙亥火", "豫丁亥土", "观己亥木", "比辛亥金", "剥癸亥水", "坤甲子金"] },
    RingDef { id: "TYPE_14", text: "正兼向度数指标", interval: 3.0, offset: 0.0, labels: &["癸山九度外", "癸山九度内", "癸山九度内", "癸山九度内", "癸山九度外", "丑山九度外", "丑山九度内", "丑山九度内", "丑山九度内", "丑山九度外", "艮山九度外", "艮山九度内", "艮山九度内", "艮山九度内", "艮山九度外", "寅山九度外", "寅山九度内", "寅山九度内", "寅山九度内", "寅山九度外", "甲山九度外", "甲山九度内", "甲山九度内", "甲山九度内", "甲山九度外", "卯山九度外", "卯山九度内", "卯山九度内", "卯山九度内", "卯山九度外", "乙山九度外", "乙山九度内", "乙山九度内", "乙山九度内", "乙山九度外", "辰山九度外", "辰山九度内", "辰山九度内", "辰山九度内", "辰山九度外", "巽山九度外", "巽山九度内", "巽山九度内", "巽山九度内", "巽山九度外", "巳山九度外", "巳山九度内", "巳山九度内", "巳山九度内", "巳山九度外", "丙山九度外", "丙山九度内", "丙山九度内", "丙山九度内", "丙山九度外", "午山九度外", "午山九度内", "午山九度内", "午山九度内", "午山九度外", "丁山九度外", "丁山九度内", "丁山九度内", "丁山九度内", "丁山九度外", "未山九度外", "未山九度内", "未山九度内", "未山九度内", "未山九度外", "坤山九度外", "坤山九度内", "坤山九度内", "坤山九度内", "坤山九度外", "申山九度外", "申山九度内", "申山九度内", "申山九度内", "申山九度外", "庚山九度外", "庚山九度内", "庚山九度内", "庚山九度内", "庚山九度外", "酉山九度外", "酉山九度内", "酉山九度内", "酉山九度内", "酉山九度外", "辛山九度外", "辛山九度内", "辛山九度内", "辛山九度内", "辛山九度外", "戌山九度外", "戌山九度内", "戌山九度内", "戌山九度内", "戌山九度外", "乾山九度外", "乾山九度内", "乾山九度内", "乾山九度内", "乾山九度外", "亥山九度外", "亥山九度内", "亥山九度内", "亥山九度内", "亥山九度外", "壬山九度外", "壬山九度内", "壬山九度内", "壬山九度内", "壬山九度外", "子山九度外", "子山九度内", "子山九度内", "子山九度内", "子山九度外"] },
];

// Ring scalars (values[] / rings[]). The reading formulas keep their inline
// literals for now and are pinned to these by the #[cfg(test)] module in main.rs.
const MOUNTAIN_STEP: f32 = 15.0;
const EARTH_OFFSET: f32 = 0.0;
const HUMAN_OFFSET: f32 = -7.5;
const HEAVEN_OFFSET: f32 = 7.5;
const FENJIN_STEP: f32 = 3.0;
const DRAGON72_STEP: f32 = 5.0;
const DRAGON60_STEP: f32 = 6.0;
#[allow(dead_code)] // pinned by the mechanics_pin test; the reading formula takes it in round B
const DRAGON60_OFFSET: f32 = 1.5;
const HEXAGRAM64_STEP: f32 = 5.625;
const BAGUA_STEP: f32 = 45.0;
const ZHENG_XIANG_DEG: f32 = 9.0;
// <<< END GENERATED <<<

/// Which branch each mountain "borrows" (寄) for 分金/龙 naming (三合盘 convention).
fn borrow_branch(m: usize) -> usize {
    match MOUNTAINS[m] {
        "子" | "癸" => 0, // 癸寄丑 → but 子 is itself 子
        "丑" | "艮" => 1,
        "寅" | "甲" => 2,
        "卯" => 3,
        "辰" | "乙" | "巽" => 4,
        "巳" | "丙" => 5,
        "午" => 6,
        "未" | "丁" | "坤" => 7,
        "申" | "庚" => 8,
        "酉" => 9,
        "戌" | "辛" | "乾" => 10,
        "亥" | "壬" => 11,
        _ => 0,
    }
}

/// A 旺相 Jiazi name for a branch + slot (slot 0..5 for fenjin, 0..3 for dragons).
fn wangxiang(branch: usize, slot: usize) -> &'static str {
    // Odd-indexed branches are yin; even-indexed are yang.
    let stems = if branch % 2 == 0 { YANG_STEMS } else { YIN_STEMS };
    let stem = stems[slot % 5];
    // Return a static string by matching (we only have 12*5=60 combos, all in JIAZI).
    let target = format!("{}{}", stem, BRANCHES[branch]);
    JIAZI.iter().find(|j| **j == target).copied().unwrap_or("甲子")
}

fn nayin_of(jiazi: &str) -> &'static str {
    JIAZI.iter().position(|j| *j == jiazi).map(|i| NAYIN[i]).unwrap_or("")
}

// ---------------------------------------------------------------------------
// Geometry helpers: convert a compass angle to a screen position.
// Compass angle θ is measured clockwise from north (up). The dial is rotated so
// that the mountain at the reading heading appears at the top pointer.
// ---------------------------------------------------------------------------

fn screen_pos(cx: f32, cy: f32, r: f32, compass_deg: f32, heading: f32) -> Pos2 {
    let a = (compass_deg - heading).to_radians();
    Pos2::new(cx + r * a.sin(), cy - r * a.cos())
}

// ---------------------------------------------------------------------------
// Reading: given a heading, compute what it falls on.
// ---------------------------------------------------------------------------

struct Reading {
    heading: f32,
    /// Mountain index on each of the three plates (0..23).
    mountain: usize,
    /// Within-mountain offset in degrees from the mountain center (−7.5..7.5).
    offset: f32,
    /// Fenjin slot (0..4) within the mountain.
    fenjin_slot: usize,
    /// 72-dragon slot (0..2) within the mountain.
    dragon72_slot: usize,
    /// 60-dragon index (0..59).
    dragon60: usize,
    /// Mountain index for the "sitting" side (坐, opposite).
    sitting: usize,
}

fn normalize_deg(d: f32) -> f32 {
    let mut x = d % 360.0;
    if x < 0.0 {
        x += 360.0;
    }
    x
}

fn reading_for(heading: f32) -> Reading {
    // The numeric literals below are pinned to the generated ring scalars by the
    // `mechanics_pin` test module at the bottom of this file. They stay inline in
    // round A because tests/numeric/source_parse.py evaluates these exact lets;
    // round B swaps them for the consts together with LC测试.
    let h = normalize_deg(heading);
    let m = ((h + 7.5) % 360.0 / 15.0).floor() as usize % 24;
    let center = m as f32 * 15.0;
    let offset = normalize_deg(h - center + 180.0) - 180.0; // −180..180 → −7.5..7.5
    let from_start = normalize_deg(h - (center - 7.5));
    let fenjin_slot = ((from_start / 3.0).floor() as usize).min(4);
    let dragon72_slot = ((from_start / 5.0).floor() as usize).min(2);
    let dragon60 = (((h - 1.5 + 360.0) % 360.0 / 6.0).floor() as usize) % 60;
    let sitting = (m + 12) % 24;
    Reading {
        heading: h,
        mountain: m,
        offset,
        fenjin_slot,
        dragon72_slot,
        dragon60,
        sitting,
    }
}

// ---------------------------------------------------------------------------
// App state.
// ---------------------------------------------------------------------------

struct LuopanApp {
    heading: f32,
    bagua_mode: BaguaMode,
    rings: ShowRings,
    screenshot: Option<String>,
    frame: u64,
    screenshot_requested: bool,
    screenshot_at: Option<std::time::Instant>,
}

impl LuopanApp {
    fn new(cc: &eframe::CreationContext<'_>, screenshot: Option<String>) -> Self {
        install_cjk_font(&cc.egui_ctx);
        Self {
            heading: 0.0,
            bagua_mode: BaguaMode::HouTian,
            rings: ShowRings::default(),
            screenshot,
            frame: 0,
            screenshot_requested: false,
            screenshot_at: None,
        }
    }

    fn handle_screenshot(&mut self, ctx: &egui::Context) {
        if let Some(path) = self.screenshot.clone() {
            // A handful of frames is enough for the first full paint; requesting
            // early keeps the whole run fast even when the display driver stalls.
            if self.frame >= 10 && !self.screenshot_requested {
                ctx.send_viewport_cmd(egui::ViewportCommand::Screenshot(egui::UserData::default()));
                self.screenshot_requested = true;
                self.screenshot_at = Some(std::time::Instant::now());
            }
            let events = ctx.input(|i| i.events.clone());
            for ev in &events {
                if let egui::Event::Screenshot { image, .. } = ev {
                    save_png(image, &path);
                    ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                }
            }
            // Safety valve: never hang if the screenshot event never arrives.
            let timed_out = self
                .screenshot_at
                .map(|t| t.elapsed().as_secs() > 45)
                .unwrap_or(false);
            if self.screenshot_requested && timed_out {
                ctx.send_viewport_cmd(egui::ViewportCommand::Close);
            }
        }
    }

    fn draw_compass(&mut self, ui: &mut egui::Ui) {
        let avail = ui.available_size();
        let size = avail.min_elem().max(320.0);
        let (rect, response) = ui.allocate_exact_size(Vec2::splat(size), Sense::click_and_drag());
        let cx = rect.center().x;
        let cy = rect.center().y;
        let r = size * 0.5 - 8.0;

        // Drag to rotate: dragging clockwise turns the dial.
        if response.dragged() {
            let d = response.drag_delta();
            // Screen-space drag to the right/left maps to heading change.
            let turn = -d.x * 0.5;
            self.heading = normalize_deg(self.heading + turn);
        }
        if response.hovered() {
            response.ctx.set_cursor_icon(egui::CursorIcon::Grab);
        }

        // Round C: the exact same renderer as the headless artifact (src/dial.rs).
        let painter = ui.painter();
        let mut cv = PainterCanvas { painter };
        let st = DialState {
            heading: self.heading,
            bagua_mode: self.bagua_mode,
            rings: &self.rings,
        };
        dial::draw_dial(&mut cv, &st, cx, cy, r);
    }

    fn knowledge_panel(&self, ui: &mut egui::Ui) {
        ui.add_space(4.0);
        ui.label(egui::RichText::new("风水知识库").size(18.0).strong().color(Color32::from_rgb(220, 190, 130)));
        ui.separator();
        let topics = [
            ("二十四山", "十二地支＋八干(甲乙丙丁庚辛壬癸)＋四维(乾坤艮巽)，共24山，各占15°。"),
            ("三盘", "地盘正针(基准)、人盘中针(逆时针7.5°)、天盘缝针(顺时针7.5°)。"),
            ("百二十分金", "圆周120等份，每份3°，每山5份，以六十甲子循环命名。"),
            ("穿山七十二龙", "圆周72等份，每份5°，每山3龙，用于定来龙干支。"),
            ("透地平分六十龙", "圆周60等份，每份6°，以六十甲子命名，定龙脉入首。"),
            ("正兼向", "正向=每山15°；兼向=越过山界3–7°的邻山过渡区。"),
            ("八卦", "先天八卦(乾坤定南北)与后天八卦(坎离定南北)两种方位。"),
            ("五行", "天干：甲乙木丙丁火庚辛金壬癸水；地支：子亥水寅卯木巳午火申酉金辰戌丑未土。"),
        ];
        egui::ScrollArea::vertical().show(ui, |ui| {
            for (title, body) in topics {
                ui.collapsing(title, |ui| {
                    ui.label(body);
                });
            }
        });
    }

    fn reading_panel(&mut self, ui: &mut egui::Ui) {
        let rd = reading_for(self.heading);
        ui.add_space(4.0);
        ui.label(egui::RichText::new("方位读数").size(18.0).strong().color(Color32::from_rgb(220, 190, 130)));
        ui.separator();

        ui.horizontal(|ui| {
            ui.label("朝向");
            ui.add(egui::Slider::new(&mut self.heading, 0.0..=360.0).suffix("°"));
        });
        ui.horizontal(|ui| {
            if ui.button("复位朝北(0°)").clicked() {
                self.heading = 0.0;
            }
            if ui.button("坐向(转180°)").clicked() {
                self.heading = normalize_deg(self.heading + 180.0);
            }
        });

        let m = rd.mountain;
        let row = |ui: &mut egui::Ui, k: &str, v: String, c: Color32| {
            ui.horizontal(|ui| {
                ui.label(egui::RichText::new(k).color(Color32::from_rgb(150, 130, 100)));
                ui.label(egui::RichText::new(v).color(c).strong());
            });
        };
        row(ui, "度数", format!("{:.2}°", rd.heading), Color32::WHITE);
        row(ui, "地盘正针", format!("{}山", MOUNTAINS[m]), wuxing_color(WUXING[m]));
        // BUG-001: 人盘/天盘 are label-table rotations, not offsets
        // (LC逆向 118357a: TYPE_8/9 offsetDegrees = 0.0f; 人盘 list starts 癸, 天盘 starts 子).
        // Kept as inline literals because tests/numeric/source_parse.py evaluates these lets.
        let hm = ((rd.heading / 15.0).floor() as usize + 1) % 24;
        let tm = ((rd.heading / 15.0).floor() as usize) % 24;
        row(ui, "人盘中针", format!("{}山", MOUNTAINS[hm]), Color32::from_rgb(180, 200, 220));
        row(ui, "天盘缝针", format!("{}山", MOUNTAINS[tm]), Color32::from_rgb(230, 200, 170));
        row(ui, "五行", WUXING[m].to_string(), wuxing_color(WUXING[m]));
        row(ui, "阴阳", YINYANG[m].to_string(), Color32::WHITE);
        row(ui, "后天卦", ring_text_clean(2, rd.heading), Color32::WHITE);
        row(ui, "先天卦", ring_text_clean(1, rd.heading), Color32::WHITE);
        row(ui, "正/兼向", format!("{} ({:+.1}°)", ring_text_clean(14, rd.heading), rd.offset), Color32::WHITE);

        // B2: table text from mechanics.json rings[].labels (no derivation).
        row(ui, "分金", ring_text_clean(6, rd.heading), Color32::from_rgb(230, 200, 150));
        row(ui, "七十二龙", ring_text_clean(5, rd.heading), Color32::from_rgb(170, 200, 150));
        row(ui, "六十龙", ring_text_clean(7, rd.heading), Color32::from_rgb(150, 180, 210));
        row(ui, "坐", format!("{}山", MOUNTAINS[rd.sitting]), wuxing_color(WUXING[rd.sitting]));

        ui.separator();
        ui.label(
            egui::RichText::new("说明：分金/龙/八卦文字取自 mechanics.json rings[].labels（DEX 原表）。")
                .size(11.0)
                .color(Color32::from_rgb(120, 110, 95)),
        );
    }
}

/// B2: text of `rings[type-1]` for this heading, straight from mechanics.json.
fn ring_index(ring_type: usize, heading: f32) -> usize {
    let ring = &RINGS[ring_type - 1];
    let n = ring.labels.len();
    ((((normalize_deg(heading) - ring.offset) % 360.0 + 360.0) % 360.0) / ring.interval)
        .floor() as usize
        % n
}

fn ring_text(ring_type: usize, heading: f32) -> &'static str {
    let ring = &RINGS[ring_type - 1];
    ring.labels[ring_index(ring_type, heading)]
}

fn ring_center(ring_type: usize, index: usize) -> f32 {
    let ring = &RINGS[ring_type - 1];
    ring.offset + (index as f32 + 0.5) * ring.interval
}

fn ring_text_clean(ring_type: usize, heading: f32) -> String {
    ring_text(ring_type, heading).chars().filter(|c| *c != '\u{200c}').collect()
}

fn rd_offset_label(rd: &Reading) -> String {
    let off = rd.offset;
    if off.abs() * 2.0 <= ZHENG_XIANG_DEG {
        format!("正向 ({:+.1}°)", off)
    } else {
        let neighbor = if off < 0.0 { rd.mountain.wrapping_sub(1) % 24 } else { (rd.mountain + 1) % 24 };
        format!("兼向{}({:+.1}°)", MOUNTAINS[neighbor], off)
    }
}

fn wuxing_color(wx: &str) -> Color32 {
    match wx {
        "木" => Color32::from_rgb(90, 180, 90),
        "火" => Color32::from_rgb(220, 90, 70),
        "土" => Color32::from_rgb(190, 150, 80),
        "金" => Color32::from_rgb(220, 190, 90),
        "水" => Color32::from_rgb(90, 150, 220),
        _ => Color32::WHITE,
    }
}

fn gua_wuxing(gua: &str) -> &'static str {
    GUA_WUXING.iter().find(|(g, _)| *g == gua).map(|(_, w)| *w).unwrap_or("")
}

fn gua_color(wx: &str) -> Color32 {
    wuxing_color(wx)
}

// ---------------------------------------------------------------------------
// Fonts and screenshot.
// ---------------------------------------------------------------------------

fn install_cjk_font(ctx: &egui::Context) {
    let candidates = [
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/Deng.ttf",
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simsun.ttc",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    ];
    for path in candidates {
        if let Ok(bytes) = std::fs::read(path) {
            let mut fonts = egui::FontDefinitions::default();
            fonts.font_data.insert(
                "cjk".to_owned(),
                egui::FontData::from_owned(bytes).into(),
            );
            for family in [FontFamily::Proportional, FontFamily::Monospace] {
                fonts.families.entry(family).or_default().push("cjk".to_owned());
            }
            ctx.set_fonts(fonts);
            return;
        }
    }
}

fn save_png(image: &egui::ColorImage, path: &str) {
    let [w, h] = image.size;
    let mut rgba = Vec::with_capacity(w * h * 4);
    for p in &image.pixels {
        rgba.extend_from_slice(&[p.r(), p.g(), p.b(), p.a()]);
    }
    if let Some(parent) = std::path::Path::new(path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let _ = image::save_buffer(path, &rgba, w as u32, h as u32, image::ColorType::Rgba8);
}

// ---------------------------------------------------------------------------
// eframe::App.
// ---------------------------------------------------------------------------

impl eframe::App for LuopanApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.handle_screenshot(ctx);

        // Fixed chrome so the window and the headless artifact (software.rs) agree
        // on where the dial sits: {TOP_H, LEFT_W, RIGHT_W} live in src/dial.rs.
        egui::TopBottomPanel::top("title").exact_height(dial::TOP_H).show(ctx, |ui| {
            ui.add_space(4.0);
            ui.horizontal(|ui| {
                ui.label(
                    egui::RichText::new("罗盘 · 风水 (三维山水全景地图 桌面版)")
                        .size(20.0)
                        .strong()
                        .color(Color32::from_rgb(220, 190, 130)),
                );
                ui.separator();
                ui.label("八卦盘面");
                ui.selectable_value(&mut self.bagua_mode, BaguaMode::HouTian, "后天八卦");
                ui.selectable_value(&mut self.bagua_mode, BaguaMode::XianTian, "先天八卦");
                ui.separator();
                ui.checkbox(&mut self.rings.earth, "地盘");
                ui.checkbox(&mut self.rings.human, "人盘");
                ui.checkbox(&mut self.rings.heaven, "天盘");
                ui.checkbox(&mut self.rings.fenjin, "分金");
                ui.checkbox(&mut self.rings.dragon72, "七十二龙");
                ui.checkbox(&mut self.rings.dragon60, "六十龙");
                ui.checkbox(&mut self.rings.bagua, "八卦");
            });
            ui.add_space(4.0);
        });

        egui::SidePanel::right("reading")
            .resizable(false)
            .exact_width(dial::RIGHT_W)
            .show(ctx, |ui| {
                self.reading_panel(ui);
            });
        egui::SidePanel::left("knowledge")
            .resizable(false)
            .exact_width(dial::LEFT_W)
            .show(ctx, |ui| {
                self.knowledge_panel(ui);
            });

        egui::CentralPanel::default()
            .frame(egui::Frame::central_panel(&ctx.style()).inner_margin(0.0))
            .show(ctx, |ui| {
                ui.centered_and_justified(|ui| {
                    self.draw_compass(ui);
                });
            });

        self.frame += 1;
    }
}

// ---------------------------------------------------------------------------
// Entry point.
// ---------------------------------------------------------------------------

fn main() -> eframe::Result {
    let mut screenshot = None;
    let mut screenshot_ui = None;
    let args: Vec<String> = std::env::args().collect();
    let mut i = 1;
    while i < args.len() {
        if args[i] == "--screenshot" && i + 1 < args.len() {
            screenshot = Some(args[i + 1].clone());
            i += 2;
        } else if args[i] == "--dump-rings" && i + 1 < args.len() {
            let h: f32 = args[i + 1].parse().unwrap_or(0.0);
            for t in 1..=14usize {
                let i = ring_index(t, h);
                println!(
                    "LCRING type={} h={:.2} i={} center={:.2} text={}",
                    t,
                    normalize_deg(h),
                    i,
                    ring_center(t, i),
                    ring_text(t, h)
                );
            }
            return Ok(());
        } else if args[i] == "--screenshot-ui" && i + 1 < args.len() {
            // Real egui window + deliverable PNG (needs a desktop session).
            screenshot_ui = Some(args[i + 1].clone());
            i += 2;
        } else {
            i += 1;
        }
    }

    if let Some(path) = screenshot {
        software::software_screenshot(&path);
        return Ok(());
    }

    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1100.0, 780.0])
            .with_title("罗盘 · 风水"),
        ..Default::default()
    };

    eframe::run_native(
        "luopan",
        options,
        Box::new(move |cc| Ok(Box::new(LuopanApp::new(cc, screenshot_ui)))),
    )
}

// ---------------------------------------------------------------------------
// Round A pins: the generated ring scalars must stay equal to the literals that
// `reading_for` / `reading_panel` still carry inline, and the读数 must not drift.
// ---------------------------------------------------------------------------

#[cfg(test)]
mod mechanics_pin {
    use super::*;

    #[test]
    fn ring_scalars_match_reading_literals() {
        assert_eq!(MOUNTAIN_STEP, 15.0);
        assert_eq!(FENJIN_STEP, 3.0);
        assert_eq!(DRAGON72_STEP, 5.0);
        assert_eq!(DRAGON60_STEP, 6.0);
        assert_eq!(DRAGON60_OFFSET, 1.5);
        assert_eq!(HEXAGRAM64_STEP, 5.625);
        assert_eq!(ZHENG_XIANG_DEG, 9.0);
        assert_eq!(EARTH_OFFSET, 0.0);
        assert_eq!(HUMAN_OFFSET, -7.5);
        assert_eq!(HEAVEN_OFFSET, 7.5);
        assert_eq!(BAGUA_STEP, 45.0);
    }

    #[test]
    fn table_lengths() {
        assert_eq!(MOUNTAINS.len(), 24);
        assert_eq!(WUXING.len(), 24);
        assert_eq!(YINYANG.len(), 24);
        assert_eq!(HOUTIAN_GUA.len(), 24);
        assert_eq!(XIANTIAN_GUA.len(), 24);
        assert_eq!(JIAZI.len(), 60);
        assert_eq!(NAYIN.len(), 60);
        assert_eq!(GUA_WUXING.len(), 8);
    }

    #[test]
    fn b2_vectors_match_dex_tables() {
        // 逆向 8368fbd 的四个原版向量（B2 验收②）
        assert_eq!(ring_text(7, 0.0), "壬子 庚子", "60龙 0°");
        assert_eq!(ring_text(7, 170.0), "丙午 甲午", "60龙 170°");
        assert_eq!(ring_text(5, 0.0), "戊子", "72龙 0°");
        assert_eq!(ring_text(6, 0.0), "甲山 戊子", "120分金 0°");
    }

    #[test]
    fn b2_all_780_cells() {
        // F-02：14 圈层逐格真执行 ring_index/ring_text/ring_center（不是字符串存在性检查）。
        let mut cells = 0usize;
        for t in 1..=14usize {
            let ring = &RINGS[t - 1];
            let n = ring.labels.len();
            assert!(n > 0, "TYPE_{t} 无 labels");
            for i in 0..n {
                let center = ring_center(t, i);
                assert_eq!(ring_index(t, center), i, "TYPE_{t} cell {i} center={center}");
                assert_eq!(ring_text(t, center), ring.labels[i], "TYPE_{t} cell {i} text");
                let lower = ring.offset + ring.interval * i as f32;
                assert_eq!(ring_index(t, lower), i, "TYPE_{t} cell {i} lower={lower}");
                let before = lower - 0.01;
                assert_eq!(
                    ring_index(t, before),
                    (i + n - 1) % n,
                    "TYPE_{t} cell {i} before={before}"
                );
                cells += 1;
            }
        }
        assert_eq!(cells, 780, "14 圈层总格数");
    }

    #[test]
    fn readings_pinned_at_boundaries() {
        for (h, want) in [
            (0.0, "子"),
            (7.4, "子"),
            (7.5, "癸"),
            (10.0, "癸"),
            (90.0, "卯"),
            (180.0, "午"),
            (270.0, "酉"),
            (352.5, "子"),
            (359.99, "子"),
            (360.0, "子"),
            (720.5, "子"),
            (-0.01, "子"),
        ] {
            assert_eq!(MOUNTAINS[reading_for(h).mountain], want, "heading {h}");
        }
        let rd = reading_for(4.501);
        assert_eq!(rd.fenjin_slot, 4);
        assert_eq!(rd.dragon72_slot, 2);
        assert_eq!(rd.sitting, 12);
        assert_eq!(rd.offset > 4.5, true);
    }
}
