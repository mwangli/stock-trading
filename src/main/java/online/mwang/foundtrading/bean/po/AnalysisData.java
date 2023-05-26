package online.mwang.foundtrading.bean.po;

import lombok.Data;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/26 15:12
 * @description: AnalysisData
 */
@Data
public class AnalysisData {
    // 最近收益
    private Double income;
    // 上次收益
    private Double preIncome;
    // 累计收益
    private Double totalIncome;
    // 平均收益
    private Double avgIncome;
    // 收益率
    private Double incomeRate;
    // 平均收益率
    private Double avgRate;
    // 日收益率
    private Double dailyIncomeRate;
    // 平均日收益率
    private Double avgDailyRate;
    // 账户金额
    private AccountInfo accountInfo;
    // 收益列表
    private List<Point> incomeList;
    // 收益率列表
    private List<Point> rateList;
    // 日收益率列表
    private List<Point> dailyRateList;
    // 收益排行
    private List<Point> incomeOrder;
    // 收益排行
    private List<Point> rateOrder;
    // 持有天数
    private List<Point> holdDaysList;
    // 日收益率排行
    private List<Point> dailyRateOrder;
    private List<Integer> test;
}
