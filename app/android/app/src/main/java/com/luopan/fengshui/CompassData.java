package com.luopan.fengshui;

/**
 * Feng Shui compass (罗盘) data and reading logic.
 *
 * Ring values come from the original app's own bundled knowledge base
 * (assets/compassIntroduce/*.html). The per-mountain 分金/龙 naming table is in the
 * original's encrypted DEX and is reconstructed here with the standard 三合盘 convention.
 */
public final class CompassData {

    /** 24 mountains clockwise from 子 (north = 0°). */
    public static final String[] MOUNTAINS = {
        "子","癸","丑","艮","寅","甲","卯","乙","辰","巽","巳","丙",
        "午","丁","未","坤","申","庚","酉","辛","戌","乾","亥","壬"
    };

    /** Five elements (五行) per mountain, aligned with MOUNTAINS. */
    public static final String[] WUXING = {
        "水","水","土","土","木","木","木","木","土","木","火","火",
        "火","火","土","土","金","金","金","金","土","金","水","水"
    };

    /** Yin/yang (三元盘) per mountain. */
    public static final String[] YINYANG = {
        "阴","阴","阴","阳","阳","阳","阴","阴","阴","阳","阳","阳",
        "阴","阴","阴","阳","阳","阳","阴","阴","阴","阳","阳","阳"
    };

    /** 后天八卦 per mountain. */
    public static final String[] HOUTIAN = {
        "坎","坎","艮","艮","艮","震","震","震","巽","巽","巽","离",
        "离","离","坤","坤","坤","兑","兑","兑","乾","乾","乾","坎"
    };

    /** 先天八卦 per mountain. */
    public static final String[] XIANTIAN = {
        "坤","坤","震","震","震","离","离","离","兑","兑","兑","乾",
        "乾","乾","巽","巽","巽","坎","坎","坎","艮","艮","艮","坤"
    };

    /** 60 Jiazi in order. */
    public static final String[] JIAZI = {
        "甲子","乙丑","丙寅","丁卯","戊辰","己巳","庚午","辛未","壬申","癸酉",
        "甲戌","乙亥","丙子","丁丑","戊寅","己卯","庚辰","辛巳","壬午","癸未",
        "甲申","乙酉","丙戌","丁亥","戊子","己丑","庚寅","辛卯","壬辰","癸巳",
        "甲午","乙未","丙申","丁酉","戊戌","己亥","庚子","辛丑","壬寅","癸卯",
        "甲辰","乙巳","丙午","丁未","戊申","己酉","庚戌","辛亥","壬子","癸丑",
        "甲寅","乙卯","丙辰","丁巳","戊午","己未","庚申","辛酉","壬戌","癸亥"
    };

    /** 纳音五行 per 甲子. */
    public static final String[] NAYIN = {
        "海中金","海中金","炉中火","炉中火","大林木","大林木","路旁土","路旁土","剑锋金","剑锋金",
        "山头火","山头火","涧下水","涧下水","城头土","城头土","白蜡金","白蜡金","杨柳木","杨柳木",
        "泉中水","泉中水","屋上土","屋上土","霹雳火","霹雳火","松柏木","松柏木","长流水","长流水",
        "沙中金","沙中金","山下火","山下火","平地木","平地木","壁上土","壁上土","金箔金","金箔金",
        "佛灯火","佛灯火","天河水","天河水","大驿土","大驿土","钗钏金","钗钏金","桑柘木","桑柘木",
        "大溪水","大溪水","沙中土","沙中土","天上火","天上火","石榴木","石榴木","大海水","大海水"
    };

