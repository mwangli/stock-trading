package online.mwang.foundtrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
public class ScoreStrategy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String params;
    private String description;
    private Integer status;
    private Integer deleted;
    private Date createTime;
    private Date updateTime;
}
