package online.mwang.foundtrading.utils;

import com.baidu.aip.ocr.AipOcr;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;


@Component
public class OcrUtils {

    public static final String APP_ID = "34683951";
    public static final String API_KEY = "hgfY9DodOtbSuF7v6n89YGki";
    public static final String SECRET_KEY = "DGsjMnwV4QxoQDjjrRfPxZxvaUmRmBsF";

    public String execute(String base64) {
        AipOcr client = new AipOcr(APP_ID, API_KEY, SECRET_KEY);
        System.setProperty("aip.log.level", "error");
        final Base64.Decoder decoder = Base64.getDecoder();
        byte[] bytes = decoder.decode(base64.split(",")[1]);
        JSONObject numbers = client.numbers(bytes, new HashMap<>());
        JSONArray wordsResult = numbers.getJSONArray("words_result");
        return wordsResult.getJSONObject(0).getString("words");
    }

    public List<String> basic(byte[] bytes) {
        AipOcr client = new AipOcr(APP_ID, API_KEY, SECRET_KEY);
        System.setProperty("aip.log.level", "error");
//        final Base64.Decoder decoder = Base64.getDecoder();
//        byte[] bytes = decoder.decode(base64.split(",")[1]);
        JSONObject words = client.basicGeneral(bytes, new HashMap<>());
        JSONArray wordsResult = words.getJSONArray("words_result");
        ArrayList<String> list = new ArrayList<>();
        wordsResult.forEach(e -> list.add(e.toString()));
        return list;
    }
}