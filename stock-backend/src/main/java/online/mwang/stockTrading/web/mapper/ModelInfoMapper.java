package online.mwang.stockTrading.web.mapper;

import online.mwang.stockTrading.entities.ModelInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ModelInfoMapper {
    List<ModelInfo> findAll();
    ModelInfo findById(Long id);
    void save(ModelInfo modelInfo);
    void update(ModelInfo modelInfo);
    void delete(Long id);
}
