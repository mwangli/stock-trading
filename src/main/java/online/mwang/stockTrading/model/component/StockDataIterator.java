package online.mwang.stockTrading.model.component;

import online.mwang.stockTrading.web.bean.po.StockPrices;
import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;

public class StockDataIterator implements DataSetIterator {

    private final static int BATCH_SIZE = 32;
    private final static int SEQUENCE_LENGTH = 22;
    private final static int FEATURE_VECTOR = 1;
    private final static double SPLIT_RATIO=0.8;
    private final List<DataSet> inputDateSet = new ArrayList<>();
    private DataSetPreProcessor preProcessor = new NormalizerMinMaxScaler(0, 1);
    private int index = 0;

    public StockDataIterator(List<StockPrices> dataList) {
        for (int i = 0; i < dataList.size() - SEQUENCE_LENGTH - 1; i++) {
            INDArray feature = Nd4j.create(FEATURE_VECTOR, SEQUENCE_LENGTH);
            INDArray label = Nd4j.create(FEATURE_VECTOR, SEQUENCE_LENGTH);
            for (int row = 0; row < FEATURE_VECTOR; row++) {
                Double value = dataList.get(i + row).getIncreaseRate();
                for (int col = 0; col < SEQUENCE_LENGTH; col++) {
                    feature.putScalar(row, col, value);
                }
            }
            for (int row = 0; row < FEATURE_VECTOR; row++) {
                Double nextValue = dataList.get(i + row +1).getIncreaseRate();
                for (int col = 0; col < SEQUENCE_LENGTH; col++) {
                    label.putScalar(row, col, nextValue);
                }
            }
            inputDateSet.add(new DataSet(feature, label));
        }
    }

    @Override
    public DataSet next(int i) {
        return null;
    }

    @Override
    public int inputColumns() {
        return FEATURE_VECTOR;
    }

    @Override
    public int totalOutcomes() {
        return FEATURE_VECTOR;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        index = 0;
    }

    @Override
    public int batch() {
        return BATCH_SIZE;
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return preProcessor;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
        this.preProcessor = dataSetPreProcessor;
    }

    @Override
    public List<String> getLabels() {
        return null;
    }

    @Override
    public boolean hasNext() {
        return index + BATCH_SIZE < inputDateSet.size();
    }

    @Override
    public DataSet next() {
        INDArray input = Nd4j.create(BATCH_SIZE, FEATURE_VECTOR, SEQUENCE_LENGTH);
        INDArray output = Nd4j.create(BATCH_SIZE, FEATURE_VECTOR, SEQUENCE_LENGTH);
        for (int i = 0; i < BATCH_SIZE; i++) {
            DataSet dataSet = inputDateSet.get(index+i);
            input.putRow(i, dataSet.getFeatures());
            output.putRow(i, dataSet.getLabels());
        }
        index += BATCH_SIZE;
        return new DataSet(input, output);
    }
}
