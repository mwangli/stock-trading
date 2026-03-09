package com.stock.modelService.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * LSTM 模型仓库自定义实现
 * 使用 MongoTemplate.distinct 实现高效查询
 *
 * @author AI Assistant
 * @since 1.0
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
}
