package com.stock.modelService.support;

import ai.djl.Device;
import ai.djl.engine.Engine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * LSTM 训练 GPU 诊断
 * <p>
 * 应用启动时检测 DJL/PyTorch 可用的计算设备，便于排查 Windows 下
 * 为何显示 CPU 而非 GPU 的问题。
 * </p>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@Component
public class LstmGpuDiagnostic {

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        try {
            Engine engine = Engine.getInstance();
            log.info("[LSTM GPU 诊断] 引擎: {} {}", engine.getEngineName(), engine.getVersion());

            Device[] devices = engine.getDevices(8);
            if (devices == null || devices.length == 0) {
                log.warn("[LSTM GPU 诊断] 未检测到任何设备，将使用 CPU");
                return;
            }

            for (int i = 0; i < devices.length; i++) {
                Device d = devices[i];
                log.info("[LSTM GPU 诊断] 设备[{}]: {} (type={})", i, d, d.getDeviceType());
            }

            boolean hasGpu = false;
            for (Device d : devices) {
                if (d.getDeviceType() == Device.Type.GPU) {
                    hasGpu = true;
                    break;
                }
            }

            if (!hasGpu) {
                log.warn("[LSTM GPU 诊断] 未检测到 GPU，可能原因：");
                log.warn("  1. CUDA 版本不匹配：系统为 CUDA 11.2 时，需升级至 CUDA 12.1（本项目使用 cu121）");
                log.warn("     下载: https://developer.nvidia.com/cuda-12-1-0-download-archive");
                log.warn("  2. 未安装 Visual C++ 2019 Redistributable");
                log.warn("  3. NVIDIA 驱动过旧，需支持 CUDA 12.1");
                log.warn("  4. 运行环境为 Linux 时，pom.xml 中 Linux 依赖为 pytorch-native-cpu，需改为 cu121");
            } else {
                log.info("[LSTM GPU 诊断] GPU 可用，训练将使用 GPU 加速");
            }
        } catch (Exception e) {
            log.error("[LSTM GPU 诊断] 检测失败", e);
        }
    }
}
