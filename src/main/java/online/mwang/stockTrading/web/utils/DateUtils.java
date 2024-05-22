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

    public static Date getNextDay(Date date, int amount) {
        if (date == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, amount);
        return calendar.getTime();
    }

    public static Date getNextTradingDay(Date date) {
        if (date == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 1);
        while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            calendar.add(Calendar.DATE, 1);
        }
        return calendar.getTime();
    }


    public static long diff(Date date1, Date date2) {
        long diffInMillis = date1.getTime() - date2.getTime();
        return diffInMillis / (24 * 60 * 60 * 1000);
    }

    public static String timeConvertor(long millis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        String res = minutes + "分钟 " + seconds + "秒";
        if (hours > 0) res = hours + "小时 " + res;
        return res;
    }

    public static Boolean isDeadLine() {
        return isDeadLine1() || isDeadLine2();
    }

    // 上午交易时间段即将结束(9:30-11:30)
    public static Boolean isDeadLine1() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final int hours = calendar.get(Calendar.HOUR_OF_DAY);
        final int minutes = calendar.get(Calendar.MINUTE);
        return hours == 11 && minutes >= 20 && minutes <= 30;
    }

    // 下午交易时间段即将结束(13:00-15:00)
    public static Boolean isDeadLine2() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final int hours = calendar.get(Calendar.HOUR_OF_DAY);
        final int minutes = calendar.get(Calendar.MINUTE);
        return hours == 14 && minutes >= 50;
    }

    public static Boolean inTradingTimes1() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("09:30") >= 0 && format.compareTo("11:30") <= 0;
    }

    public static Boolean inTradingTimes2() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("13:00") >= 0 && format.compareTo("15:00") <= 0;
    }

    public static boolean isWeekends(Date date) {
        if (date == null) return false;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int weekday = calendar.get(Calendar.DAY_OF_WEEK);
        return weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY;
    }
}
