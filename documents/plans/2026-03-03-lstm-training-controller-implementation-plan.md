# LstmTrainingController Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 model-service 新增 LstmTrainingController，提供 `/api/lstm/train` POST 接口，使用统一 `Response<T>` 返回成功消息。

**Architecture:** 新增控制器类放置在 `com.stock.modelService.controller` 包中，使用 `@RestController` + `@RequestMapping` 暴露接口；通过构造注入 `LstmTrainingService` 并调用 `trainModel(stockCode)`。

**Tech Stack:** Java 17, Spring Boot 3.2.x, Lombok (`@RequiredArgsConstructor`, `@Slf4j`).

---

### Task 1: 新增 LstmTrainingController 控制器

**Files:**
- Create: `backend/model-service/src/main/java/com/stock/modelService/controller/LstmTrainingController.java`

**Step 1: 写下最小控制器骨架（不含业务逻辑）**

```java
package com.stock.modelService.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/lstm")
public class LstmTrainingController {
}
```

**Step 2: 添加依赖与接口方法签名**

```java
private final LstmTrainingService lstmTrainingService;

@PostMapping("/train")
public Response<String> trainLstmModel(@RequestParam String stockCode) {
    lstmTrainingService.trainModel(stockCode);
    return Response.success("训练已触发");
}
```

**Step 3: 补充必要 import**

```java
import com.stock.modelService.service.LstmTrainingService;
import com.stock.modelService.dto.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
```

**Step 4: 编译验证（模块级）**

Run: `mvn compile -pl model-service` (在 backend 目录下)
Expected: BUILD SUCCESS

**Step 5: LSP 诊断**

Run: `lsp_diagnostics` on `LstmTrainingController.java`
Expected: no errors/warnings

**Step 6: Commit**

```bash
git add backend/model-service/src/main/java/com/stock/modelService/controller/LstmTrainingController.java
git commit -m "feat(model-service): 添加LSTM训练控制器"
```

---
