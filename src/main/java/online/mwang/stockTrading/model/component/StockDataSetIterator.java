package online.mwang.stockTrading.model.component;

import javafx.util.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by zhanghao on 26/7/17.
 * Modified by zhanghao on 28/9/17.
 *
 * @author ZHANG HAO
 */
public class StockDataSetIterator implements DataSetIterator {

    private final int VECTOR_SIZE = 2;
    private final int miniBatchSize;
    private final int exampleLength;
    private final int predictLength = 1;

    /**
     * minimal values of each feature in stock dataset
     */
    private final double[] minArray;
    /**
     * maximal values of each feature in stock dataset
     */
    private final double[] maxArray;

    /**
     * mini-batch offset
     */
    private final LinkedList<Integer> exampleStartOffsets = new LinkedList<>();

    /**
     * stock dataset for training
     */
    private final List<StockData> train;
    /**
     * adjusted stock dataset for testing
     */
    private final List<Pair<INDArray, INDArray>> test;

    private List<String> dateList;

    public List<String> getDateList() {
        return this.dateList;
    }

    public StockDataSetIterator(List<StockData> dataList, int miniBatchSize, int exampleLength, double splitRatio) {
        assert !dataList.isEmpty();
        List<Double> openPriceList = dataList.stream().map(StockData::getOpen).filter(p -> p > 0).collect(Collectors.toList());
        double max1 = openPriceList.stream().mapToDouble(s -> s).max().orElse(0);
        double min1 = openPriceList.stream().mapToDouble(s -> s).min().orElse(0);
        List<Double> closePriceList = dataList.stream().map(StockData::getClose).filter(p -> p > 0).collect(Collectors.toList());
        double max2 = closePriceList.stream().mapToDouble(s -> s).max().orElse(0);
        double min2 = closePriceList.stream().mapToDouble(s -> s).min().orElse(0);
        minArray = new double[]{min1, min2};
        maxArray = new double[]{max1, max2};
        this.miniBatchSize = miniBatchSize;
        this.exampleLength = exampleLength;
        int split = (int) Math.round(dataList.size() * splitRatio);
        train = dataList.subList(0, split);
        test = generateTestDataSet(dataList.subList(split, dataList.size()));
        initializeOffsets();
    }

    /**
     * initialize the mini-batch offsets
     */
    private void initializeOffsets() {
        exampleStartOffsets.clear();
        int window = exampleLength + predictLength;
        for (int i = 0; i < train.size() - window; i++) {
            exampleStartOffsets.add(i);
        }
    }

    public List<Pair<INDArray, INDArray>> getTestDataSet() {
        return test;
    }

    public double[] getMaxArray() {
        return maxArray;
    }

    public double[] getMinArray() {
        return minArray;
    }

    @Override
    public DataSet next(int num) {
        if (exampleStartOffsets.size() == 0) throw new NoSuchElementException();
        int actualMiniBatchSize = Math.min(num, exampleStartOffsets.size());
        INDArray input = Nd4j.create(new int[]{actualMiniBatchSize, VECTOR_SIZE, exampleLength}, 'f');
        INDArray label = Nd4j.create(new int[]{actualMiniBatchSize, VECTOR_SIZE, exampleLength}, 'f');
        for (int index = 0; index < actualMiniBatchSize; index++) {
            int startIdx = exampleStartOffsets.removeFirst();
            int endIdx = startIdx + exampleLength;
            StockData curData = train.get(startIdx);
            StockData nextData;
            for (int i = startIdx; i < endIdx; i++) {
                int c = i - startIdx;
                input.putScalar(new int[]{index, 0, c}, (curData.getOpen() - minArray[0]) / (maxArray[0] - minArray[0]));
                input.putScalar(new int[]{index, 1, c}, (curData.getClose() - minArray[1]) / (maxArray[1] - minArray[1]));
                nextData = train.get(i + 1);
                label.putScalar(new int[]{index, 0, c}, (nextData.getOpen() - minArray[1]) / (maxArray[1] - minArray[1]));
                label.putScalar(new int[]{index, 1, c}, (nextData.getClose() - minArray[1]) / (maxArray[1] - minArray[1]));
                curData = nextData;
            }
            if (exampleStartOffsets.size() == 0) break;
        }
        return new DataSet(input, label);
    }

    @Override
    public int totalExamples() {
        return train.size() - exampleLength - predictLength;
    }

    @Override
    public int inputColumns() {
        return VECTOR_SIZE;
    }

    @Override
    public int totalOutcomes() {
        return VECTOR_SIZE;
    }

    @Override
    public boolean resetSupported() {
        return false;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        initializeOffsets();
    }

    @Override
    public int batch() {
        return miniBatchSize;
    }

    @Override
    public int cursor() {
        return totalExamples() - exampleStartOffsets.size();
    }

    @Override
    public int numExamples() {
        return totalExamples();
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public boolean hasNext() {
        return exampleStartOffsets.size() > 0;
    }

    @Override
    public DataSet next() {
        return next(miniBatchSize);
    }

    private List<Pair<INDArray, INDArray>> generateTestDataSet(List<StockData> stockDataList) {
        int window = exampleLength + predictLength;
        List<Pair<INDArray, INDArray>> test = new ArrayList<>();
        List<String> dateList = new ArrayList<>();
        for (int i = 0; i < stockDataList.size() - window; i++) {
            INDArray input = Nd4j.create(new int[]{exampleLength, VECTOR_SIZE}, 'f');
            for (int j = i; j < i + exampleLength; j++) {
                StockData stock = stockDataList.get(j);
                input.putScalar(new int[]{j - i, 0}, (stock.getOpen() - minArray[0]) / (maxArray[0] - minArray[0]));
                input.putScalar(new int[]{j - i, 1}, (stock.getClose() - minArray[1]) / (maxArray[1] - minArray[1]));
            }
            StockData stock = stockDataList.get(i + exampleLength);
            INDArray label;
            label = Nd4j.create(new int[]{VECTOR_SIZE}, 'f');
            label.putScalar(new int[]{0}, stock.getOpen());
            label.putScalar(new int[]{1}, stock.getClose());
            test.add(new Pair<>(input, label));
            dateList.add(stock.getDate());
        }
        this.dateList = dateList;
        return test;
    }
}
