---
language: 
- zh
tags:
- bert
- financial-sentiment-analysis
- sentiment-analysis
license: "apache-2.0"  
widget:
- text: "此外宁德时代上半年实现出口约2GWh，同比增加200%+。"

---

# Financial Sentiment Analysis in Chinese
This is a fine-tuned version of FinBERT, based on [bert-base-chinese](https://huggingface.co/bert-base-chinese), on a private dataset (around ~8k analyst report sentences) for sentiment analysis. 

* Test Accuracy = 0.88
* Test Macro F1 = 0.87  
* **Labels**: 0 -> Neutral; 1 -> Positive; 2 -> Negative

# Usage
```
from transformers import TextClassificationPipeline
from transformers import AutoModelForSequenceClassification, TrainingArguments, Trainer
from transformers import BertTokenizerFast
model_path="./fin_sentiment_bert_zh/"
new_model = AutoModelForSequenceClassification.from_pretrained(model_path,output_attentions=True)
tokenizer = BertTokenizerFast.from_pretrained(model_path)
PipelineInterface = TextClassificationPipeline(model=new_model, tokenizer=tokenizer, return_all_scores=True)
label = PipelineInterface("此外宁德时代上半年实现出口约2GWh，同比增加200%+。") 
print(label)
```
```
[[{'label': 'LABEL_0', 'score': 0.0007030126871541142}, {'label': 'LABEL_1', 'score': 0.9989339709281921}, {'label': 'LABEL_2', 'score': 0.000363016442861408}]]
```
