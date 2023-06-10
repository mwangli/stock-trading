package online.mwang.foundtrading.job;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunTokenJob extends BaseJob {

    private final DailyJob job;

    @SneakyThrows
    public static void main(String[] args) {
        // 创建 POST 请求对象
        HttpPost httpPost = new HttpPost("https://weixin.citicsinfo.com/reqxml?");

        HttpEntity httpEntity = new UrlEncodedFormEntity(
                Arrays.asList(new BasicNameValuePair("action", "100"),
                        new BasicNameValuePair("age", "20")),
                StandardCharsets.UTF_8
        );

//        FormEn

        httpPost.setEntity(httpEntity);
//        httpPost.removeHeader(new Header(HttpHeaders.CONTENT_LENGTH));

//        httpPost.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(httpEntity.getContentLength()));

        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        String result= EntityUtils.toString(entity,"UTF-8");
        System.out.println(result);
    }

    @Override
    public void run() {
        job.runTokenJob();
    }
}
