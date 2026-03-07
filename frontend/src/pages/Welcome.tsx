import {PageContainer} from '@ant-design/pro-components';
import {useModel} from '@umijs/max';
import {Card, theme} from 'antd';
import React from "react/index";

const Welcome: React.FC = () => {
  const {token} = theme.useToken();
  const {initialState} = useModel('@@initialState');

  const index = 1;
  const tittle = "StockTrading System";
  const href = "https://www.yuque.com/mwangli/ha7323/axga8dz9imansvl4";

  return (
    <PageContainer>
      <Card
        style={{
          borderRadius: 8,
        }}
        bodyStyle={{
          backgroundImage:
            initialState?.settings?.navTheme === 'realDark'
              ? 'background-image: linear-gradient(75deg, #1A1B1F 0%, #191C1F 100%)'
              : 'background-image: linear-gradient(75deg, #FBFDFF 0%, #F5F7FF 100%)',
        }}
      >
        <div
          style={{
            backgroundPosition: '100% -30%',
            backgroundRepeat: 'no-repeat',
            backgroundSize: '274px auto',
            backgroundImage:
              "url('https://gw.alipayobjects.com/mdn/rms_a9745b/afts/img/A*BuFmQqsB2iAAAAAAAAAAAAAAARQnAQ')",
          }}
        >
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 16,
            }}
          >
            <div
              style={{
                backgroundColor: token.colorBgContainer,
                boxShadow: token.boxShadow,
                borderRadius: '8px',
                fontSize: '14px',
                color: token.colorTextSecondary,
                lineHeight: '22px',
                padding: '16px 19px',
                minWidth: '220px',
                flex: 1,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  gap: '4px',
                  alignItems: 'center',
                }}
              >
                <div
                  style={{
                    width: 48,
                    height: 48,
                    lineHeight: '22px',
                    backgroundSize: '100%',
                    textAlign: 'center',
                    padding: '8px 16px 16px 12px',
                    color: '#FFF',
                    fontWeight: 'bold',
                    backgroundImage:
                      "url('https://gw.alipayobjects.com/zos/bmw-prod/daaf8d50-8e6d-4251-905d-676a24ddfa12.svg')",
                  }}
                >
                  {index}
                </div>
                <div
                  style={{
                    fontSize: '16px',
                    color: token.colorText,
                    paddingBottom: 8,
                  }}
                >
                  {tittle}
                </div>
              </div>
              <div
                style={{
                  fontSize: '14px',
                  color: token.colorTextSecondary,
                  textAlign: 'justify',
                  lineHeight: '22px',
                  marginBottom: 8,
                }}
              >
                <p
                  style={{
                    fontSize: '14px',
                    color: token.colorTextSecondary,
                    lineHeight: '22px',
                    marginTop: 16,
                    marginBottom: 32,
                    width: '95%',
                  }}
                  hidden={false}
                >
                  <div style={{
                    fontWeight: 'bolder'
                  }}>
                    Stock-Trading System是一款自动化股票交易管理软件，可以按照自定义交易策略，进行自动化的股票买卖交易, 也可以查看股票历史价格，实时价格，买卖记录和相关报表数据<br/>
                  </div>
                  <div style={{
                    fontWeight: 'bolder',
                    fontSize: 'large'
                  }}>
                    2025-03-13：目前暂时无法使用滑块验证码登录，待后期优化<br/>
                  </div>
                  <div style={{
                    fontSize: '16px',
                    fontWeight: 'bold',
                    marginTop: '16px'
                  }}>
                    开发日志:
                  </div>
                  <div
                    style={{
                      display: 'flex',
                      flexWrap: 'wrap',
                      // gap: 16,
                    }}
                  >
                    <div style={{
                      fontSize: '14px',
                      color: token.colorTextSecondary,
                      lineHeight: '22px',
                      marginTop: 16,
                      marginBottom: 16,
                      width: '50%',
                    }}>
                      <span>2023-06-03</span> <br/>
                      1. 修复日期差计算bug <br/>
                      2. 新增运行日志查询页面 <br/>
                      <span>2023-06-04</span> <br/>
                      3. 解决了日志查询滚动条固定到底部的问题 <br/>
                      <span>2023-06-06</span> <br/>
                      4. 优化买入金额问题 <br/>
                      <span>2023-06-09</span> <br/>
                      5. 增加取消订单的结果查询 <br/>
                      6. 增加数据分时时段查询功能 <br/>
                      <span>2023-06-10</span> <br/>
                      7. 线程池任务执行异常问题修复 <br/>
                      8. 验证码登录功能实现优化 <br/>
                      <span>2023-06-11</span> <br/>
                      9. HttpClient-FormData表单提交优化 <br/>
                      10. 自动登陆功能实现 <br/>
                      <span>2023-06-14</span> <br/>
                      11. 买卖时机策略优化 <br/>
                      12. 增加终止任务功能 <br/>
                      <span>2023-06-16</span> <br/>
                      13. 取消终止任务功能 <br/>
                      14. 增加日志开关选项 <br/>
                      <span>2023-06-20</span> <br/>
                      15. 增加任务中断功能 <br/>
                      16. 优化交易等待时间 <br/>
                      <span>2023-06-27</span> <br/>
                      17. 清除退市股票数据 <br/>
                      18. 调整交易等待时间 <br/>
                      <span>2023-06-29</span> <br/>
                      19. 实数数据获取优化 <br/>
                      <span>2023-07-04</span> <br/>
                      20. 任务运行状态优化 <br/>
                      21. 账户金额显示优化 <br/>
                      <span>2023-07-17</span> <br/>
                      22. 购买金额数量优化 <br/>
                      23. 排名重复数据优化 <br/>
                      <span>2025-02-09</span> <br/>
                      24. 系统功能异常提示 <br/>
                      <span>2024-04-8</span> <br/>
                      22. 后台项目整体框架优化<br/>
                      23. 使用LSTM模型预测价格 <br/>
                      <span>2024-04-11</span> <br/>
                    </div>
                    <div style={{
                      fontSize: '14px',
                      color: token.colorTextSecondary,
                      lineHeight: '22px',
                      marginTop: 16,
                      marginBottom: 32,
                      width: '40%',
                    }}>
                      24. 增加订单查询页面 <br/>
                      <span>2024-04-12</span> <br/>
                      25. 优化欢迎页面补充文档 <br/>
                      <span>2024-04-13</span> <br/>
                      26. 预测价格图表展示优化 <br/>
                      <span>2024-04-14</span> <br/>
                      27. 修复历史订单确实，优化查询功能 <br/>
                      28. 简化模型参数，提升训练速度 <br/>
                      <span>2024-04-27</span> <br/>
                      29. 优化访客用户登录名称 <br/>
                      <span>2024-04-28</span> <br/>
                      30. 买入股票逻辑优化 <br/>
                      <span>2024-04-29</span> <br/>
                      31. 买入卖出逻辑优化 <br/>
                      32. 增加debug日志模式 <br/>
                      <span>2024-05-01</span> <br/>
                      33. DL4J升级1.0.0版本 <br/>
                      34. 模型代码数据处理优化 <br/>
                      <span>2024-05-03</span> <br/>
                      35. 增加任务终止功能 <br/>
                      <span>2024-05-07</span> <br/>
                      36. 数据分析页面优化 <br/>
                      <span>2024-05-21</span> <br/>
                      37. 股票评分系数优化<br/>
                      38. 历史数据同步BUG修复<br/>
                      <span>2024-05-28</span> <br/>
                      39. 价格预测BUG修复<br/>
                      40. 线程池代码优化<br/>
                      <span>2024-06-17</span> <br/>
                      39. 预测结果展示修复<br/>
                      40. 买入选股数量优化<br/>
                      <span>2025-02-22</span> <br/>
                      41. 平台数据接口修复<br/>
                      42. 新增自选股票功能<br/>
                      <span>2025-03-13</span> <br/>
                      43. 新增刷新Token任务<br/>
                      44. 修复交易流程相关BUG<br/>
                    </div>

                  </div>
                </p>
              </div>
              <div
                style={{
                  fontSize: '14px',
                  color: token.colorTextSecondary,
                  textAlign: 'justify',
                  lineHeight: '22px',
                  marginBottom: 8,
                }}
              >
                {""}
              </div>
              <a href={href} target="_blank" rel="noreferrer">
                了解更多 {'>'}
              </a>
            </div>
          </div>
        </div>
      </Card>
    </PageContainer>
  );
};

export default Welcome;
