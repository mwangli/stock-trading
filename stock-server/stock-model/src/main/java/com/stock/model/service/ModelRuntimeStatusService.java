// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelMetadataDocument;
import com.stock.model.repository.ModelMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 模型运行时状态持久化服务。
 *
 * 将启动预热和模型切换产生的运行时校验结果写入 MySQL，供页面查询直接读取。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRuntimeStatusService {

    /** 运行时校验进行中。 */
    public static final String STATUS_CHECKING = "RUNTIME_CHECKING";

    /** 运行时校验成功。 */
    public static final String STATUS_HEALTHY = "RUNTIME_HEALTHY";

    /** 运行时校验失败。 */
    public static final String STATUS_UNHEALTHY = "RUNTIME_UNHEALTHY";

    private final ModelMetadataRepository metadataRepository;

    /**
     * 记录指定模型版本的运行时校验状态。
     *
     * 页面查询只读取本记录，不在请求线程内重新加载 GridFS 参数或执行模型前向验证。
     * 状态记录失败时保留模型原有运行结果，并输出明确日志供运维排查。
     *
     * @param model 模型版本元数据，不可为空
     * @param status 运行时状态
     * @param errorMessage 校验失败原因，成功或检查中时为空
     */
    public void record(LstmModelDocument model, String status, String errorMessage) {
        if (model == null) {
            return;
        }
        try {
            ModelMetadataDocument metadata = metadataRepository.findByModelName(model.getModelName())
                    .orElseGet(ModelMetadataDocument::new);
            metadata.setModelName(model.getModelName());
            metadata.setModelVersion(model.getModelVersion());
            metadata.setStatus(status);
            metadata.setEngineName(model.getEngineName());
            metadata.setLastError(errorMessage);
            metadata.setUpdatedAt(LocalDateTime.now());
            metadataRepository.save(metadata);
        } catch (RuntimeException exception) {
            log.warn("记录模型运行时状态失败: modelId={}, status={}, reason={}",
                    model.getId(), status, exception.getMessage());
        }
    }
}
// AI_GENERATE_END -
