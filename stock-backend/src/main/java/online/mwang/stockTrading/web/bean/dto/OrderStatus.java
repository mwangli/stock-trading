package online.mwang.stockTrading.web.bean.dto;

public class OrderStatus {
    private String answerNo;
    private String code;
    private String name;
    private String status;

    public OrderStatus() {
    }

    public OrderStatus(String answerNo, String code, String name, String status) {
        this.answerNo = answerNo;
        this.code = code;
        this.name = name;
        this.status = status;
    }

    public String getAnswerNo() {
        return answerNo;
    }

    public void setAnswerNo(String answerNo) {
        this.answerNo = answerNo;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
