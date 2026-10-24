// AI_GENERATE_START--
package com.stock.decision.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略指标开关与 JSON 文本之间的 JPA 转换器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Converter
public class BooleanMapJsonConverter implements AttributeConverter<Map<String, Boolean>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Boolean>> MAP_TYPE = new TypeReference<>() { };

    /**
     * 将指标开关转换为数据库 JSON 文本。
     *
     * @param attribute 指标开关
     * @return JSON 文本
     */
    @Override
    public String convertToDatabaseColumn(Map<String, Boolean> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
        } catch (Exception exception) {
            throw new IllegalArgumentException("策略指标开关序列化失败", exception);
        }
    }

    /**
     * 将数据库 JSON 文本转换为指标开关。
     *
     * @param dbData JSON 文本
     * @return 指标开关
     */
    @Override
    public Map<String, Boolean> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(OBJECT_MAPPER.readValue(dbData, MAP_TYPE));
        } catch (Exception exception) {
            throw new IllegalArgumentException("策略指标开关反序列化失败", exception);
        }
    }
}
// AI_GENERATE_END--
