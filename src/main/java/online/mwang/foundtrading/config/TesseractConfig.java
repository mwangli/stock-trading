package online.mwang.foundtrading.config;

import lombok.SneakyThrows;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.File;

@Configuration
public class TesseractConfig {

    @Bean
    @SneakyThrows
    public ITesseract getInstance() {
        ITesseract instance = new Tesseract();
        // 临时存储数据文件
        Resource resource = new ClassPathResource("/ocr/eng.traineddata");
        File tempFile = new File("/temp/eng.traineddata");
        FileUtils.copyToFile(resource.getInputStream(), tempFile);
        instance.setDatapath(tempFile.getParent());
        instance.setLanguage("eng");
        return instance;
    }
}
