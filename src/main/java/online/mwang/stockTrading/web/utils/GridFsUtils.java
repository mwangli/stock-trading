package online.mwang.stockTrading.web.utils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.dataset.api.preprocessor.serializer.NormalizerSerializer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

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
        gridFsTemplate.delete(new Query(Criteria.where("filename").is(fileName)));
        gridFsTemplate.store(new ByteArrayInputStream(outputStream.toByteArray()), fileName);
    }


    @SneakyThrows
    public ByteArrayInputStream readFromMongo(String fileName) {
        GridFsResource resource = gridFsTemplate.getResource(fileName);
        return !resource.exists() ? null : new ByteArrayInputStream(resource.getInputStream().readAllBytes());
    }

    @SneakyThrows
    public void deleteFile(String fileName) {
        gridFsTemplate.delete(new Query(Criteria.where("filename").is(fileName)));
    }

    @SneakyThrows
    public void saveModelToMongo(MultiLayerNetwork model, String fileName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ModelSerializer.writeModel(model, outputStream, true);
        saveToMongo(outputStream, fileName);
    }

    @SneakyThrows
    public void saveScalerToMongo(NormalizerMinMaxScaler minMaxScaler, String fileName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        NormalizerSerializer.getDefault().write(minMaxScaler, outputStream);
        saveToMongo(outputStream, fileName);
    }

    @SneakyThrows
    public MultiLayerNetwork readModelFromMongo(String fileName) {
        ByteArrayInputStream inputStream = readFromMongo(fileName);
        return inputStream == null ? null : ModelSerializer.restoreMultiLayerNetwork(inputStream);
    }

    @SneakyThrows
    public NormalizerMinMaxScaler readScalerFromMongo(String fileName) {
        ByteArrayInputStream inputStream = readFromMongo(fileName);
        return inputStream == null ? null : NormalizerSerializer.getDefault().restore(inputStream);
    }
}
