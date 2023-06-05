package online.mwang.foundtrading.utils;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {

    public static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

    public static void main(String[] args) throws Exception {
        StringBuilder stringBuffer = new StringBuilder();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MONTH, -1);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        int count = 1;
        while (count <= 1000) {
            int weekDay = calendar.get(Calendar.DAY_OF_WEEK);
            Date date = calendar.getTime();
            String format = simpleDateFormat.format(date);
//            if (weekDay == 1 || weekDay == 7) {
//                System.out.println(weekDay);
//                System.out.println(date);
//
//            } else {
//                stringBuffer.append(format).append(",");
//                if (count % 20 == 0) stringBuffer.append("\r\n");
//                count++;
//            }
            stringBuffer.append(format).append("-") .append(weekDay-1)  .append(",");
            if (count % 20 == 0) stringBuffer.append("\r\n");
            count++;
            calendar.add(Calendar.DATE, 1);
        }
        System.out.println(stringBuffer);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream("date.txt"));
        bufferedOutputStream.write(stringBuffer.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String format1(Date date) {
        return date == null ? "" : dateFormat.format(date);
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
}
