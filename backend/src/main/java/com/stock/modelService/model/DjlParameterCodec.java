// AI_GENERATE_START -
package com.stock.modelService.model;

import ai.djl.Model;
import ai.djl.nn.Block;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 基于 DJL Block 参数流 API 的模型二进制编解码器。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Component
public class DjlParameterCodec implements ModelBinaryCodec {

    /**
     * 序列化模型参数。
     *
     * @param model 已设置 Block 的 DJL 模型
     * @return 参数二进制
     * @throws IOException 参数未初始化或写入失败
     */
    @Override
    public byte[] serialize(Model model) throws IOException {
        Block block = model.getBlock();
        if (block == null) {
            throw new IOException("Model block is not initialized");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            block.saveParameters(dos);
            dos.flush();
            return baos.toByteArray();
        }
    }

    /**
     * 将参数二进制加载到目标模型。
     *
     * @param paramsBytes 参数二进制
     * @param model 已设置兼容 Block 的目标模型
     * @throws IOException 参数格式错误或加载失败
     */
    @Override
    public void deserialize(byte[] paramsBytes, Model model) throws IOException {
        Block block = model.getBlock();
        if (block == null) {
            throw new IOException("Model block is not initialized");
        }
        if (model.getNDManager() == null) {
             throw new IOException("Model NDManager is not available");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(paramsBytes);
             DataInputStream dis = new DataInputStream(bais)) {
            block.loadParameters(model.getNDManager(), dis);
        } catch (ai.djl.MalformedModelException e) {
            throw new IOException("Failed to deserialize model parameters", e);
        }
    }
}
// AI_GENERATE_END -
