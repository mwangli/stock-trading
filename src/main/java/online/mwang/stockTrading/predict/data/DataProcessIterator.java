package online.mwang.stockTrading.predict.data;

import com.opencsv.CSVReader;
import lombok.SneakyThrows;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.nd4j.linalg.primitives.Pair;


public class DataProcessIterator implements DataSetIterator {

    private final int VECTOR_SIZE = 1; // number of features for a stock data
    private int BATCH_SIZE; // mini-batch size
    private int exampleLength = 22; // default 22, say, 22 working days per month
    private int predictLength = 1; // default 1, say, one day ahead prediction

    /**
     * minimal values of each feature in stock dataset
     */
    private double[] minArray = new double[VECTOR_SIZE];
    /**
     * maximal values of each feature in stock dataset
     */
    private double[] maxArray = new double[VECTOR_SIZE];

    /**
     * mini-batch offset
     */
    private LinkedList<Integer> exampleStartOffsets = new LinkedList<>();

    /**
     * stock dataset for training
     */
    private List<StockData> train;
    /**
     * adjusted stock dataset for testing
     */
    private List<Pair<INDArray, INDArray>> test;

    public DataProcessIterator(String filename, int BATCH_SIZE, int exampleLength, double splitRatio) {
        List<StockData> stockDataList = readStockDataFromFile(filename);
        this.BATCH_SIZE = BATCH_SIZE;
        this.exampleLength = exampleLength;
        int split = (int) Math.round(stockDataList.size() * splitRatio);
        train = stockDataList.subList(0, split);
        test = generateTestDataSet(stockDataList.subList(split, stockDataList.size()));
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

    public double getMaxNum() {
        return maxArray[0];
    }

    public double getMinNum() {
        return minArray[0];
    }

    @Override
    public DataSet next(int num) {
        if (exampleStartOffsets.size() == 0) throw new NoSuchElementException();
        int actualMiniBatchSize = Math.min(num, exampleStartOffsets.size());
        INDArray input = Nd4j.create(new int[]{actualMiniBatchSize, VECTOR_SIZE, exampleLength}, 'f');
        INDArray label;
        label = Nd4j.create(new int[]{actualMiniBatchSize, predictLength, exampleLength}, 'f');
        for (int index = 0; index < actualMiniBatchSize; index++) {
            int startIdx = exampleStartOffsets.removeFirst();
            int endIdx = startIdx + exampleLength;
            StockData curData = train.get(startIdx);
            StockData nextData;
            for (int i = startIdx; i < endIdx; i++) {
                int c = i - startIdx;
                nextData = train.get(i + 1);
                input.putScalar(new int[] {index, 0, c}, (curData.getPrice1() - minArray[0]) / (maxArray[0] - minArray[0]));
//                input.putScalar(new int[] {index, 1, c}, (curData.getPrice2() - minArray[1]) / (maxArray[1] - minArray[1]));

                label.putScalar(new int[] {index, 0, c}, (nextData.getPrice1() - minArray[0]) / (maxArray[0] - minArray[0]));
//                label.putScalar(new int[] {index, 1, c}, (nextData.getPrice2() - minArray[1]) / (maxArray[1] - minArray[1]));

                curData = nextData;
            }
            if (exampleStartOffsets.size() == 0) break;
        }
        return new DataSet(input, label);
    }

//    private double feedLabel(StockData data) {
//        double value;
//        value = (data.getPrice1() - minArray[0]) / (maxArray[0] - minArray[0]);
//        return value;
//    }


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
        return BATCH_SIZE;
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
        return next(BATCH_SIZE);
    }

    private List<Pair<INDArray, INDArray>> generateTestDataSet(List<StockData> stockDataList) {
        int window = exampleLength + predictLength;
        List<Pair<INDArray, INDArray>> test = new ArrayList<>();
        for (int i = 0; i < stockDataList.size() - window; i++) {
            INDArray input = Nd4j.create(new int[]{exampleLength, VECTOR_SIZE}, 'f');
            for (int j = i; j < i + exampleLength; j++) {
                StockData stock = stockDataList.get(j);
                input.putScalar(new int[]{j - i, 0}, (stock.getPrice1() - minArray[0]) / (maxArray[0] - minArray[0]));

            }
            StockData stock = stockDataList.get(i + exampleLength);
            INDArray label;
            label = Nd4j.create(new int[]{1}, 'f');
            label.putScalar(new int[]{0}, stock.getPrice1());
            test.add(new Pair<>(input, label));
        }
        return test;
    }


    @SneakyThrows
    private List<StockData> readStockDataFromFile(String filename) {
        List<StockData> stockDataList = new ArrayList<>();

        for (int i = 0; i < maxArray.length; i++) { // initialize max and min arrays
            maxArray[i] = Double.MIN_VALUE;
            minArray[i] = Double.MAX_VALUE;
        }
        List<String[]> list = new CSVReader(new FileReader(filename)).readAll(); // load all elements in a list
        for (String[] arr : list) {
            //  去掉第0行的标题
            if (arr[0].equals("date")) continue;
            double[] nums = new double[VECTOR_SIZE];
            for (int i = 0; i < VECTOR_SIZE; i++) {
                // 从第1列开始读取，第0列是日期，VECTOR_SIZE 是输入特征维度，即输入多少列
                nums[i] = Double.parseDouble(arr[i + 1]);
                if (nums[i] > maxArray[i]) maxArray[i] = nums[i];
                if (nums[i] < minArray[i]) minArray[i] = nums[i];
            }
            StockData stockData = new StockData();
            stockData.setDate(arr[0]);
            // 保存特征维度
            for (int i = 0; i < VECTOR_SIZE; i++) {
                stockData.setPrice1(nums[0]);
//                    new StockData(arr[0], arr[1], nums[0], nums[1], nums[2], nums[3], nums[4])
            }
            stockDataList.add(stockData);
        }

        return stockDataList;
    }
}
