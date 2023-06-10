package online.mwang.foundtrading.utils;

import lombok.SneakyThrows;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/6/10 16:04
 * @description: OCRUtils
 */
@Component
public class OCRUtils {

    // 执行OCR图片识别
    @SneakyThrows
    public String execute(String base64) {
        ITesseract instance = new Tesseract();
        final String path = new ClassPathResource("/ocr").getPath();
        instance.setDatapath(path);
        instance.setLanguage("eng");
        return instance.doOCR(base64ToBufferedImage(base64.split(",")[1]));
    }

    @SneakyThrows
    private static String BufferedImageToBase64(BufferedImage bufferedImage) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        byte[] bytes = baos.toByteArray();
        final Base64.Encoder encoder = Base64.getEncoder();
        String pngBase64 = encoder.encodeToString(bytes).trim();
        return "data:image/jpg;base64," + pngBase64;
    }

    @SneakyThrows
    private static BufferedImage base64ToBufferedImage(String base64) {
        final Base64.Decoder decoder = Base64.getDecoder();
        int start = base64.indexOf(",");
        base64 = base64.substring(start + 1);
        byte[] bytes1 = decoder.decode(base64);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes1);
        return ImageIO.read(bais);
    }
}
