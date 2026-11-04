package com.luopan.fengshui;

/**
 * Feng Shui compass (罗盘) data and reading logic.
 *
 * Ring values come from the original app's own bundled knowledge base
 * (assets/compassIntroduce/*.html). The per-mountain 分金/龙 naming table is in the
 * original's encrypted DEX and is reconstructed here with the standard 三合盘 convention.
 */
public final class CompassData {

    // >>> BEGIN GENERATED (app/mechanics.json) >>>
    // Source: app/mechanics.json (sha256: 984c5fb618634b0a1202908243acfa1dfbd3d1e4efa0b34df2a778e0d31f34ca). Regenerate: tools/mechanics/gen-mechanics.ps1
    // Hand edits inside the markers are rejected by gen-mechanics.ps1 -Check.
    public static final String[] MOUNTAINS = {
        "子", "癸", "丑", "艮", "寅", "甲",
        "卯", "乙", "辰", "巽", "巳", "丙",
        "午", "丁", "未", "坤", "申", "庚",
        "酉", "辛", "戌", "乾", "亥", "壬"
    };
    public static final String[] WUXING = {
        "水", "水", "土", "土", "木", "木",
        "木", "木", "土", "木", "火", "火",
        "火", "火", "土", "土", "金", "金",
        "金", "金", "土", "金", "水", "水"
    };
    public static final String[] YINYANG = {
        "阴", "阴", "阴", "阳", "阳", "阳",
        "阴", "阴", "阴", "阳", "阳", "阳",
        "阴", "阴", "阴", "阳", "阳", "阳",
        "阴", "阴", "阴", "阳", "阳", "阳"
    };
    public static final String[] HOUTIAN = {
        "坎", "坎", "艮", "艮", "艮", "震",
        "震", "震", "巽", "巽", "巽", "离",
        "离", "离", "坤", "坤", "坤", "兑",
        "兑", "兑", "乾", "乾", "乾", "坎"
    };
    public static final String[] XIANTIAN = {
        "坤", "坤", "震", "震", "震", "离",
        "离", "离", "兑", "兑", "兑", "乾",
        "乾", "乾", "巽", "巽", "巽", "坎",
        "坎", "坎", "艮", "艮", "艮", "坤"
    };
    public static final String[] JIAZI = {
        "甲子", "乙丑", "丙寅", "丁卯", "戊辰", "己巳", "庚午", "辛未", "壬申", "癸酉",
        "甲戌", "乙亥", "丙子", "丁丑", "戊寅", "己卯", "庚辰", "辛巳", "壬午", "癸未",
        "甲申", "乙酉", "丙戌", "丁亥", "戊子", "己丑", "庚寅", "辛卯", "壬辰", "癸巳",
        "甲午", "乙未", "丙申", "丁酉", "戊戌", "己亥", "庚子", "辛丑", "壬寅", "癸卯",
        "甲辰", "乙巳", "丙午", "丁未", "戊申", "己酉", "庚戌", "辛亥", "壬子", "癸丑",
        "甲寅", "乙卯", "丙辰", "丁巳", "戊午", "己未", "庚申", "辛酉", "壬戌", "癸亥"
    };
    public static final String[] NAYIN = {
        "海中金", "海中金", "炉中火", "炉中火", "大林木", "大林木", "路旁土", "路旁土", "剑锋金", "剑锋金",
        "山头火", "山头火", "涧下水", "涧下水", "城头土", "城头土", "白蜡金", "白蜡金", "杨柳木", "杨柳木",
        "泉中水", "泉中水", "屋上土", "屋上土", "霹雳火", "霹雳火", "松柏木", "松柏木", "长流水", "长流水",
        "沙中金", "沙中金", "山下火", "山下火", "平地木", "平地木", "壁上土", "壁上土", "金箔金", "金箔金",
        "佛灯火", "佛灯火", "天河水", "天河水", "大驿土", "大驿土", "钗钏金", "钗钏金", "桑柘木", "桑柘木",
        "大溪水", "大溪水", "沙中土", "沙中土", "天上火", "天上火", "石榴木", "石榴木", "大海水", "大海水"
    };
    private static final String[] BRANCHES = {
        "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    };
    private static final String[] YANG_STEMS = {
        "甲", "丙", "戊", "庚", "壬"
    };
    private static final String[] YIN_STEMS = {
        "乙", "丁", "己", "辛", "癸"
    };
    public static final String[] COMPASS_TYPES = {
        "二十四山(阴)", "常用罗盘", "三合罗盘(白)",
        "三元罗盘(金)", "综合罗盘(白)"
    };
    public static final String[] READ_TYPES = {
        "先天八卦_卦位 45°", "后天八卦_卦位 45°",
        "先天八卦洛书数 45°", "地盘正针二十四山 15°",
        "穿山七十二龙 5°", "地盘正针百二十分金 3°",
        "透地平分六十龙 6°", "人盘中针二十四山 15°",
        "天盘缝针二十四山 15°", "天盘缝针百二十分金 3°",
        "先天方图六十四卦内卦 5.625°", "先天圆图六十四卦外卦 5.625°",
        "六十甲配六十四卦五行 5.625°", "正兼向度数指标 3°"
    };
    public static final class RingDef {
        public final String id;
        public final String text;
        public final float interval;
        public final float offset;
        public final String[] labels;
        RingDef(String id, String text, float interval, float offset, String[] labels) {
            this.id = id; this.text = text; this.interval = interval;
            this.offset = offset; this.labels = labels;
        }
    }
    public static final RingDef[] RINGS = {
        new RingDef("TYPE_1", "先天八卦_卦位", 45f, 22.5f, new String[]{"‌震", "‌离", "‌兑", "乾", "‌巽", "‌坎", "‌艮", "‌坤"}),
        new RingDef("TYPE_2", "后天八卦_卦位", 45f, 22.5f, new String[]{"坤", "兑七", "乾六", "离", "艮八", "震三", "‌巽四", "‌坎"}),
        new RingDef("TYPE_3", "先天八卦洛书数", 45f, 22.5f, new String[]{"‌震 4", "‌离 3", "‌兑 2", "乾 1", "‌巽 5", "‌坎 6", "‌艮 7", "‌坤 8"}),
        new RingDef("TYPE_4", "地盘正针二十四山", 15f, 7.5f, new String[]{"癸山", "丑山", "艮山", "寅山", "甲山", "卯山", "乙山", "辰山", "巽山", "巳山", "丙山", "午山", "丁山", "未山", "坤山", "申山", "庚山", "酉山", "辛山", "戌山", "乾山", "亥山", "壬山", "子山"}),
        new RingDef("TYPE_5", "穿山七十二龙", 5f, 2.5f, new String[]{"庚子", "壬子", "正癸", "乙丑", "丁丑", "己丑", "辛丑", "癸丑", "正艮", "丙寅", "戌寅", "庚寅", "壬寅", "甲寅", "正甲", "丁卯", "己卯", "辛卯", "癸卯", "乙卯", "正乙", "戌辰", "庚辰", "壬辰", "甲辰", "丙辰", "正巽", "己巳", "辛巳", "癸巳", "乙巳", "丁巳", "正丙", "庚午", "壬午", "甲午", "丙午", "戌午", "正丁", "辛未", "癸未", "乙未", "丁未", "巳未", "正坤", "壬坤", "甲坤", "丙坤", "戌坤", "庚坤", "正庚", "癸酉", "乙酉", "丁酉", "己酉", "辛酉", "正辛", "甲戌", "丙戌", "戊戌", "庚戌", "壬戌", "正乾", "乙亥", "丁亥", "己亥", "辛亥", "癸亥", "正壬", "甲子", "丙子", "戊子"}),
        new RingDef("TYPE_6", "地盘正针百二十分金", 3f, 1.5f, new String[]{"甲山 庚子", "甲山 壬子", "癸山 甲子", "癸山 丙子", "癸山 戊子", "癸山 庚子", "癸山 壬子", "丑山 乙丑", "丑山 丁丑", "丑山 己丑", "丑山 辛丑", "丑山 癸丑", "艮山 乙丑", "艮山 丁丑", "艮山 己丑", "艮山 辛丑", "艮山 癸丑", "寅山 甲寅", "寅山 丙寅", "寅山 戊寅", "寅山 庚寅", "寅山 壬寅", "甲山 甲寅", "甲山 丙寅", "甲山 戊寅", "甲山 庚寅", "甲山 壬寅", "卯山 乙卯", "卯山 丁卯", "卯山 己卯", "卯山 辛卯", "卯山 癸卯", "已山 乙卯", "已山 丁卯", "已山 己卯", "已山 辛卯", "已山 癸卯", "辰山 甲辰", "辰山 丙辰", "辰山 戊辰", "辰山 庚辰", "辰山 壬辰", "巽山 甲辰", "巽山 丙辰", "巽山 戊辰", "巽山 庚辰", "巽山 壬辰", "巳山 乙巳", "巳山 丁巳", "巳山 己巳", "巳山 辛巳", "巳山 癸巳", "丙山 乙巳", "丙山 丁巳", "丙山 己巳", "丙山 辛巳", "丙山 癸巳", "午山 甲午", "午山 丙午", "午山 戊午", "午山 庚午", "午山 壬午", "丁山 甲午", "丁山 丙午", "丁山 戊午", "丁山 庚午", "丁山 壬午", "未山 乙未", "未山 丁未", "未山 己未", "未山 辛未", "未山 癸未", "坤山 乙未", "坤山 丁未", "坤山 己未", "坤山 辛未", "坤山 癸未", "申山 甲申", "申山 丙申", "申山 戊申", "申山 庚申", "申山 壬申", "庚山 甲申", "庚山 丙申", "庚山 戊申", "庚山 庚申", "庚山 壬申", "酉山 乙酉", "酉山 丁酉", "酉山 己酉", "酉山 辛酉", "酉山 癸酉", "辛山 乙酉", "辛山 丁酉", "辛山 己酉", "辛山 辛酉", "辛山 癸酉", "戌山 甲戌", "戌山 丙戌", "戌山 戊戌", "戌山 庚戌", "戌山 壬戌", "乾山 甲戌", "乾山 丙戌", "乾山 戊戌", "乾山 庚戌", "乾山 壬戌", "亥山 乙亥", "亥山 丁亥", "亥山 己亥", "亥山 辛亥", "亥山 癸亥", "壬山 乙亥", "壬山 丁亥", "壬山 己亥", "壬山 辛亥", "壬山 癸亥", "甲山 甲子", "甲山 丙子", "甲山 戊子"}),
        new RingDef("TYPE_7", "透地平分六十龙", 6f, 1.5f, new String[]{"壬子 壬子", "癸丑 乙丑", "癸丑 丁丑", "癸丑 己丑", "癸丑 辛丑", "癸丑 癸丑", "艮寅 丙寅", "艮寅 戊寅", "艮寅 庚寅", "艮寅 壬寅", "艮寅 甲寅", "甲卯 丁卯", "甲卯 己卯", "甲卯 辛卯", "甲卯 癸卯", "甲卯 乙卯", "乙辰 戊辰", "乙辰 庚辰", "乙辰 壬辰", "乙辰 甲辰", "乙辰 丙辰", "巽巳 己巳", "巽巳 辛巳", "巽巳 癸巳", "巽巳 乙巳", "巽巳 丁巳", "丙午 庚午", "丙午 壬午", "丙午 甲午", "丙午 丙午", "丙午 戊午", "丁未 辛未", "丁未 癸未", "丁未 乙未", "丁未 丁未", "丁未 己未", "坤申 壬申", "坤申 甲申", "坤申 丙申", "坤申 戊申", "坤申 庚申", "庚酉 癸酉", "庚酉 乙酉", "庚酉 丁酉", "庚酉 己酉", "庚酉 辛酉", "辛戌 甲戌", "辛戌 丙戌", "辛戌 戊戌", "辛戌 庚戌", "辛戌 壬戌", "乾亥 乙亥", "乾亥 丁亥", "乾亥 己亥", "乾亥 辛亥", "乾亥 癸亥", "壬子 甲子", "壬子 丙子", "壬子 戊子", "壬子 庚子"}),
        new RingDef("TYPE_8", "人盘中针二十四山", 15f, 0f, new String[]{"癸山", "丑山", "艮山", "寅山", "甲山", "卯山", "乙山", "辰山", "巽山", "巳山", "丙山", "午山", "丁山", "未山", "坤山", "申山", "庚山", "酉山", "辛山", "戌山", "乾山", "亥山", "壬山", "子山"}),
        new RingDef("TYPE_9", "天盘缝针二十四山", 15f, 0f, new String[]{"子山", "癸山", "丑山", "艮山", "寅山", "甲山", "卯山", "乙山", "辰山", "巽山", "巳山", "丙山", "午山", "丁山", "未山", "坤山", "申山", "庚山", "酉山", "辛山", "戌山", "乾山", "亥山", "壬山"}),
        new RingDef("TYPE_10", "天盘缝针百二十分金", 3f, 0f, new String[]{"甲山甲子", "甲山丙子", "甲山戊子", "甲山庚子", "甲山壬子", "癸山甲子", "癸山丙子", "癸山戊子", "癸山庚子", "癸山壬子", "丑山乙丑", "丑山丁丑", "丑山己丑", "丑山辛丑", "丑山癸丑", "艮山乙丑", "艮山丁丑", "艮山己丑", "艮山辛丑", "艮山癸丑", "寅山甲寅", "寅山丙寅", "寅山戊寅", "寅山庚寅", "寅山壬寅", "甲山甲寅", "甲山丙寅", "甲山戊寅", "甲山庚寅", "甲山壬寅", "卯山乙卯", "卯山丁卯", "卯山己卯", "卯山辛卯", "卯山癸卯", "已山乙卯", "已山丁卯", "已山己卯", "已山辛卯", "已山癸卯", "辰山甲辰", "辰山丙辰", "辰山戊辰", "辰山庚辰", "辰山壬辰", "巽山甲辰", "巽山丙辰", "巽山戊辰", "巽山庚辰", "巽山壬辰", "巳山乙巳", "巳山丁巳", "巳山己巳", "巳山辛巳", "巳山癸巳", "丙山乙巳", "丙山丁巳", "丙山己巳", "丙山辛巳", "丙山癸巳", "午山甲午", "午山丙午", "午山戊午", "午山庚午", "午山壬午", "丁山甲午", "丁山丙午", "丁山戊午", "丁山庚午", "丁山壬午", "未山乙未", "未山丁未", "未山己未", "未山辛未", "未山癸未", "坤山乙未", "坤山丁未", "坤山己未", "坤山辛未", "坤山癸未", "申山甲申", "申山丙申", "申山戊申", "申山庚申", "申山壬申", "庚山甲申", "庚山丙申", "庚山戊申", "庚山庚申", "庚山壬申", "酉山乙酉", "酉山丁酉", "酉山己酉", "酉山辛酉", "酉山癸酉", "辛山乙酉", "辛山丁酉", "辛山己酉", "辛山辛酉", "辛山癸酉", "戌山甲戌", "戌山丙戌", "戌山戊戌", "戌山庚戌", "戌山壬戌", "乾山甲戌", "乾山丙戌", "乾山戊戌", "乾山庚戌", "乾山壬戌", "亥山乙亥", "亥山丁亥", "亥山己亥", "亥山辛亥", "亥山癸亥", "壬山乙亥", "壬山丁亥", "壬山己亥", "壬山辛亥", "壬山癸亥"}),
        new RingDef("TYPE_11", "先天方图六十四卦内卦", 5.625f, 0f, new String[]{"乾小畜", "兑中孚", "离家人", "震益", "巽巽", "坎涣", "艮渐", "坤观", "乾需", "兑节", "离既济", "震屯", "巽井", "坎坎", "艮蹇", "坤比", "乾大畜", "兑损", "离贲", "震颐", "巽蛊", "坎蒙", "艮艮", "坤剥", "乾泰", "兑临", "离明夷", "震复", "巽升", "坎师", "艮谦", "坤坤", "坤豫", "艮小过", "坎解", "巽恒", "震震", "离丰", "兑归妹", "乾大壮", "坤晋", "艮旅", "坎未济", "巽鼎", "震噬嗑", "离离", "兑睽", "乾大有", "坤萃", "艮咸", "坎困", "巽大过", "震随", "离革", "兑兑", "乾夬", "坤否", "艮遁", "坎讼", "巽姤", "震无妄", "离同人", "兑履", "乾乾"}),
        new RingDef("TYPE_12", "先天圆图六十四卦外卦", 5.625f, 0f, new String[]{"坤复一", "艮颐六", "坎屯七", "巽益二", "震震八", "离噬嗑三", "兑随四", "乾无妄九", "坤明夷一", "艮贲六", "坎既济七", "巽家人二", "震丰八", "离离三", "兑革四", "乾同人九", "坤临一", "艮损六", "坎节七", "巽中孚二", "震归妹八", "离睽三", "兑兑四", "乾履九", "坤泰一", "艮大畜六", "坎需七", "巽小畜二", "震大壮八", "离大有三", "兑夬四", "乾乾九", "乾姤九", "兑大过四", "离鼎三", "震恒八", "巽巽二", "坎井七", "艮蛊六", "坤升一", "乾讼九", "兑困四", "离未济三", "震解八", "巽涣二", "坎坎七", "艮蒙六", "坤师一", "乾遁九", "兑咸四", "离旅三", "震小过八", "巽渐二", "坎蹇七", "艮艮六", "坤谦一", "乾否九", "兑萃四", "离晋三", "震豫八", "巽观二", "坎比七", "艮剥六", "坤坤一"}),
        new RingDef("TYPE_13", "六十甲配六十四卦五行", 5.625f, 0f, new String[]{"复甲子金", "颐丙子水", "屯戊子火", "益庚子土", "震壬子木", "噬嗑乙丑金", "随丁丑水", "无妄己丑火", "明夷辛丑土", "贲癸丑木", "既济甲寅水", "家人丙寅火", "丰戊寅土", "离庚寅木", "革庚寅木", "同人壬寅金", "临乙卯水", "损丁卯火", "节己卯土", "中孚辛卯木", "归妹癸卯金", "睽甲辰水", "兑丙辰土", "履戊辰木", "泰庚辰金", "大畜壬辰水", "需乙巳火", "小畜丁巳土", "大壮己巳木", "大有辛巳金", "夬癸巳水", "乾甲午金", "姤甲午金", "大过丙午水", "鼎戊午火", "恒庚午土", "巽壬午木", "井乙未金", "蛊丁未水", "升己未火", "讼辛未土", "困癸未木", "未济甲申水", "解丙申火", "涣戊申土", "坎庚申木", "蒙庚申木", "师壬申金", "遁乙酉水", "咸丁酉火", "旅己酉土", "小过辛酉木", "渐癸酉金", "蹇甲戌火", "艮丙戌土", "谦戊戌木", "否庚戌金", "萃壬戌水", "晋乙亥火", "豫丁亥土", "观己亥木", "比辛亥金", "剥癸亥水", "坤甲子金"}),
        new RingDef("TYPE_14", "正兼向度数指标", 3f, 0f, new String[]{"癸山九度外", "癸山九度内", "癸山九度内", "癸山九度内", "癸山九度外", "丑山九度外", "丑山九度内", "丑山九度内", "丑山九度内", "丑山九度外", "艮山九度外", "艮山九度内", "艮山九度内", "艮山九度内", "艮山九度外", "寅山九度外", "寅山九度内", "寅山九度内", "寅山九度内", "寅山九度外", "甲山九度外", "甲山九度内", "甲山九度内", "甲山九度内", "甲山九度外", "卯山九度外", "卯山九度内", "卯山九度内", "卯山九度内", "卯山九度外", "乙山九度外", "乙山九度内", "乙山九度内", "乙山九度内", "乙山九度外", "辰山九度外", "辰山九度内", "辰山九度内", "辰山九度内", "辰山九度外", "巽山九度外", "巽山九度内", "巽山九度内", "巽山九度内", "巽山九度外", "巳山九度外", "巳山九度内", "巳山九度内", "巳山九度内", "巳山九度外", "丙山九度外", "丙山九度内", "丙山九度内", "丙山九度内", "丙山九度外", "午山九度外", "午山九度内", "午山九度内", "午山九度内", "午山九度外", "丁山九度外", "丁山九度内", "丁山九度内", "丁山九度内", "丁山九度外", "未山九度外", "未山九度内", "未山九度内", "未山九度内", "未山九度外", "坤山九度外", "坤山九度内", "坤山九度内", "坤山九度内", "坤山九度外", "申山九度外", "申山九度内", "申山九度内", "申山九度内", "申山九度外", "庚山九度外", "庚山九度内", "庚山九度内", "庚山九度内", "庚山九度外", "酉山九度外", "酉山九度内", "酉山九度内", "酉山九度内", "酉山九度外", "辛山九度外", "辛山九度内", "辛山九度内", "辛山九度内", "辛山九度外", "戌山九度外", "戌山九度内", "戌山九度内", "戌山九度内", "戌山九度外", "乾山九度外", "乾山九度内", "乾山九度内", "乾山九度内", "乾山九度外", "亥山九度外", "亥山九度内", "亥山九度内", "亥山九度内", "亥山九度外", "壬山九度外", "壬山九度内", "壬山九度内", "壬山九度内", "壬山九度外", "子山九度外", "子山九度内", "子山九度内", "子山九度内", "子山九度外"}),
    };
    // Ring scalars (values[] / rings[]).
    public static final float HEXAGRAM64_DEG = 5.625f;
    static final float MOUNTAIN_STEP = 15f;
    static final float MOUNTAIN_HALF = 7.5f;
    static final float EARTH_OFFSET = 0f;
    static final float HUMAN_OFFSET = -7.5f;
    static final float HEAVEN_OFFSET = 7.5f;
    static final float FENJIN_STEP = 3f;
    static final float DRAGON72_STEP = 5f;
    static final float DRAGON60_STEP = 6f;
    static final float DRAGON60_OFFSET = 1.5f;
    static final float BAGUA_STEP = 45f;
    static final float ZHENG_XIANG_DEG = 9f;
    // <<< END GENERATED <<<

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
        // B2 table texts (mechanics.json rings[].labels), no derivation
        public final String fenjinText;
        public final String dragon72Text;
        public final String dragon60Text;
        public final String jianText;
        public final String houtianText;
        public final String xiantianText;

        Reading(float heading) {
            this.heading = norm(heading);
            float h = this.heading;
            int m = (int) Math.floor(norm(h + MOUNTAIN_HALF) / MOUNTAIN_STEP) % 24;
            this.mountain = m;
            float center = m * MOUNTAIN_STEP;
            this.offset = norm(h - center + 180f) - 180f;
            float fromStart = norm(h - (center - MOUNTAIN_HALF));
            this.fenjinSlot = Math.min((int) Math.floor(fromStart / FENJIN_STEP), 4);
            this.dragon72Slot = Math.min((int) Math.floor(fromStart / DRAGON72_STEP), 2);
            this.dragon60 = (int) Math.floor(((h - DRAGON60_OFFSET + 360f) % 360f) / DRAGON60_STEP) % 60;
            this.sitting = (m + 12) % 24;

            this.fenjin = wangxiang(borrowBranch(m), fenjinSlot);
            this.dragon72 = wangxiang(borrowBranch(m), dragon72Slot);
            this.dragon60Name = JIAZI[dragon60];
            this.fenjinNayin = nayinOf(this.fenjin);
            this.dragon72Nayin = nayinOf(this.dragon72);
            this.dragon60Nayin = nayinOf(this.dragon60Name);

            this.fenjinText = ringText(6, h);
            this.dragon72Text = ringText(5, h);
            this.dragon60Text = ringText(7, h);
            this.jianText = ringText(14, h);
            this.houtianText = ringText(2, h);
            this.xiantianText = ringText(1, h);

            if (Math.abs(offset) * 2f <= ZHENG_XIANG_DEG) {
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

    /** B2: text of RINGS[type-1] for this heading, straight from mechanics.json. */
    public static String ringText(int type, float heading) {
        return RINGS[type - 1].labels[ringIndex(type, heading)];
    }

    /** B2: cell index under the pointer, floor(((h-offset+360)%360)/interval). */
    public static int ringIndex(int type, float heading) {
        RingDef r = RINGS[type - 1];
        double h = ((heading - r.offset) % 360.0 + 360.0) % 360.0;
        return ((int) Math.floor(h / r.interval)) % r.labels.length;
    }

    /** B2: cell centre = offset + (i + 0.5) * interval. */
    public static float ringCenter(int type, int index) {
        RingDef r = RINGS[type - 1];
        return r.offset + (index + 0.5f) * r.interval;
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

}
