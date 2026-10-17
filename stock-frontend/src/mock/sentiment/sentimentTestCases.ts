// AI_GENERATE_START -
import rawDataset from './sentiment-stock-news-v1.json';

export type SentimentLabel = 'positive' | 'neutral' | 'negative';

export interface SentimentTestCase {
  id: string;
  title: string;
  text: string;
  expectedLabel: SentimentLabel;
  focus: string;
}

interface SentimentDatasetItem {
  id: string;
  text: string;
  expectedLabel: SentimentLabel;
}

const dataset = rawDataset as SentimentDatasetItem[];

export const allSentimentTestCases: SentimentTestCase[] = dataset.map((item) => ({
  id: item.id,
  title: item.id,
  text: item.text,
  expectedLabel: item.expectedLabel,
  focus: item.expectedLabel,
}));

export const pickRandomTestCase = (): SentimentTestCase =>
  allSentimentTestCases[Math.floor(Math.random() * allSentimentTestCases.length)] ?? allSentimentTestCases[0];
// AI_GENERATE_END -