# ApiResponse 设计说明

## 背景与目标
- 新增通用 `ApiResponse<T>` 作为统一 API 响应封装。
- 用于 LSTM 训练控制器返回统一格式。

## 架构
- DTO 新增：`com.stock.modelService.dto.ApiResponse<T>`
- 控制器使用：`com.stock.modelService.controller.LstmTrainingController`

## 组件与依赖
- Lombok：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 字段：`success`、`message`、`data`
- 静态工厂：`success(T data)`、`error(String message)`

## 数据流
1. `POST /api/lstm/train` 接收 `stockCode`
2. 调用 `LstmTrainingService.trainModel(stockCode)`
3. 返回 `ApiResponse.success("训练已触发")`

## 错误处理
- 统一交给全局 `@ControllerAdvice` 处理

## 测试
- 本次不新增测试；后续具备真实环境时补充集成测试
