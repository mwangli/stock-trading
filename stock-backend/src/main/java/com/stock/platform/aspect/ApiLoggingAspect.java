// AI_GENERATE_START----
package com.stock.platform.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * REST 接口统一入口日志切面。
 * 仅记录控制器和方法名称，避免将请求体中的密码、Token 或券商凭据写入日志。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Aspect
@Component
public class ApiLoggingAspect {

    /**
     * 记录 REST Controller 方法入口。
     *
     * @param joinPoint 当前接口调用连接点
     */
    @Before("within(@org.springframework.web.bind.annotation.RestController *)")
    public void logControllerEntry(JoinPoint joinPoint) {
        String controllerName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        log.info("[API] 访问接口: controller={}, method={}", controllerName, methodName);
    }
}
// AI_GENERATE_END----
