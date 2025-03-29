package online.mwang.stockTrading.web.utils;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class CaptchaSolver {

    static {
        // Load OpenCV library
//        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        //Loader.load(opencv_java.class);
//        System.load("/usr/local/lib/libopencv_java410.dylib");
        System.load("/usr/local/share/java/opencv4/libopencv_java4100.dylib");

    }
    public static void main(String[] args) throws Exception {
        // 1. 获取验证码图片
        BufferedImage bgImage = getImageFromURL("https://necaptcha.nosdn.127.net/e4a173774d4f46869a164ca7a5b20d51@2x.jpg");
        BufferedImage fgImage = getImageFromURL("https://necaptcha.nosdn.127.net/72cfcb2d8b0a44a5982d04ed8a0df47e@2x.png");

        // 2. 保存图片
        File bgFile = new File("bg.png");
        File fgFile = new File("fg.png");
        ImageIO.write(bgImage, "PNG", bgFile);
        ImageIO.write(fgImage, "PNG", fgFile);

        // 3. 处理图像，计算缺口位置
        Mat bgMat = Imgcodecs.imread(bgFile.getAbsolutePath());
        Mat fgMat = Imgcodecs.imread(fgFile.getAbsolutePath());
        int distance = calculateSliderDistance(bgMat, fgMat);

        // 4. 模拟滑动操作
        Robot robot = new Robot();
        Point sliderLocation = new Point(100, 200); // 假设滑块初始位置
        robot.mouseMove(sliderLocation.x, sliderLocation.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseMove(sliderLocation.x + distance, sliderLocation.y);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        // 等待验证结果
        Thread.sleep(2000);

        String token = "00ddfd98c91441699198f875f60098ac"; // 示例token
        String validate = "MbUNOYgtZj7q4/2Jf2KFq4Bd4126CyP+GnIQA1ShP0ZOvHwLNEgAKfbUcvdz4fETy8kYtYQbPwzwAOfgvF5GaWBbzLOPm0OoKGBm8rxIukrd9EHy50ZmY1BCCyD1H14Ibee0S2SR5PVnwwB0uBJm7jEoeEG5ORJskqp1yzBZBVE="; // 示例validate
        String zoneId = "CN31"; // 示例zoneId
        String referer = "https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html"; // 示例referer

        // 构建查询参数
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("referer", URLEncoder.encode(referer, "UTF-8"));
        queryParams.put("zoneId", zoneId);
        queryParams.put("token", token);
        queryParams.put("validate", validate);
        queryParams.put("dt", "ceMg845hNpFFFkBURBLTZ0YLNYu8K33W"); // 示例dt
        queryParams.put("id", "d7a0e925d21e41df9680622ac96778b0"); // 示例id
        queryParams.put("acToken", "undefined");
        queryParams.put("width", "220");
        queryParams.put("type", "2");
        queryParams.put("version", "2.27.2");
        queryParams.put("cb", "gGDVgB2juIyrkYsvt%2FXPRSTFMsEUpLEU%2FWjzyn%2Bca6FHcKdSz40uqsYngizFtC%2BF%2B9y2qR8bcYVUv8zp054SY2LPKpQ7");
        queryParams.put("extraData", "");
        queryParams.put("bf", "0");
        queryParams.put("runEnv", "10");
        queryParams.put("sdkVersion", "undefined");
        queryParams.put("iv", "4");
        queryParams.put("callback", "__JSONP_71y2rrv_1");

        String data = "your_encoded_data_here"; // 替换为实际的 data 参数值
        queryParams.put("data", URLEncoder.encode(data, "UTF-8"));

        // 构建完整的请求URL
        String baseUrl = "https://c.dun.163.com/api/v3/check";
        StringBuilder fullUrl = new StringBuilder(baseUrl);
        fullUrl.append("?");
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            fullUrl.append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append("&");
        }
        // 去掉最后一个"&"
        fullUrl.deleteCharAt(fullUrl.length() - 1);

        sendGetRequest(fullUrl.toString());

    }

    // 从URL获取图片
    private static BufferedImage getImageFromURL(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        InputStream inputStream = connection.getInputStream();
        return ImageIO.read(inputStream);
    }

    // 计算缺口的位置
    private static int calculateSliderDistance(Mat bgMat, Mat fgMat) {
        // 确保图像尺寸一致
        if (bgMat.rows() != fgMat.rows() || bgMat.cols() != fgMat.cols()) {
            Imgproc.resize(fgMat, fgMat, new Size(bgMat.cols(), bgMat.rows()));
        }

        // 确保图像为灰度图
        if (bgMat.channels() != 1) {
            Imgproc.cvtColor(bgMat, bgMat, Imgproc.COLOR_BGR2GRAY);
        }
        if (fgMat.channels() != 1) {
            Imgproc.cvtColor(fgMat, fgMat, Imgproc.COLOR_BGR2GRAY);
        }

        // 计算差异
        Mat diff = new Mat();
        Core.absdiff(bgMat, fgMat, diff);

        // 二值化差异图像
        Imgproc.threshold(diff, diff, 50, 255, Imgproc.THRESH_BINARY);

        // 查找最大值的位置
        for (int i = 0; i < diff.cols(); i++) {
            for (int j = 0; j < diff.rows(); j++) {
                if (diff.get(j, i)[0] > 0) {
                    return i; // 返回缺口的水平位置
                }
            }
        }
        return 0; // 如果没有找到缺口
    }

    private static void sendGetRequest(String urlString) throws Exception {
        // 创建 URL 对象
        URL url = new URL(urlString);

        // 打开连接
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        // 获取响应码
        int responseCode = connection.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            // 请求成功，处理响应内容
            try (InputStream is = connection.getInputStream();
                 InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader br = new BufferedReader(isr)) {

                String line;
                StringBuilder response = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                System.out.println("Response: " + response.toString());
                // 进一步解析响应数据
                // 根据实际返回的 JSON 数据进行解析和处理
            }
        } else {
            System.err.println("Request failed.");
        }
    }

}

