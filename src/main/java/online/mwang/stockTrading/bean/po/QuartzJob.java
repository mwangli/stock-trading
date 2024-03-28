package online.mwang.stockTrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.Data;

import java.util.Date;

@Data
public class QuartzJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String className;
    private String cron;
    private String status;
    private String running;
    private String deleted;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
    @TableField(exist = false)
    private String token;
    @TableField(exist = false)
    private String logSwitch;
    @TableField(exist = false)
    private String enableWaiting;

    public static SFunction<QuartzJob, Object> getOrder(String key) {
        if (key == null) return QuartzJob::getSort;
        switch (key) {
            case "status":
                return QuartzJob::getStatus;
            case "createTime":
                return QuartzJob::getCreateTime;
            case "updateTime":
                return QuartzJob::getUpdateTime;
            default:
                return QuartzJob::getSort;
        }
    }
}
