package online.mwang.stockTrading.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.bean.po.DevicePower;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/7/13 15:10
 * @description: DevicePowerController
 */
@Slf4j
@RestController
@Api(tags = "A-储能系统推荐")
public class DevicePowerController {

    @ResponseBody
    @PostMapping("test")
    @ApiOperation("储能系统推荐接口")
    public String getValues(@RequestBody List<DevicePower> params) {
//        final JSONArray power1List = params.getJSONArray("power1");
//        final ArrayList<DevicePower> list = new ArrayList<>();
        final double v1 = params.stream().mapToDouble(o -> o.getPower1() * o.getQuantity()).sum();
        final double v2 = params.stream().mapToDouble(o -> o.getPower2() * o.getQuantity() * o.getHours()).sum();
        final String res1 = getV1(v1);
        final String res2 = getV2(v2);
        return res1.concat("+").concat(res2);
    }

    private String getV1(double v1) {
        String res1 = "3.8KW";
        if (v1 < 3.8) {
            res1 = "3.8KW";
        }
        if (v1 >= 3.8 && v1 < 5.7) {
            res1 = "5.7KW";
        }
        if (v1 >= 5.7 && v1 < 7.6) {
            res1 = "7.6KW";
        }
        if (v1 >= 7.6 && v1 < 9.6) {
            res1 = "9.6KW";
        }
        if (v1 >= 9.6 && v1 < 11.4) {
            res1 = "11.4KW";
        }
        if (v1 >= 11.4) {
            res1 = "11.4KW";
        }
        return res1;
    }


    private String getV2(double v2) {
        String res1 = "H2";
        if (v2 < 8) {
            res1 = "H2";
        }
        if (v2 >= 8 && v2 < 12) {
            res1 = "H2";
        }
        if (v2 >= 12 && v2 < 16) {
            res1 = "H3";
        }
        if (v2 >= 16 && v2 < 20) {
            res1 = "h4";
        }
        if (v2 >= 20 && v2 < 24) {
            res1 = "H5";
        }
        if (v2 >= 24 && v2 < 28) {
            res1 = "H6";
        }
        if (v2 >= 28) {
            res1 = "H7";
        }
        return res1;
    }
}
