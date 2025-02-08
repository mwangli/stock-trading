package online.mwang.stockTrading.web.controller;


import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.dto.ExcelRecordDTO;
import online.mwang.stockTrading.web.config.ExcelFillCellMergeStrategy;
import online.mwang.stockTrading.web.utils.OcrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ocr")
@RequiredArgsConstructor
public class OCRController {

    private static final Map<Long, List<String>> wordsMap = new HashMap<>();

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final List<String> ignoreWords = Arrays.asList("按金额", "筛选", "余额", "储蓄卡", "收支", "十", "明细", "借记卡", "账本", "借记卡3862",
            "其他消费", "交通出行", "卡号", "津贴", "备注", "明细", "分析", "借记卡", "l令", "记一笔", "结余");
    @Autowired
    private OcrUtils ocrUtils;

    @SneakyThrows
    @PostMapping("/uploadImage")
    public Response<Integer> uploadImage(MultipartFile originFileObj, HttpServletRequest request) {
        // 保证异步上传的文件有序,用文件修改时间戳作为key
        List<String> words = ocrUtils.basic(originFileObj.getBytes());
        // fetchData
        List<String> lines = words.stream().map(w -> JSON.parseObject(w).getString("words")).collect(Collectors.toList());
        // filterData
        log.info("lines:{}", lines);
        List<String> filterLines = lines.stream().filter(word -> ignoreWords.stream().noneMatch(word::contains)).collect(Collectors.toList());
//        log.info("filterLines:{}", filterLines);
        String lastModified = request.getParameter("lastModified");
        wordsMap.put(Long.parseLong(lastModified), filterLines);
        return Response.success(wordsMap.size());
    }


    @SneakyThrows
    @GetMapping("/downloadExcel")
    public void downloadExcel(HttpServletResponse response) {
        log.info("开始下载文件~~~~");
        int[] mergeColumnIndex = {0, 1};
        // 需要从第几行开始合并
        int mergeRowIndex = 1;
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("test", String.valueOf(StandardCharsets.UTF_8)).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), ExcelRecordDTO.class).inMemory(true).sheet("demo").registerWriteHandler(new ExcelFillCellMergeStrategy(mergeRowIndex, mergeColumnIndex)).doWrite(dataWash());
        wordsMap.clear();
    }


    /**
     * 针对建设银行，中信银行，招商银行手机银行APP账单截图识别
     */
    private List<ExcelRecordDTO> dataWash() {
        // sort
        ArrayList<Long> keys = new ArrayList<>(wordsMap.keySet());
        Collections.sort(keys);
        ArrayList<String> wordsList = new ArrayList<>();
        keys.forEach(key -> wordsList.addAll(wordsMap.get(key)));
        log.info("wordsList:{}", wordsList);
        // buildData
        ArrayList<ExcelRecordDTO> dataList = new ArrayList<>();
        HashSet<Object> duplicateSet = new HashSet<>();
        String year = "yyyy";
        String month = "MM";
        String day = "dd";
        // 用以暂存数据
        ExcelRecordDTO preDTO = new ExcelRecordDTO();
        for (int i = 0; i < wordsList.size() - 1; i++) {
            String line = wordsList.get(i);
            String nextLine = wordsList.get(i + 1);
            if (isYear(line)) {
                if (line.contains("-") && line.contains(":")) {
                    year = line.split("-")[0];
                    month = line.split("-")[1];
                    day = line.split("-")[2].substring(0, 2);
                }
                if (line.contains("/") ) {
//                    String part1 = line.split("\\/")[0];
//                    String part2 = line.split("\\/")[1].split("总")[0];
//                    month = part1.length() < part2.length() ? part1 : part2;
//                    year = part1.length() > part2.length() ? part1 : part2;
                }
                if (line.contains(".")) {
                    year = line.split("\\.")[0];
                    month = line.split("\\.")[1];
                }
                if (line.contains("年")) {
                    year = line.split("\\年")[0];
                    month = line.split("\\年")[1].replaceAll("月", "").replaceAll("v", "").replaceAll("V", "");
                }
                if (month.length() == 1) {
                    month = "0".concat(month);
                }
                preDTO.setYear(year);
                preDTO.setMonth(year + "-" + month);
                preDTO.setDay(day);
                continue;
            }
            if (isDate(line)) {
                if (line.contains("日") && line.contains("月")) {
                    day = line.split("\\月")[1];
                    day = day.split("\\日")[0];
                } else {
                    day = line.split("\\.")[1];
                    day = day.split("星期")[0];
                }
                continue;
            }
            if (!isAmount(line) && isAmount(nextLine)) {
                ExcelRecordDTO excelRecordDTO = new ExcelRecordDTO();
                excelRecordDTO.setYear(year.replaceAll("V", "").replaceAll("v", "").replaceAll("银行卡", "").substring(0, 4));
                excelRecordDTO.setMonth(excelRecordDTO.getYear() + "-" + month.replaceAll("v", "").replaceAll("银行卡", "").replaceAll("、", "").substring(0, 2));
                excelRecordDTO.setDay(day.length() == 1 ? "0".concat(day) : day);
                if (nextLine.contains("-")) {
                    excelRecordDTO.setType("支出");
                } else {
                    excelRecordDTO.setType("收入");
                }
                excelRecordDTO.setAmount(nextLine.replaceAll("￥", "").replaceAll("人民币元", "").replaceAll("不计入", "").replaceAll("不t", "").replaceAll(",", ""));
                excelRecordDTO.setTarget(line);
                String duplicateKey = excelRecordDTO.getYear() + excelRecordDTO.getMonth() + excelRecordDTO.getDay() + excelRecordDTO.getAmount() + excelRecordDTO.getTarget();
                if (!duplicateSet.contains(duplicateKey)) {
                    preDTO = excelRecordDTO;
                    dataList.add(excelRecordDTO);
                    duplicateSet.add(duplicateKey);
                }
            }
        }
        return dataList;
    }

    private boolean isAmount(String line) {
        return line.contains("￥") || line.contains("人民币元") || line.startsWith("-") || line.startsWith("+");
    }

    private boolean isDate(String word) {
//        boolean data1 = word.length() <= 5 && word.contains(".") && !word.contains("￥");
        boolean data2 = word.contains("月") && word.contains("日");
        boolean data3 = word.contains("星期") && word.contains(".");
        return data2 || data3;
    }

    private boolean isYear(String word) {
        return word.startsWith("2019") ||
                word.startsWith("2020") ||
                word.startsWith("2021") ||
                word.startsWith("2022") ||
                word.startsWith("2023") ||
                word.startsWith("2024") ||
                word.startsWith("2025") ||
                word.contains("/2020") ||
                word.contains("/2021") ||
                word.contains("/2022");
    }
}
