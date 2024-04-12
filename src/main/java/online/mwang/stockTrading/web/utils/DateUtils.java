package online.mwang.stockTrading.web.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class DateUtils {

    public static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    public static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    public static final SimpleDateFormat timeFormat2 = new SimpleDateFormat("MM-dd HH:mm");

    public static String format1(Date date) {
        return date == null ? "" : dateFormat.format(date);
    }

    public static String format2(Date date) {
        return date == null ? "" : timeFormat2.format(date);
    }

    public static Date getNextDay(Date date) {
        if (date == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 1);
        return calendar.getTime();
    }

    public static String camelToUnderline(String str) {
        if (str == null || "".equals(str.trim())) {
            return "";
        }
        int len = str.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append("_").append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String timeConvertor(long millis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        return hours + "小时 " + minutes + "分钟 " + seconds + "秒";
    }

    public static Boolean isDeadLine1() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final int hours = calendar.get(Calendar.HOUR_OF_DAY);
        final int minutes = calendar.get(Calendar.MINUTE);
        return hours >= 11 && minutes >= 20;
    }

    public static Boolean isDeadLine2() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final int hours = calendar.get(Calendar.HOUR_OF_DAY);
        final int minutes = calendar.get(Calendar.MINUTE);
        return hours >= 14 && minutes >= 50;
    }

    public static Boolean inTradingTimes1() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("09:30") >= 0 && format.compareTo("11:30") <= 0;
    }

    public static Boolean inTradingTimes2() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("13:00") >= 0 && format.compareTo("15:00") <= 0;
    }


}
