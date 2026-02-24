---
language: "en"
tags:
- financial-sentiment-analysis
- sentiment-analysis
widget:
- text: "Stocks rallied and the British pound gained."
---

FinBERT is a pre-trained NLP model to analyze sentiment of financial text. It is built by further training the BERT language model in the finance domain, using a large financial corpus and thereby fine-tuning it for financial sentiment classification. [Financial PhraseBank](https://www.researchgate.net/publication/251231107_Good_Debt_or_Bad_Debt_Detecting_Semantic_Orientations_in_Economic_Texts) by Malo et al. (2014) is used for fine-tuning. For more details, please see the paper [FinBERT: Financial Sentiment Analysis with Pre-trained Language Models](https://arxiv.org/abs/1908.10063) and our related [blog post](https://medium.com/prosus-ai-tech-blog/finbert-financial-sentiment-analysis-with-bert-b277a3607101) on Medium.

The model will give softmax outputs for three labels: positive, negative or neutral.

---

About Prosus

Prosus is a global consumer internet group and one of the largest technology investors in the world. Operating and investing globally in markets with long-term growth potential, Prosus builds leading consumer internet companies that empower people and enrich communities. For more information, please visit www.prosus.com.

Contact information

Please contact Dogu Araci dogu.araci[at]prosus[dot]com and Zulkuf Genc zulkuf.genc[at]prosus[dot]com about any FinBERT related issues and questions.
