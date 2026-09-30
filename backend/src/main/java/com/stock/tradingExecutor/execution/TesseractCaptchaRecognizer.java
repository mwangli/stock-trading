// AI_GENERATE_START -
package com.stock.tradingExecutor.execution;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Tesseract 算术验证码识别组件。
 * 作为独立于 RapidOCR 的本地 Java OCR 引擎参与表达式投票。
 *
 * @author mwangli
 * @since 2026-09-30
 */
@Slf4j
@Component
public class TesseractCaptchaRecognizer {

    private final Tesseract engine;
    private final Object engineLock = new Object();

    /**
     * 初始化 Tesseract 引擎、英文数字模型和算术表达式字符白名单。
     */
    public TesseractCaptchaRecognizer() {
        File tessData = LoadLibs.extractTessResources("tessdata");
        engine = new Tesseract();
        engine.setDatapath(tessData.getAbsolutePath());
        engine.setLanguage("eng");
        engine.setPageSegMode(7);
        engine.setOcrEngineMode(1);
        engine.setVariable("tessedit_char_whitelist", "0123456789+-*/=");
        engine.setVariable("load_system_dawg", "0");
        engine.setVariable("load_freq_dawg", "0");
    }

    /**
     * 使用 Tesseract Java SDK 识别预处理后的验证码图片。
     *
     * @param image 验证码图片
     * @return OCR 原始文本，识别失败时返回 null
     */
    public String recognize(BufferedImage image) {
        if (image == null) {
            return null;
        }
        try {
            synchronized (engineLock) {
                return engine.doOCR(image);
            }
        } catch (TesseractException | RuntimeException exception) {
            log.warn("[ZXBroker] Tesseract OCR 识别失败: {}", exception.getMessage());
            return null;
        }
    }
}
// AI_GENERATE_END -