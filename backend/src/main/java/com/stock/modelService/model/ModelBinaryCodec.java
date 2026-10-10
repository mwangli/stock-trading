// AI_GENERATE_START -
package com.stock.modelService.model;

import ai.djl.Model;
import java.io.IOException;

/**
 * DJL 模型参数序列化接口。
 * 用于不依赖本地文件系统地保存和恢复小型模型参数。
 *
 * @author mwangli
 * @since 2026-10-10
 */
public interface ModelBinaryCodec {
    /**
     * Serialize model parameters to byte array.
     * @param model DJL Model containing the block and parameters
     * @return byte array containing serialized parameters
     * @throws IOException if serialization fails
     */
    byte[] serialize(Model model) throws IOException;

    /**
     * Deserialize parameters from byte array into the model's block.
     * The model must have a block set and an NDManager available.
     * @param paramsBytes serialized parameters
     * @param model target Model to load parameters into
     * @throws IOException if deserialization fails
     */
    void deserialize(byte[] paramsBytes, Model model) throws IOException;
}
// AI_GENERATE_END -
