<!-- AI_GENERATE_START ---- -->
# Python 模型端

该目录负责 `stock-trading4` 的离线模型训练、评估、ONNX 导出和模型制品构建。

## 职责

- 校验 Java 导出的训练数据快照。
- 训练和评估共享多任务 LSTM。
- 训练、蒸馏和评估情感模型。
- 默认导出 FP32 ONNX。
- 仅在 2C4G FP32 实测资源不足时生成 INT8 情感模型候选。
- 生成 Manifest、Metadata、Metrics、SHA-256 和 Golden Case。

Python 不负责交易、风控、券商调用或生产模型激活。

## 代码注释约定

面向主要使用 Java 的维护者，Python 类、函数、数据契约和关键业务分支使用中文 Docstring/注释；Python 语法、库名和标准类型保留英文。注释重点说明业务用途、输入输出、失败条件和生产边界，不为简单赋值添加重复注释。

## 本地运行

要求 Python 3.14。开发环境可使用虚拟环境：

```text
python -m venv .venv
.venv\Scripts\activate
python -m pip install -e ".[dev]"
python -m stock_models inspect-config --config configs/base.yaml
```

正式可复现任务使用 Docker：

```text
docker compose --profile training run --rm stock-python inspect-config --config /workspace/configs/base.yaml
```

运行数据全部放在 `runtime/`，不纳入 Git。

## 当前批次能力

当前工程底座提供：

- 配置读取与校验。
- Run ID 和运行目录创建。
- LSTM 与情感 JSONL 数据快照的 Manifest、Schema、字段顺序、类型、Shape、记录数与 SHA-256 校验。
- ONNX 制品目录打包。

Java 导出的单个快照目录结构为：

```text
<dataset-id>/
├─ schema.json
├─ train.jsonl
├─ validation.jsonl
└─ manifest.json
```

校验命令：

```text
python -m stock_models validate-data --manifest runtime/datasets/<dataset-id>/manifest.json
```

快照文件必须使用无 BOM UTF-8。JSONL 字段顺序由 `schema.json` 固定，任何字段错位、Shape 变化、摘要变化或记录数不一致都会被拒绝。

LSTM 和情感训练在后续批次实现，不提供无实现的占位训练命令。
<!-- AI_GENERATE_END ---- -->
