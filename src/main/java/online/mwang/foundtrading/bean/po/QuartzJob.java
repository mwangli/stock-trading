package online.mwang.foundtrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
    private Date create_time;
    private Date updateTime;
}
