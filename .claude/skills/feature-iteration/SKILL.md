---
name: feature-iteration
description: Use when user mentions 迭代, 功能迭代, 需求迭代, or similar terms indicating a feature development or iteration task
---

# 功能迭代 (Feature Iteration)

## Overview

功能迭代是标准化的软件开发流程，确保从需求到交付的每个环节都经过严格评审和验证。遵循"文档先行，编码在后"原则，通过多阶段评审保证质量。

## When to Use

**触发关键词：**
- 迭代、功能迭代、需求迭代
- 开发新功能、添加功能
- 实现需求、功能开发
- feature development, iteration

**适用场景：**
- 新增功能模块
- 现有功能优化
- 需求变更实现
- Bug修复后的功能完善

## Workflow

```
需求分析 → 需求评审 → 设计文档 → 设计评审 → 编码实现 → 测试验证 → 用户验收 → 代码提交
    ↑__________↓_____________↓____________↓__________↓___________↓___________↓
              (迭代循环，任何阶段发现问题都返回修改)
```

## Phase 1: 需求分析 (Requirements Analysis)

### MUST DO
1. **理解用户原始需求**
   - 记录功能描述的核心要点
   - 确认业务目标和价值
   - 识别隐含需求和边界条件

2. **生成需求文档 (PRD)**
   - 文档编号: PRD-XXX
   - 包含：背景、目标、范围、功能列表、验收标准
   - 使用模板：`doc/requirements/PRD-XXX-需求名称.md`

3. **需求评审 Checklist**
   - [ ] 需求描述清晰无歧义
   - [ ] 功能范围明确
   - [ ] 验收标准可量化
   - [ ] 依赖关系已识别

### Output
- `doc/requirements/PRD-XXX-需求名称.md`

## Phase 2: 设计文档 (Design Documents)

### MUST DO
1. **架构设计 (DESIGN-001)**
   - 系统架构图
   - 技术选型说明
   - 模块划分

2. **数据库设计 (DESIGN-002)**
   - ER图
   - 表结构设计
   - 索引设计
   - 使用 MyBatis-Plus 注解规范

3. **API接口设计**
   - RESTful接口规范
   - 请求/响应格式
   - 错误码定义

4. **前端设计**
   - 页面结构
   - 组件设计
   - 状态管理

### Output
- `doc/design/DESIGN-001-架构设计.md`
- `doc/design/DESIGN-002-数据库设计.md`
- `doc/design/DESIGN-003-接口设计.md` (如需要)

## Phase 3: 设计评审 (Design Review)

### MUST DO
1. **架构评审**
   - [ ] 是否符合现有架构
   - [ ] 扩展性是否满足要求
   - [ ] 技术选型是否合理

2. **数据库评审**
   - [ ] 表结构是否符合范式
   - [ ] 索引是否合理
   - [ ] 字段命名规范

3. **API评审**
   - [ ] 接口是否符合RESTful规范
   - [ ] 参数验证规则
   - [ ] 响应格式统一

### Decision Gate
**用户必须确认设计无误后才能进入编码阶段**

## Phase 4: 编码实现 (Implementation)

### MUST DO
1. **环境准备**
   - 确认技术栈版本
   - 检查依赖库
   - 配置开发环境

2. **代码规范**
   - 遵循 AGENTS.md 中的代码规范
   - MyBatis-Plus: 使用 `@TableName`, `@TableField`, `@TableId`
   - Service层: 继承 `ServiceImpl<M, T>`
   - Mapper层: 继承 `BaseMapper<T>`

3. **开发顺序**
   - 数据库表/实体类
   - Mapper接口
   - Service实现
   - Controller接口
   - 前端页面

4. **代码注释**
   - 复杂逻辑必须注释
   - 公共方法添加JavaDoc
   - API添加Swagger注解

### MUST NOT DO
- 不编写设计文档直接编码
- 跳过代码审查
- 使用不规范的命名
- 硬编码配置信息

## Phase 5: 测试验证 (Testing)

### MUST DO
1. **单元测试**
   - Service层核心方法
   - 工具类方法
   - 覆盖率 > 80%

2. **集成测试**
   - API接口测试
   - 数据库操作测试
   - 前端页面测试

3. **验收标准 Checklist**
   - [ ] 功能符合需求描述
   - [ ] 边界条件处理正确
   - [ ] 异常处理完善
   - [ ] 性能满足要求
   - [ ] 无console错误
   - [ ] 响应时间 < 500ms

4. **测试命令**
   ```bash
   # 后端测试
   mvn test
   
   # 前端测试
   npm test
   ```

