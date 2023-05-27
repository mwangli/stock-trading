package online.mwang.foundtrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String deleted;
    private Date createTime;
    private Date updateTime;

    public static SFunction<QuartzJob, Object> getOrder(String key) {
        if (key == null) return QuartzJob::getId;
        switch (key) {
            case "status":
                return QuartzJob::getStatus;
            case "createTime":
                return QuartzJob::getCreateTime;
            case "updateTime":
                return QuartzJob::getUpdateTime;
            default:
                return QuartzJob::getId;
        }
    }
}
