// AI_GENERATE_START----
package com.stock.starter.config;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 为遗留 Long 主键的 MongoDB 文档生成单实例单调标识。
 * 字符串业务主键和已经赋值的主键不会被修改。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class MongoLongIdGenerator implements BeforeConvertCallback<Object> {

    private static final int TIME_SHIFT = 20;

    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() << TIME_SHIFT);

    /**
     * 在文档转换前填充空的 Long 类型 Mongo 主键。
     *
     * @param entity 待保存文档
     * @param collection 目标集合名称
     * @return 已完成主键填充的文档
     */
    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        Field idField = findLongIdField(entity.getClass());
        try {
            boolean newDocument = idField != null && readField(idField, entity) == null;
            invokeLifecycle(entity, newDocument ? "onCreate" : "onUpdate");
            if (newDocument) {
                idField.set(entity, nextId());
            }
            return entity;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("MongoDB 文档保存前初始化失败: "
                    + entity.getClass().getName(), exception);
        }
    }

    private Object readField(Field field, Object entity) throws IllegalAccessException {
        field.setAccessible(true);
        return field.get(entity);
    }

    private void invokeLifecycle(Object entity, String methodName)
            throws InvocationTargetException, IllegalAccessException {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(entity);
                return;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
    }

    private Field findLongIdField(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == Long.class && field.isAnnotationPresent(Id.class)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private long nextId() {
        long timeFloor = System.currentTimeMillis() << TIME_SHIFT;
        return sequence.updateAndGet(previous -> Math.max(previous + 1, timeFloor));
    }
}
// AI_GENERATE_END----
