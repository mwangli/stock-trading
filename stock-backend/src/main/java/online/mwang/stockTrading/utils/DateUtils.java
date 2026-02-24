package online.mwang.stockTrading.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日期工具类
 */
public class DateUtils {

    public static final String FORMAT_1 = "yyyyMMdd";
    public static final String FORMAT_2 = "yyyy-MM-dd";
    public static final String FORMAT_3 = "yyyy-MM-dd HH:mm:ss";

    public static String format1(Date date) {
        return new SimpleDateFormat(FORMAT_1).format(date);
    }

    public static String format2(Date date) {
        return new SimpleDateFormat(FORMAT_2).format(date);
    }

    public static String format3(Date date) {
        return new SimpleDateFormat(FORMAT_3).format(date);
    }

    public static Date parse1(String dateStr) {
        try {
            return new SimpleDateFormat(FORMAT_1).parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}
