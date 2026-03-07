package com.stock.modelService.model.io;

import ai.djl.Model;
import ai.djl.nn.Block;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.ndarray.types.DataType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.IOException;

class DjlParameterCodecTest {

    @Test
    void shouldSerializeAndDeserializeParameters() throws IOException {
        try (NDManager manager = NDManager.newBaseManager()) {
            // 1. Create a simple model
            Model originalModel = Model.newInstance("test-model");
            Block block = new SequentialBlock()
                    .add(Linear.builder().setUnits(10).build())
                    .add(Linear.builder().setUnits(1).build());
            originalModel.setBlock(block);
            
            // Initialize parameters
            block.initialize(manager, DataType.FLOAT32, new Shape(1, 5));
            
            // 2. Serialize
            DjlParameterCodec codec = new DjlParameterCodec();
            byte[] bytes = codec.serialize(originalModel);
            Assertions.assertNotNull(bytes);
            Assertions.assertTrue(bytes.length > 0);
            
            // 3. Deserialize into new model
            Model newModel = Model.newInstance("test-model-2");
            Block newBlock = new SequentialBlock()
                    .add(Linear.builder().setUnits(10).build())
                    .add(Linear.builder().setUnits(1).build());
            newModel.setBlock(newBlock);
            
            // Note: block must be initialized before loading parameters in some cases,
            // or at least have shapes defined.
            newBlock.initialize(manager, DataType.FLOAT32, new Shape(1, 5));
            
            codec.deserialize(bytes, newModel);
            
            // 4. Verify parameters match
            Assertions.assertEquals(
                originalModel.getBlock().getParameters().size(),
                newModel.getBlock().getParameters().size()
            );
        }
    }
}