    private static final String[] BRANCHES = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] YANG_STEMS = {"甲","丙","戊","庚","壬"};
    private static final String[] YIN_STEMS  = {"乙","丁","己","辛","癸"};

    public static final int WUXING_WATER = 0;
    public static final int WUXING_WOOD  = 1;
    public static final int WUXING_FIRE  = 2;
    public static final int WUXING_EARTH = 3;
    public static final int WUXING_METAL = 4;

    private CompassData() {}

    public static int wuxingIndex(String wx) {
        switch (wx) {
            case "水": return WUXING_WATER;
            case "木": return WUXING_WOOD;
            case "火": return WUXING_FIRE;
            case "土": return WUXING_EARTH;
            case "金": return WUXING_METAL;
            default: return -1;
        }
    }

    private static float norm(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }

    private static int borrowBranch(int m) {
        String s = MOUNTAINS[m];
        if (s.equals("子") || s.equals("癸")) return 0;
        if (s.equals("丑") || s.equals("艮")) return 1;
        if (s.equals("寅") || s.equals("甲")) return 2;
        if (s.equals("卯")) return 3;
        if (s.equals("辰") || s.equals("乙") || s.equals("巽")) return 4;
        if (s.equals("巳") || s.equals("丙")) return 5;
        if (s.equals("午")) return 6;
        if (s.equals("未") || s.equals("丁") || s.equals("坤")) return 7;
        if (s.equals("申") || s.equals("庚")) return 8;
        if (s.equals("酉")) return 9;
        if (s.equals("戌") || s.equals("辛") || s.equals("乾")) return 10;
        return 11; // 亥 / 壬
    }

    private static String wangxiang(int branch, int slot) {
        String[] stems = (branch % 2 == 0) ? YANG_STEMS : YIN_STEMS;
        String target = stems[slot % 5] + BRANCHES[branch];
        for (int i = 0; i < JIAZI.length; i++) {
            if (JIAZI[i].equals(target)) return JIAZI[i];
        }
        return "甲子";
    }

    private static String nayinOf(String jiazi) {
        for (int i = 0; i < JIAZI.length; i++) {
            if (JIAZI[i].equals(jiazi)) return NAYIN[i];
        }
        return "";
    }

    /** A single compass reading for a heading. */
    public static final class Reading {
        public final float heading;
        public final int mountain;      // 0..23 on 地盘正针
        public final float offset;      // degrees from the mountain center, −7.5..7.5
        public final int fenjinSlot;    // 0..4
        public final int dragon72Slot;  // 0..2
        public final int dragon60;      // 0..59
        public final int sitting;       // opposite mountain
        public final String fenjin;
        public final String dragon72;
        public final String dragon60Name;
        public final String fenjinNayin;
        public final String dragon72Nayin;
        public final String dragon60Nayin;
        public final String jian;       // 正向 / 兼向…

        Reading(float heading) {
            this.heading = norm(heading);
            float h = this.heading;
            int m = (int) Math.floor(norm(h + 7.5f) / 15f) % 24;
            this.mountain = m;
            float center = m * 15f;
            this.offset = norm(h - center + 180f) - 180f;
            float fromStart = norm(h - (center - 7.5f));
            this.fenjinSlot = Math.min((int) Math.floor(fromStart / 3f), 4);
            this.dragon72Slot = Math.min((int) Math.floor(fromStart / 5f), 2);
            this.dragon60 = (int) Math.floor(((h - 1.5f + 360f) % 360f) / 6f) % 60;
            this.sitting = (m + 12) % 24;

            this.fenjin = wangxiang(borrowBranch(m), fenjinSlot);
            this.dragon72 = wangxiang(borrowBranch(m), dragon72Slot);
            this.dragon60Name = JIAZI[dragon60];
            this.fenjinNayin = nayinOf(this.fenjin);
            this.dragon72Nayin = nayinOf(this.dragon72);
            this.dragon60Nayin = nayinOf(this.dragon60Name);

            if (Math.abs(offset) * 2f <= 9f) {
                this.jian = "正向";
            } else {
                int n = offset < 0 ? (m + 23) % 24 : (m + 1) % 24;
                this.jian = "兼向" + MOUNTAINS[n];
            }
        }
    }

    public static Reading read(float heading) {
        return new Reading(heading);
    }

    public static final String[] KNOWLEDGE_TITLES = {
        "二十四山", "三盘", "百二十分金", "穿山七十二龙",
        "透地平分六十龙", "正兼向", "八卦", "五行"
    };

    public static final String[] KNOWLEDGE_BODIES = {
        "十二地支＋八干（甲乙丙丁庚辛壬癸）＋四维（乾坤艮巽），共二十四山，每山占15度，四正位：子北、午南、卯东、酉西。",
        "地盘正针为基准；人盘中针逆时针偏7.5度；天盘缝针顺时针偏7.5度，用于消砂纳水。",
        "圆周120等份，每份3度，每山5份，以六十甲子循环命名。",
        "圆周72等份，每份5度，每山3龙，用于确定来龙干支。",
        "圆周60等份，每份6度，以六十甲子命名，用于确定龙脉入首。",
        "正向为每山15度范围；兼向为越过山界约3–7度的邻山过渡区。",
        "先天八卦（乾坤定南北）与后天八卦（坎离定南北）两种方位体系。",
        "天干：甲乙木 丙丁火 庚辛金 壬癸水；地支：子亥水 寅卯木 巳午火 申酉金 辰戌丑未土。"
    };

    /** 5 compass dials (from CommonCompassEnum, decompiled) — all free, no paywall. */
    public static final String[] COMPASS_TYPES = {
        "二十四山(阴)", "常用罗盘", "三合罗盘(白)", "三元罗盘(金)", "综合罗盘(白)"
    };

    /** 14 ring/reading types (from CompassReadTypeEnum, decompiled). */
    public static final String[] READ_TYPES = {
        "先天八卦_卦位 45°", "后天八卦_卦位 45°", "先天八卦洛书数 45°",
        "地盘正针二十四山 15°", "穿山七十二龙 5°", "地盘正针百二十分金 3°",
        "透地平分六十龙 6°", "人盘中针二十四山 15°", "天盘缝针二十四山 15°",
        "天盘缝针百二十分金 3°", "先天方图六十四卦内卦 5.625°", "先天圆图六十四卦外卦 5.625°",
        "六十甲配六十四卦五行 5.625°", "正兼向度数指标 3°"
    };

    /** Degrees per 64-hexagram division (360/64). */
    public static final float HEXAGRAM64_DEG = 5.625f;
}
