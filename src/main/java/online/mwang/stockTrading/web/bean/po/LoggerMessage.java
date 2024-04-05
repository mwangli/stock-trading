package online.mwang.stockTrading.web.bean.po;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoggerMessage{

    private String body;
    private String timestamp;
    private String threadName;
    private String className;
    private String level;
}