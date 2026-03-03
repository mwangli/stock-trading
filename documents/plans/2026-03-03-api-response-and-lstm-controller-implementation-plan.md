# ApiResponse & LstmTrainingController Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 新增通用 `ApiResponse<T>` DTO，并在 `LstmTrainingController` 中使用其返回统一响应。

**Architecture:** 在 `model-service` 模块新增 DTO 类 `ApiResponse<T>`，提供 success/error 工厂方法；新增控制器 `LstmTrainingController`，注入 `LstmTrainingService`，暴露 `POST /api/lstm/train` 接口并返回 `ApiResponse`。

**Tech Stack:** Java 17, Spring Boot 3.2.x, Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`).

---

### Task 1: 新增 ApiResponse DTO

**Files:**
- Create: `backend/model-service/src/main/java/com/stock/modelService/dto/ApiResponse.java`

**Step 1: 编写 DTO 类**

```java
package com.stock.modelService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
```

**Step 2: 编译验证（模块级）**

Run: `mvn compile -pl model-service` (在 backend 目录下)
Expected: BUILD SUCCESS

**Step 3: LSP 诊断**

Run: `lsp_diagnostics` on `ApiResponse.java`
Expected: no errors/warnings

**Step 4: Commit**

```bash
git add backend/model-service/src/main/java/com/stock/modelService/dto/ApiResponse.java
git commit -m "feat(model-service): 添加ApiResponse通用响应"
```

---

### Task 2: 新增 LstmTrainingController 控制器

**Files:**
- Create: `backend/model-service/src/main/java/com/stock/modelService/controller/LstmTrainingController.java`

**Step 1: 编写控制器类**

```java
package com.stock.modelService.controller;

import com.stock.modelService.dto.ApiResponse;
import com.stock.modelService.service.LstmTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/lstm")
public class LstmTrainingController {
    private final LstmTrainingService lstmTrainingService;

    @PostMapping("/train")
    public ApiResponse<String> trainLstmModel(@RequestParam String stockCode) {
        lstmTrainingService.trainModel(stockCode);
        return ApiResponse.success("训练已触发");
    }
}
```

**Step 2: 编译验证（模块级）**

Run: `mvn compile -pl model-service` (在 backend 目录下)
Expected: BUILD SUCCESS

**Step 3: LSP 诊断**

Run: `lsp_diagnostics` on `LstmTrainingController.java`
Expected: no errors/warnings

**Step 4: Commit**

```bash
git add backend/model-service/src/main/java/com/stock/modelService/controller/LstmTrainingController.java
git commit -m "feat(model-service): 添加LSTM训练控制器"
```

---
