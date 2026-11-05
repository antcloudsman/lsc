import java.util.Locale;

/**
 * F-16 对拍探针：按 App 的真实路径生成读盘顶部两行（float32 语义）。
 *
 * 与 CompassReadSheet.java L27-L29 / L47-L49 同法：
 *   float h    = SensorMath.normalize(raw);
 *   float back = SensorMath.normalize(raw + 180f);
 *   String.format(Locale.US, "向北 %.1f°    坐南 %.1f°", h, back)
 *
 * 输出 36000 行（0.00°…359.99°，0.01° 网格），TAB 分隔：
 *   i <TAB> h <TAB> back <TAB> head1
 *
 * 用法（tests/at/verify_readsheet_fmt.py 会自动编译并调用）：
 *   javac ReadSheetFmtProbe.java -d <tmp> && java -cp <tmp> ReadSheetFmtProbe
 */
public final class ReadSheetFmtProbe {

    private static float normalize(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder(36000 * 48);
        for (int i = 0; i < 36000; i++) {
            float raw = i / 100f;
            float h = normalize(raw);
            float back = normalize(raw + 180f);
            sb.append(i).append('\t')
              .append(Float.toString(h)).append('\t')
              .append(Float.toString(back)).append('\t')
              .append(String.format(Locale.US, "向北 %.1f°    坐南 %.1f°", h, back))
              .append('\n');
        }
        System.out.print(sb);
    }
}
