package online.mwang.stockTrading.web.utils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.datavec.api.transform.transform.doubletransform.MinMaxNormalizer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.dataset.api.preprocessor.Normalizer;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.dataset.api.preprocessor.serializer.NormalizerSerializer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/5/8 10:38
 * @description: MongoUtils
 */
@Component
@RequiredArgsConstructor
public class GridFsUtils {

    private final GridFsTemplate gridFsTemplate;

    @SneakyThrows
    public void saveToMongo(ByteArrayOutputStream outputStream, String fileName) {
        gridFsTemplate.delete(new Query(Criteria.where("fileName").is(fileName)));
        gridFsTemplate.store(new ByteArrayInputStream(outputStream.toByteArray()), fileName);
    }

    @SneakyThrows
    public InputStream readFromMongo(String fileName) {
        GridFsResource resource = gridFsTemplate.getResource(fileName);
        return resource.getInputStream();
    }

    @SneakyThrows
    public void saveModelToMongo(MultiLayerNetwork model, String fileName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ModelSerializer.writeModel(model, outputStream, true);
        saveToMongo(outputStream, fileName);
    }

    @SneakyThrows
    public MultiLayerNetwork readModelFromMongo(String fileName) {
        InputStream inputStream = readFromMongo(fileName);
        return ModelSerializer.restoreMultiLayerNetwork(inputStream);
    }

    @SneakyThrows
    public void saveScalerToMongo(NormalizerMinMaxScaler minMaxScaler, String fileName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        NormalizerSerializer.getDefault().write(minMaxScaler, outputStream);
        saveToMongo(outputStream, fileName);
    }

    @SneakyThrows
    public NormalizerMinMaxScaler readScalerFromMongo(String fileName) {
        InputStream inputStream = readFromMongo(fileName);
        return NormalizerSerializer.getDefault().restore(inputStream);
    }
}
