package online.mwang.stockTrading.web.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.Data;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/30 15:56
 * @description: ScoreStrategy
 */
@Data
@TableName(autoResultMap = true)
public class ModelStrategy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String params;
    private String description;
    private Integer status;
    private Integer sort;
    private Integer deleted;
    private Date createTime;
    private Date updateTime;

    public static SFunction<ModelStrategy, Object> getOrder(String key) {
        if (key == null) return ModelStrategy::getSort;
        switch (key) {
            case "status":
                return ModelStrategy::getStatus;
            case "createTime":
                return ModelStrategy::getCreateTime;
            case "updateTime":
                return ModelStrategy::getUpdateTime;
            default:
                return ModelStrategy::getSort;
        }
    }
}
