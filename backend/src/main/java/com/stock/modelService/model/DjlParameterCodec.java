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
 * Implementation of ModelBinaryCodec using DJL's Block parameter streaming API.
 */
@Component
public class DjlParameterCodec implements ModelBinaryCodec {

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