### Acceptance Criteria (验收标准)
- **功能完整性**: 所有需求点已实现
- **代码质量**: 无严重代码异味
- **测试覆盖**: 核心功能都有测试用例
- **文档完整**: 代码注释和文档已更新
- **用户体验**: 界面友好，交互流畅

## Phase 6: 问题修复 (Bug Fixing)

### Self-Analysis Process

遇到问题时按以下流程自我分析和修复：

1. **问题定位**
   - 查看错误日志
   - 复现问题步骤
   - 确定影响范围

2. **根因分析**
   - 代码逻辑错误？
   - 数据库问题？
   - 配置问题？
   - 第三方依赖问题？

3. **修复验证**
   - 修改代码
   - 本地测试
   - 回归测试

4. **修复标准**
   - 不引入新问题
   - 保持代码风格一致
   - 添加必要的注释

### Common Issues & Solutions

| 问题类型 | 排查方向 | 解决方案 |
|---------|---------|---------|
| 数据库错误 | SQL语句、连接池、事务 | 检查MyBatis-Plus配置，验证SQL |
| API错误 | 参数验证、序列化、路径 | 检查Controller注解和DTO |
| 前端错误 | 组件渲染、状态管理、API调用 | 检查React组件生命周期和Hooks |
| 性能问题 | 查询优化、N+1、缓存 | 使用分页、添加索引、优化查询 |

## Phase 7: 用户验收 (User Acceptance)

### MUST DO
1. **功能演示**
   - 演示所有功能点
   - 说明实现方案
   - 展示测试用例

2. **用户确认**
   - 用户验收通过
   - 记录反馈意见
   - 确认是否需调整

3. **文档更新**
   - 更新需求文档状态
   - 补充实现细节
   - 更新部署文档

### Decision Gate
**必须获得用户明确确认后才能提交代码**

## Phase 8: 代码提交 (Code Submission)

### MUST DO
1. **代码整理**
   - 删除调试代码
   - 格式化代码
   - 检查代码规范

2. **提交信息**
   ```
   type(scope): subject
   
   body (optional)
   
   footer (optional)
   ```
   
   示例：
   ```
   feat(product): add product collection feature
   
   - Add Product entity with MyBatis-Plus annotations
   - Implement ProductService with pagination
   - Create ProductController with RESTful APIs
   - Add frontend product list page
   
   Closes #123
   ```

3. **提交前检查**
   ```bash
   # 检查未提交文件
   git status
   
   # 检查代码变更
   git diff
   
   # 运行测试
   mvn test
   npm test
   ```

4. **提交流程**
   ```bash
   git add .
   git commit -m "feat(scope): description"
   git push origin feature/branch-name
   ```

## Decision Gates (决策关卡)

### Gate 1: 需求确认
- 需求文档已生成
- 用户确认需求无误
- **阻止进入**: 需求不清晰

### Gate 2: 设计确认
- 设计文档已完成
- 用户确认设计方案
- **阻止进入**: 设计有重大问题

### Gate 3: 测试通过
- 所有测试用例通过
- 验收标准已满足
- **阻止进入**: 有严重Bug未修复

### Gate 4: 用户验收
- 用户确认功能满足需求
- **阻止进入**: 用户不满意

## Common Mistakes

| 错误 | 后果 | 正确做法 |
|-----|------|---------|
| 跳过设计直接编码 | 返工率高，架构混乱 | 严格按照文档先行原则 |
| 需求未经确认 | 偏离用户预期 | 每个阶段都需用户确认 |
| 测试不充分 | 线上Bug多 | 严格执行验收标准 |
| 代码未格式化 | 代码风格不一致 | 提交前运行format命令 |
| 缺少注释 | 可维护性差 | 复杂逻辑必须注释 |

## Tool Commands

```bash
# 后端构建和测试
mvn clean compile
mvn test
mvn spring-boot:run

# 前端构建和测试
npm install
npm run dev
npm run build
npm run format

# 代码检查
mvn checkstyle:check
```

## Cross-References

**Required Sub-Skills:**
- `detail-spec-writing` - 编写详细设计文档
- `test-driven-development` - 测试驱动开发
- `systematic-debugging` - 系统性调试
- `verification-before-completion` - 完成前验证

**Related Skills:**
- `multi-role-collaboration` - 多角色协作
- `requesting-code-review` - 代码审查请求
- `finishing-a-development-branch` - 完成开发分支

## Red Flags - STOP and Review

- [ ] 没有需求文档就开始编码
- [ ] 没有设计评审就实现功能
- [ ] 用户未确认就提交代码
- [ ] 测试未通过就宣布完成
- [ ] 跳过Bug修复直接提交

**遇到以上任一情况，立即停止并返回对应阶段。**
