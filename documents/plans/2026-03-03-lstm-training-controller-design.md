# LstmTrainingController 设计说明

## 背景与目标
- 在 model-service 模块新增 LstmTrainingController，提供 LSTM 训练触发接口。
- 统一采用 Response<T> 作为 API 响应格式。

## 架构
- 新增控制器类：`com.stock.modelService.controller.LstmTrainingController`
- 路由前缀：`/api/lstm`
- 训练接口：`POST /train`

## 组件与依赖
- 依赖服务：`LstmTrainingService`（后续创建）。
- 使用 Lombok：`@RequiredArgsConstructor` + `@Slf4j`。
- 统一响应：`Response<T>`（需引用现有统一响应类）。

## 数据流
1. 接收 `stockCode` 作为 `@RequestParam`。
2. 调用 `lstmTrainingService.trainModel(stockCode)`。
3. 返回 `Response.success("训练已触发")`（具体文案可调整）。

## 错误处理
- 由全局 `@ControllerAdvice` 统一处理异常。
- 控制器中可记录必要日志，异常不吞并。

## 测试
- 当前仅新增控制器骨架，不新增测试。
- 若后续具备真实训练数据/环境，可补充集成测试验证。
