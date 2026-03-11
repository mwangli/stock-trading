package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.LstmModelDocument;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LSTM 模型仓库自定义实现
 * 使用 MongoTemplate.distinct 实现高效查询，使用聚合实现批量按 code 查询最新模型
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Repository
@RequiredArgsConstructor
public class LstmModelRepositoryImpl implements LstmModelRepositoryCustom {

    private static final String COLLECTION = "lstm_models";
    private static final String FIELD_MODEL_NAME = "modelName";

    private final MongoTemplate mongoTemplate;

    @Override
    public List<String> findAllModelNamesDistinct() {
        List<String> raw = mongoTemplate.findDistinct(new Query(), FIELD_MODEL_NAME, COLLECTION, String.class);
        return raw == null ? List.of() : raw.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public Map<String, LstmModelDocument> findLatestByModelNames(List<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctNames = modelNames.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(FIELD_MODEL_NAME).in(distinctNames)),
                Aggregation.project("_id", FIELD_MODEL_NAME, "epoch", "createdAt", "trainLoss", "valLoss"),
                Aggregation.sort(Sort.by(Sort.Order.asc(FIELD_MODEL_NAME), Sort.Order.desc("createdAt"))),
                Aggregation.group(FIELD_MODEL_NAME).first(Aggregation.ROOT).as("doc")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
        Map<String, LstmModelDocument> map = new HashMap<>();
        for (Document doc : results.getMappedResults()) {
            Document inner = doc.get("doc", Document.class);
            if (inner == null) {
                continue;
            }
            String modelName = inner.getString(FIELD_MODEL_NAME);
            if (modelName == null) {
                continue;
            }
            LstmModelDocument entity = new LstmModelDocument();
            entity.setId(inner.getObjectId("_id") != null ? inner.getObjectId("_id").toHexString() : null);
            entity.setModelName(modelName);
            entity.setEpoch(inner.getInteger("epoch", 0));
            Object createdAt = inner.get("createdAt");
            if (createdAt instanceof Date) {
                entity.setCreatedAt(((Date) createdAt).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            } else if (createdAt instanceof LocalDateTime) {
                entity.setCreatedAt((LocalDateTime) createdAt);
            }
            entity.setTrainLoss(inner.getDouble("trainLoss"));
            entity.setValLoss(inner.getDouble("valLoss"));
            map.put(modelName, entity);
        }
        return map;
    }
}
