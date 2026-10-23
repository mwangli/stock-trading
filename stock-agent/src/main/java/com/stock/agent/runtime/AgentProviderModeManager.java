// AI_GENERATE_START----
package com.stock.agent.runtime;

import com.stock.agent.config.AgentProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent Provider 运行时选择器。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Component
public class AgentProviderModeManager {

    private static final Set<String> SUPPORTED_MODES = Set.of("auto", "deepseek", "ollama", "disabled");

    private final AtomicReference<String> currentMode;

    /**
     * 使用配置文件中的 Provider 作为运行时初始模式。
     *
     * @param properties Agent 配置
     */
    public AgentProviderModeManager(AgentProperties properties) {
        this.currentMode = new AtomicReference<>(normalize(properties.getProvider()));
    }

    /**
     * 返回当前运行时 Provider 模式。
     *
     * @return Provider 模式
     */
    public String currentMode() {
        return currentMode.get();
    }

    /**
     * 更新当前运行时 Provider 模式。
     *
     * @param mode Provider 模式
     */
    public void switchTo(String mode) {
        String normalized = normalize(mode);
        if (!SUPPORTED_MODES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的 Provider 模式: " + mode);
        }
        currentMode.set(normalized);
    }

    private String normalize(String mode) {
        return StringUtils.hasText(mode) ? mode.trim().toLowerCase() : "auto";
    }
}
// AI_GENERATE_END----
