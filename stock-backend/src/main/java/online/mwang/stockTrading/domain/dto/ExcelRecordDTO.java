package online.mwang.stockTrading.web.bean.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@ColumnWidth(40)
public class ExcelRecordDTO {

    @ExcelIgnore
    @ExcelProperty("年份")
    private String year;
    @ExcelProperty("月份")
    private String month;
    @ExcelProperty("日期")
    private String day;
    @ExcelProperty("类型")
    private String type;
    @ExcelProperty("金额")
    private String amount;
    @ExcelProperty("对象")
    private String target;
}
