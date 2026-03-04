# DJL 模型内存序列化/反序列化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 训练与推理阶段在不使用任何本地文件系统的前提下，以 `byte[]/InputStream/OutputStream` 直接与 MongoDB 交互完成模型保存与加载。

**Architecture:** 通过 `ModelBinaryCodec` 负责参数流式序列化/反序列化，训练端保存 `paramsBytes` + `normalizationParams` 到 MongoDB，推理端重建 `StockLSTMModel` 结构后注入参数。

**Tech Stack:** Java 17, Spring Boot 3.2.x, DJL (ai.djl.*), MongoDB。

---

### Task 1: 确认 DJL 参数流式 API 可用性

**Files:**
- Modify: 无（仅检查）

**Step 1: 查阅 DJL Java API**
目标：确认是否存在类似 `Block.saveParameters(...)` / `Block.loadParameters(...)` 或等价 API，支持 `DataInputStream/DataOutputStream`。

**Step 2: 记录确认结论**
将可用 API 方法名、参数签名记录到实现注释或开发笔记（不写入业务代码）。

**Step 3: 选择最终序列化路径**
优先使用 DJL 的参数流式 API；若缺失，则回退为 DJL 提供的等价内存序列化方法（仍禁止 Path/File）。

---

### Task 2: 新增 ModelBinaryCodec 接口与实现

**Files:**
- Create: `backend/src/main/java/com/stock/modelService/model/io/ModelBinaryCodec.java`
- Create: `backend/src/main/java/com/stock/modelService/model/io/DjlParameterCodec.java`

**Step 1: 写测试骨架（若已有测试模块可用）**
若当前测试环境可用（Mongo 与 DJL 运行时具备），新增测试类：
- Create: `backend/src/test/java/com/stock/modelService/model/io/DjlParameterCodecTest.java`

示例测试结构（根据可用 API 调整）：
```java
@SpringBootTest
class DjlParameterCodecTest {
    @Test
    void shouldSerializeAndDeserializeParameters() {
        // 1) 创建模型结构
        // 2) 初始化参数
        // 3) serialize -> bytes
        // 4) 新模型结构 -> deserialize
        // 5) 对比参数是否一致
    }
}
```

**Step 2: 编写接口**
```java
public interface ModelBinaryCodec {
    byte[] serialize(Model model) throws IOException;

    void deserialize(byte[] paramsBytes, Block block) throws IOException;
}
```

**Step 3: 编写 DJL 实现**
```java
public class DjlParameterCodec implements ModelBinaryCodec {
    @Override
    public byte[] serialize(Model model) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 使用 DJL 参数流式 API 写入 baos
            return baos.toByteArray();
        }
    }

    @Override
    public void deserialize(byte[] paramsBytes, Block block) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(paramsBytes)) {
            // 使用 DJL 参数流式 API 从 bais 注入到 block
        }
    }
}
```

**Step 4: 运行测试（若环境满足）**
Run: `mvn -pl model-service -Dtest=DjlParameterCodecTest test`
Expected: PASS（若 Mongo 或依赖不可用，可用 `Assumptions.assumeTrue(...)` 跳过）

---

### Task 3: 扩展 LstmModelDocument 结构

**Files:**
- Modify: `backend/src/main/java/com/stock/modelService/entity/LstmModelDocument.java`

**Step 1: 添加字段**
```java
private String modelVersion;
```

**Step 2: 更新必要的 getter/setter/构造**
确保序列化正常。

**Step 3: 运行编译**
Run: `mvn -pl model-service -DskipTests compile`
Expected: BUILD SUCCESS

---

### Task 4: 训练端保存逻辑改为纯内存

**Files:**
- Modify: `backend/src/main/java/com/stock/modelService/service/LstmTrainerService.java`

**Step 1: 接入 ModelBinaryCodec**
通过依赖注入或实例化方式在 `LstmTrainerService` 中使用 `ModelBinaryCodec`。

**Step 2: 替换 `saveModel` 中的文件系统逻辑**
移除：
- `model.save(Path)`
- `Files.writeString/Files.walk/ZipOutputStream`
- `model-ready.txt` 写入

新增：
- `byte[] paramsBytes = codec.serialize(model);`
- 写入 MongoDB 文档字段 `params` 与 `normalizationParams`
- `modelVersion` 赋值（例如 `v1`）

**Step 3: 编译验证**
Run: `mvn -pl model-service -DskipTests compile`
Expected: BUILD SUCCESS

---

### Task 5: 推理端加载逻辑改为纯内存

**Files:**
- Modify: `backend/src/main/java/com/stock/modelService/inference/LstmInference.java`

**Step 1: 接入 ModelBinaryCodec**

**Step 2: 替换 Mongo 加载逻辑**
移除：
- 临时目录创建
- Zip 解压/文件写入

新增：
- 从 Mongo 读取 `paramsBytes`
- 重建 `StockLSTMModel` 结构
- `codec.deserialize(paramsBytes, block)` 注入参数
- `model.setBlock(block)`

**Step 3: 版本校验**
若 `modelVersion` 不匹配，拒绝加载并记录告警。

**Step 4: 编译验证**
Run: `mvn -pl model-service -DskipTests compile`
Expected: BUILD SUCCESS

---

### Task 6: 集成测试与一致性验证

**Files:**
- Modify/Create: `backend/src/test/java/com/stock/modelService/inference/LstmInferenceTest.java`
- Modify/Create: `backend/src/test/java/com/stock/modelService/model/io/DjlParameterCodecTest.java`

**Step 1: 端到端测试**
训练一次模型 → `serialize` → 写入 Mongo → `deserialize` → 推理。
如真实数据不足，使用 `Assumptions.assumeTrue(...)` 跳过。

**Step 2: 结果一致性断言**
对同一输入比较误差（相对误差 < 1e-5）。

**Step 3: 运行测试**
Run: `mvn -pl model-service test`
Expected: PASS 或按 Assumptions 跳过。

---

### Task 7: 最终验证与提交

**Step 1: LSP Diagnostics**
对修改文件执行 lsp_diagnostics，确保无错误。

**Step 2: 构建验证**
Run: `mvn -pl model-service -DskipTests compile`
Expected: BUILD SUCCESS

**Step 3: Git 提交**
按模块与变更内容拆分原子提交，遵循 `feat/fix/docs/...` 规范。

---

## 备注
- 全流程禁止使用任何本地文件系统（包括临时目录）。
- 若 DJL 无参数流式 API，需回退到“内存 Repository/Artifact”实现，但仍禁止 Path/File。
