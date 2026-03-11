# 情感分析模型格式转换指南

## 目录
- [1. 背景与问题](#1-背景与问题)
- [2. DJL 模型格式要求](#2-djl-模型格式要求)
- [3. 转换方案选择](#3-转换方案选择)
- [4. 完整转换流程](#4-完整转换流程)
- [5. 验证与测试](#5-验证与测试)
- [6. 常见问题解决](#6-常见问题解决)
- [7. 最佳实践](#7-最佳实践)

---

## 1. 背景与问题

### 1.1 当前状况
AI 股票交易系统的情感分析模块使用 Deep Java Library (DJL) 来加载和推理 Hugging Face 的 FinBERT 中文情感分析模型 (`yiyanghkust/finbert-tone-chinese`)。

### 1.2 核心问题
- **Hugging Face 模型格式**：现代 Hugging Face 模型默认提供 `model.safetensors` 文件
- **DJL 兼容性限制**：DJL 0.28.0 版本不直接支持 safetensors 格式，需要 TorchScript 格式（`.pt` 文件）
- **格式不匹配导致**：模型加载失败，系统回退到规则模式

### 1.3 解决方案概述
将 safetensors 格式的模型转换为 DJL 兼容的 TorchScript 格式，并确保所有必要配置文件正确设置。

---

## 2. DJL 模型格式要求

### 2.1 必需文件清单
DJL 情感分析模型目录必须包含以下文件：

| 文件 | 说明 | 是否必需 |
|------|------|----------|
| `{model_name}.pt` | TorchScript 格式的模型权重 | ✅ 必需 |
| `tokenizer.json` | Hugging Face 分词器完整配置 | ✅ 必需 |
| `config.json` | 模型架构和超参数配置 | ✅ 必需 |
| `serving.properties` | DJL 服务配置文件 | ✅ 必需 |

### 2.2 目录结构要求
```
backend/models/sentiment/
├── sentiment.pt              # TorchScript 模型文件
├── tokenizer.json           # 分词器配置
├── config.json             # 模型配置
└── serving.properties      # DJL 服务配置
```

### 2.3 文件命名规范
- 模型文件名必须与目录名一致（或通过 serving.properties 显式指定）
- 使用 `.pt` 扩展名表示 TorchScript 格式

---

## 3. 转换方案选择

### 3.1 方案对比

| 方案 | 描述 | 优点 | 缺点 | 推荐度 |
|------|------|------|------|--------|
| **djl-convert 工具** | 官方提供的模型转换工具 | 自动化程度高，官方支持 | 需要网络下载，可能超时 | ⭐⭐⭐⭐ |
| **手动 Python 转换** | 使用 transformers + torch 手动转换 | 完全控制转换过程，可使用现有文件 | 需要 Python 环境和依赖 | ⭐⭐⭐⭐⭐ |
| **直接下载 PyTorch 格式** | 寻找已有的 PyTorch 格式模型 | 无需转换，直接使用 | 不是所有模型都提供 pytorch_model.bin | ⭐⭐ |

### 3.2 推荐方案
**手动 Python 转换方案**，原因：
- 可以直接使用现有的 `model.safetensors` 文件
- 避免网络下载大文件的超时问题
- 转换过程可控，便于调试
- 适用于离线环境

---

## 4. 完整转换流程

### 4.1 环境准备

#### 4.1.1 Python 依赖安装
```bash
pip install torch transformers safetensors
```

#### 4.1.2 验证模型文件
确保原始模型目录包含：
```
backend/models/sentiment/
├── config.json
├── model.safetensors
├── tokenizer.json
├── tokenizer_config.json
├── special_tokens_map.json
└── vocab.txt
```

### 4.2 Safetensors → PyTorch 转换

创建转换脚本 `convert_safetensors_to_pytorch.py`：

```python
from pathlib import Path
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

def main():
    """将 safetensors 格式的模型转换为 pytorch_model.bin"""
    source_dir = Path("D:/ai-stock-trading/backend/models/sentiment")
    
    if not source_dir.exists():
        print(f"Error: Model directory not found: {source_dir}")
        return False
    
    safetensors_file = source_dir / "model.safetensors"
    if not safetensors_file.exists():
        print(f"Error: model.safetensors not found in {source_dir}")
        return False
    
    try:
        # 加载 safetensors 格式的模型
        print("Loading model from safetensors...")
        model = AutoModelForSequenceClassification.from_pretrained(
            source_dir,
            use_safetensors=True
        )
        
        # 保存为 PyTorch 格式
        print("Saving as pytorch_model.bin...")
        torch.save(model.state_dict(), source_dir / "pytorch_model.bin")
        
        # 验证新文件
        new_file = source_dir / "pytorch_model.bin"
        if new_file.exists():
            size_mb = new_file.stat().st_size / (1024 * 1024)
            print(f"✅ Conversion completed successfully!")
            print(f"   New file: {new_file}")
            print(f"   Size: {size_mb:.2f} MB")
            return True
        else:
            print("❌ Failed to create pytorch_model.bin")
            return False
            
    except Exception as e:
        print(f"❌ Error during conversion: {e}")
        return False

if __name__ == "__main__":
    success = main()
    if not success:
        exit(1)
```

执行转换：
```bash
cd D:\ai-stock-trading\.tmp
python convert_safetensors_to_pytorch.py
```

### 4.3 PyTorch → TorchScript 转换

创建转换脚本 `convert_to_torchscript.py`：

```python
import torch
from pathlib import Path
from transformers import AutoModelForSequenceClassification, AutoTokenizer

def convert_pytorch_to_torchscript():
    """将 PyTorch 模型转换为 TorchScript 格式"""
    model_path = Path("D:/ai-stock-trading/backend/models/sentiment")
    
    # 1. 从 pytorch_model.bin 加载模型
    print("正在从 pytorch_model.bin 加载模型...")
    model = AutoModelForSequenceClassification.from_pretrained(model_path)
    tokenizer = AutoTokenizer.from_pretrained(model_path)
    
    # 2. 创建示例输入
    example_text = "这是一个测试句子。"
    inputs = tokenizer(
        example_text, 
        return_tensors="pt", 
        padding=True, 
        truncation=True, 
        max_length=128
    )
    
    # 3. 转换为 TorchScript (使用 strict=False 处理复杂输出)
    print("正在转换为 TorchScript 格式...")
    model.eval()
    traced_model = torch.jit.trace(
        model, 
        (inputs['input_ids'], inputs['attention_mask']),
        strict=False
    )
    
    # 4. 保存为 .pt 文件
    output_path = model_path / "sentiment.pt"
    traced_model.save(str(output_path))
    print(f"✅ TorchScript 模型已保存: {output_path}")
    
    # 5. 创建 serving.properties
    serving_props = """
engine=PyTorch
option.modelName=sentiment
"""
    with open(model_path / "serving.properties", "w", encoding="utf-8") as f:
        f.write(serving_props.strip())
    print("✅ serving.properties 已创建")

if __name__ == "__main__":
    convert_pytorch_to_torchscript()
```

执行转换：
```bash
cd D:\ai-stock-trading\.tmp
python convert_to_torchscript.py
```

### 4.4 清理工作
- 删除临时文件：`pytorch_model.bin`（如果不需要保留）
- 保留原始 safetensors 文件作为备份

---

## 5. 验证与测试

### 5.1 文件完整性验证

创建验证脚本 `verify_model_files.py`：

```python
from pathlib import Path
import torch
from transformers import AutoTokenizer

def verify_djl_model():
    """验证模型是否能被 DJL 正确加载"""
    model_dir = Path("D:/ai-stock-trading/backend/models/sentiment")
    
    # 检查必需文件
    required_files = [
        "sentiment.pt",
        "tokenizer.json", 
        "config.json",
        "serving.properties"
    ]
    
    print("检查必需文件:")
    for file in required_files:
        path = model_dir / file
        if path.exists():
            size_mb = path.stat().st_size / (1024 * 1024)
            print(f"  ✅ {file} ({size_mb:.2f} MB)")
        else:
            print(f"  ❌ {file} 缺失")
            return False
    
    # 测试 TorchScript 模型加载
    try:
        print("\n测试 TorchScript 模型加载...")
        model = torch.jit.load(str(model_dir / "sentiment.pt"))
        print("  ✅ TorchScript 模型加载成功")
    except Exception as e:
        print(f"  ❌ TorchScript 模型加载失败: {e}")
        return False
    
    # 测试 tokenizer 加载
    try:
        print("\n测试 tokenizer 加载...")
        tokenizer = AutoTokenizer.from_pretrained(model_dir)
        test_input = tokenizer("测试文本", return_tensors="pt")
        print("  ✅ Tokenizer 加载成功")
    except Exception as e:
        print(f"  ❌ Tokenizer 加载失败: {e}")
        return False
    
    print("\n✅ 所有验证通过！模型已准备好用于 DJL。")
    return True

if __name__ == "__main__":
    verify_djl_model()
```

### 5.2 Java 集成测试

在项目中运行集成测试：

```python
# simple_test.py
import requests
import json

def test_model_in_project():
    base_url = "http://localhost:8080"
    
    # 测试健康检查
    response = requests.get(f"{base_url}/api/model-sentiment/health")
    health_data = response.json()
    assert health_data["modelLoaded"] == True
    
    # 测试情感分析
    test_text = "股价上涨了10%，市场表现良好！"
    response = requests.post(
        f"{base_url}/api/model-sentiment/analyze",
        json={"text": test_text}
    )
    result = response.json()
    assert result["label"] == "Positive"
    assert result["modelLoaded"] == True
    
    print("✅ 集成测试通过！")

if __name__ == "__main__":
    test_model_in_project()
```

---

## 6. 常见问题解决

### 6.1 转换过程中内存不足
**问题**：转换大型模型时出现内存错误  
**解决方案**：
- 在内存充足的机器上执行转换
- 使用 `torch.no_grad()` 上下文管理器减少内存占用
- 分批处理（对于批量转换场景）

### 6.2 TorchScript 追踪失败
**问题**：`torch.jit.trace` 抛出错误  
**解决方案**：
- 添加 `strict=False` 参数
- 确保示例输入覆盖所有可能的分支
- 使用 `torch.jit.script` 替代（如果模型支持）

### 6.3 DJL 加载时找不到模型
**问题**：DJL 报错找不到 `.pt` 文件  
**解决方案**：
- 确保模型文件名与目录名一致
- 或在 `serving.properties` 中显式指定 `option.modelName`
- 检查文件权限和路径分隔符

### 6.4 模型推理结果异常
**问题**：预测结果不符合预期  
**解决方案**：
- 验证标签映射是否正确 (`neutral=0, positive=1, negative=2`)
- 检查 tokenizer 配置是否匹配训练时的配置
- 对比 Python 原生推理结果确认一致性

---

## 7. 最佳实践

### 7.1 模型管理
- **版本控制**：将转换后的模型文件纳入版本控制
- **备份策略**：保留原始 safetensors 文件作为备份
- **文档记录**：记录模型来源、转换日期、转换参数

### 7.2 转换流程自动化
创建一键转换脚本 `convert_model.sh`：

```bash
#!/bin/bash
# 模型转换自动化脚本

echo "🔄 开始模型格式转换..."

# 步骤1: Safetensors → PyTorch
python convert_safetensors_to_pytorch.py
if [ $? -ne 0 ]; then
    echo "❌ Safetensors 转换失败"
    exit 1
fi

# 步骤2: PyTorch → TorchScript
python convert_to_torchscript.py
if [ $? -ne 0 ]; then
    echo "❌ TorchScript 转换失败"
    exit 1
fi

# 步骤3: 验证
python verify_model_files.py
if [ $? -ne 0 ]; then
    echo "❌ 模型验证失败"
    exit 1
fi

echo "✅ 模型转换完成！"
```

### 7.3 环境隔离
- 使用虚拟环境进行转换操作
- 记录 Python 依赖版本
- 避免影响生产环境

### 7.4 性能优化
- 在 GPU 机器上进行转换以加速
- 使用 `torch.set_num_threads(1)` 控制 CPU 线程数
- 预热模型以获得准确的推理性能基准

---

## 附录 A: 相关配置文件

### SentimentTrainingConfig.java 关键配置
```java
@ConfigurationProperties(prefix = "models.sentiment")
public class SentimentTrainingConfig {
    private String modelPath = "models/sentiment";  // 模型路径
    private String modelSource = "local";           // 使用本地模型
    private boolean downloadPretrained = false;     // 禁用远程下载
    private String pretrainedModel = "yiyanghkust/finbert-tone-chinese";
}
```

### application.yml 应用配置
```yaml
djl:
  model:
    sentiment:
      path: ./models/sentiment

models:
  sentiment:
    model-path: "models/sentiment"
    model-source: "local"
    download-pretrained: false
```

## 附录 B: 故障排除清单

- [ ] 模型目录路径是否正确？
- [ ] 所有必需文件是否存在？
- [ ] 文件权限是否正确？
- [ ] DJL 版本是否兼容？
- [ ] Python 依赖版本是否匹配？
- [ ] 标签映射是否正确？
- [ ] tokenizer 配置是否完整？

---

**文档版本**: 1.0  
**最后更新**: 2026-03-11  
**适用项目**: AI 股票交易系统  
**适用模型**: yiyanghkust/finbert-tone-chinese