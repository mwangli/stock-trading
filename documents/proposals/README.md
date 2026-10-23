<!-- AI_GENERATE_START -- -->
# Proposals

本目录保存尚未完成全部验证的专项设计。Proposal 可以描述目标实现和验收条件，但不能作为当前生产能力证明。

## 状态定义

| 状态 | 含义 |
|---|---|
| `PROPOSED` | 仅完成设计，尚未完成源码实现 |
| `SOURCE_IMPLEMENTED` | 源码已经实现或正在工作区实现，但未完成编译或运行验证 |
| `COMPILED` | 已完成编译或构建，不代表业务效果有效 |
| `RUNTIME_VERIFIED` | 已完成文档声明范围内的运行验证 |
| `SUPERSEDED` | 已废弃、被替代或不再继续实施 |

## 编写要求

每份 Proposal 必须包含状态、目标与非目标、当前证据、设计方案、影响边界、验收条件、未验证事项及退出条件。

当能力完成相应验证后，将稳定的当前事实归并到主文档；Proposal 可以保留为历史决策记录并标记最终状态，但不能与主文档并行维护两套当前事实。

## 当前 Proposal

| Proposal | 状态 | 说明 |
|---|---|---|
| [RankingLSTM-v5](model/RankingLSTM-v5.md) | `SOURCE_IMPLEMENTED` | 横截面 Ranking 与双注意力 LSTM 优化设计，尚未完成编译和运行验证 |
| [NewsCollectionQuality-v1](data/NewsCollectionQuality-v1.md) | `PROPOSED` | 新闻采集层规范化、分类、去重、事件事实与历史回填设计，等待源码实施 |
<!-- AI_GENERATE_END -- -->