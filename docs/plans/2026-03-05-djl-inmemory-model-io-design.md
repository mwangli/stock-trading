# DJL 模型内存序列化/反序列化设计（无本地文件系统）

## 背景
当前模型训练通过 `Model.save(Path)` 落地到本地目录，再上传到 MongoDB；推理时从 MongoDB 下载到临时目录后再 `Model.load(Path)`。该流程依赖本地文件系统，不满足“完全无本地文件系统”的目标。

## 目标
- 训练后模型直接以内存流/字节数组保存到 MongoDB。
- 推理时直接从 MongoDB 的字节数组加载模型参数到内存，不落磁盘。
- 保留现有模型结构（`StockLSTMModel`）与归一化参数的逻辑。

## 约束
- 仅使用 DJL Java API。
- 禁止本地文件系统（包括临时目录）。
- MongoDB 作为唯一持久化存储。

## 方案选择
采用**方案 1：引擎级内存序列化 + 结构重建**。
- 训练端：模型参数序列化为 `byte[]`。
- 推理端：重建 `StockLSTMModel` 结构后注入参数。
- 版本控制避免结构变更导致反序列化失败。

## 架构与组件
- **ModelBinaryCodec**
  - `serialize(Model model) -> byte[]`
  - `deserialize(byte[] params, Block block)`
- **LstmTrainerService**
  - 训练完成后调用 `ModelBinaryCodec.serialize`。
  - 直接写入 MongoDB（`LstmModelDocument.params`）。
- **LstmInference**
  - 从 MongoDB 读取 `params` + `normalizationParams`。
  - 重建 `StockLSTMModel` 并调用 `deserialize` 注入参数。
- **LstmModelDocument**
  - 增加字段 `modelVersion`（结构/序列化版本）。

## 数据流程
### 训练保存流
1. 训练完成得到 `Model`。
2. `ModelBinaryCodec.serialize(model)` 生成 `paramsBytes`。
3. 写入 `LstmModelDocument`（`paramsBytes` + `normalizationParams` + `modelVersion`）。
4. 按业务标识替换旧模型。

### 加载推理流
1. 从 MongoDB 获取最新模型文档。
2. 按配置重建 `StockLSTMModel`。
3. `ModelBinaryCodec.deserialize(paramsBytes, block)` 注入参数。
4. 进入推理流程。

## 错误处理与回退
- 序列化失败：训练任务失败，不写入 MongoDB。
- 反序列化失败：记录错误与版本信息，回退到默认模型结构并标记 `isLoaded=false`。
- 版本不匹配：拒绝加载并告警。
- MongoDB 不可用：直接使用默认模型，不触碰文件系统。

## 测试与验证
- **集成测试**：真实训练一次，走 `serialize -> Mongo -> deserialize` 全链路。
- **一致性校验**：训练后即时推理与 Mongo 加载后推理结果误差可控（如相对误差 < 1e-5）。
- **异常场景**：
  - Mongo 不可用
  - 版本不匹配
- **构建验证**：仅编译 `model-service` 模块（`mvn compile -pl model-service`）。

## 风险与对策
- **DJL 版本差异**（项目存在 0.23.0/0.36.0 版本痕迹）：
  - 在实现前确认实际运行时版本。
  - 若流式序列化 API 不可用，回退到“内存 Repository”方案。

## 结论
通过引擎级内存序列化与结构重建，可完全移除本地文件系统依赖，实现训练与推理的纯内存模型存取流程。
