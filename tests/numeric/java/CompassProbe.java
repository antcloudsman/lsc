package com.luopan.fengshui;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 测试探针：直接调用 Android 端真实实现 `CompassData.read(float)`，
 * 把每个航向的读数以 CSV 输出，供 tests/numeric 与 oracle 对拍。
 *
 * 用法：java -cp <classes> com.luopan.fengshui.CompassProbe headings.txt out.csv
 * 注意：只读，不改 CompassData；本文件不进入 APK。
 */
public final class CompassProbe {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: CompassProbe <headings.txt> <out.csv>");
            System.exit(2);
        }
        try (BufferedReader in = new BufferedReader(
                     new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(new FileOutputStream(args[1]), StandardCharsets.UTF_8))) {
            out.println("h,m,off,fs,d72,d60,sit,jian,fenjin,d72name,d60name");
            String line;
            while ((line = in.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty()) {
                    continue;
                }
                float h = Float.parseFloat(s);
                CompassData.Reading r = CompassData.read(h);
                out.printf(Locale.ROOT, "%s,%d,%.6f,%d,%d,%d,%d,%s,%s,%s,%s%n",
                        s, r.mountain, r.offset, r.fenjinSlot, r.dragon72Slot, r.dragon60,
                        r.sitting, r.jian, r.fenjin, r.dragon72, r.dragon60Name);
            }
        }
    }
}
