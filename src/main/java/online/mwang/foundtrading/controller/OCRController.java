package online.mwang.foundtrading.controller;


import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.dto.RecordDTO;
import online.mwang.foundtrading.utils.OcrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ocr")
@RequiredArgsConstructor
public class OCRController {


    private static final List<RecordDTO> recordDTOList = new ArrayList<>();
    private final List<String> ignoreWords = Arrays.asList("银行卡", "按金额", "筛选", "余额", "储蓄卡", ":", "收支", "十", "明细", "账本");
    private final HashSet keySet = new HashSet<String>();
    @Autowired
    private OcrUtils ocrUtils;

    @SneakyThrows
    @PostMapping("/uploadImage")
    public Response<Integer> uploadImage(MultipartFile originFileObj) {
        List<String> words = ocrUtils.basic(originFileObj.getBytes());
        recordDTOList.addAll(dataWash(words));
        log.info("缓存数据:{}", JSON.toJSONString(recordDTOList));
        log.info("缓存数据量:{}", recordDTOList.size());
        return Response.success(recordDTOList.size());
    }


    @SneakyThrows
    @GetMapping("/downloadExcel")
    public void downloadExcel(HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("test", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), RecordDTO.class).sheet("demo").doWrite(recordDTOList);
        recordDTOList.clear();
    }


    private List<RecordDTO> dataWash(List<String> words) {
        // fetchData
        List<String> lines = words.stream().map(w -> JSON.parseObject(w).getString("words")).collect(Collectors.toList());
        log.info("lines:{}", lines);
        // filterData
        List<String> filterLines = lines.stream().filter(word -> ignoreWords.stream().noneMatch(word::contains)).collect(Collectors.toList());
        log.info("filterLines:{}", filterLines);
        // buildData
        ArrayList<RecordDTO> dataList = new ArrayList<>();
        String year = "yyyy";
        String month = "MM";
        String day = "dd";
        for (int i = 0; i < filterLines.size() - 1; i++) {
            String line = filterLines.get(i);
            String nextLine = filterLines.get(i + 1);
            if (isYear(line)) {
                year = line.split("\\.")[0];
                month = line.split("\\.")[1];
                continue;
            }
            if (isDate(line)) {
                day = line.split("\\.")[1];
                continue;
            }
            if (!line.contains("￥")) {
                RecordDTO recordDTO = new RecordDTO();
                recordDTO.setYear(year);
                recordDTO.setMonth(month);
                recordDTO.setDay(day);
                if (nextLine.contains("￥")) {
                    if (nextLine.startsWith("-")) {
                        recordDTO.setType("支出");
                    } else {
                        recordDTO.setType("收入");
                    }
                    recordDTO.setAmount(nextLine.replaceAll("￥", ""));
                    recordDTO.setTarget(line);
                }
                dataList.add(recordDTO);
            }
        }
        return dataList;
    }


    private boolean isDate(String word) {
        return  word.length() <= 5 && word.contains(".") && !word.contains("￥");
    }

    private boolean isYear(String word) {
        return word.length() >= 7 && word.contains(".") && !word.contains("￥");
    }
}
