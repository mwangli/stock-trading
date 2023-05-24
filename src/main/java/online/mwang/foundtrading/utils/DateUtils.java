package online.mwang.foundtrading.utils;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {

    public static void main(String[] args) throws Exception {
        StringBuilder stringBuffer = new StringBuilder();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -5);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        int count = 1;
        while (count <= 1000) {
            int weekDay = calendar.get(Calendar.DAY_OF_WEEK);
            Date date = calendar.getTime();
            String format = simpleDateFormat.format(date);
            if (weekDay == 1 || weekDay == 7) {
                System.out.println(weekDay);
                System.out.println(date);

            } else {
                stringBuffer.append(format).append(",");
                if (count % 20 == 0) stringBuffer.append("\r\n");
                count++;
            }
            calendar.add(Calendar.DATE, 1);
        }
        System.out.println(stringBuffer);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream("json/date.txt"));
        bufferedOutputStream.write(stringBuffer.toString().getBytes(StandardCharsets.UTF_8));
    }
}
