package online.mwang.foundtrading.bean.po;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/7/13 15:07
 * @description: Device
 */
@Data
public class DevicePower {

    @ExcelProperty("Load")
    private String name;
    @ExcelProperty("Nominal Power (KW)")
    private Double power1;
    @ExcelProperty("Backup Power (KW)")
    private Double power2;
    private Integer quantity;
    private Integer hours;
}
