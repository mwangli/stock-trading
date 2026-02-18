package online.mwang.stockTrading.modules.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.Data;

import java.util.Date;

/**
 * 模型信息实体
 */
@Data
@TableName(autoResultMap = true)
public class ModelInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String trainPeriod;
    private Integer trainTimes;
    private Double testDeviation;
    private Double score;
    private Date createTime;
    private Date updateTime;
    private String status;

    public static SFunction<ModelInfo, Object> getOrder(String key) {
        if (key == null) return ModelInfo::getUpdateTime;
        switch (key) {
            case "name":
                return ModelInfo::getName;
            case "trainPeriod":
                return ModelInfo::getTrainPeriod;
            case "testDeviation":
                return ModelInfo::getTestDeviation;
            case "score":
                return ModelInfo::getScore;
            case "createTime":
                return ModelInfo::getCreateTime;
            case "updateTime":
                return ModelInfo::getUpdateTime;
            default:
                return ModelInfo::getStatus;
        }
    }
}
