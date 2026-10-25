// AI_GENERATE_START-----
package com.stock.platform.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Around;
import org.springframework.stereotype.Component;

/**
 * REST 接口统一访问和耗时日志切面。
 * 仅记录控制器、方法、执行结果和耗时，避免将请求参数中的敏感信息写入日志。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Aspect
@Component
public class ApiLoggingAspect {

    /**
     * 记录 REST Controller 方法入口、完成状态和执行耗时。
     *
     * @param joinPoint 当前接口调用连接点
     * @return Controller 方法返回值
     * @throws Throwable Controller 方法抛出的原始异常
     */
    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logControllerInvocation(ProceedingJoinPoint joinPoint) throws Throwable {
        String controllerName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        long startNanos = System.nanoTime();
        log.info("[API] 访问接口: controller={}, method={}", controllerName, methodName);
        try {
            Object result = joinPoint.proceed();
            log.info("[API] 接口完成: controller={}, method={}, costMs={}",
                    controllerName, methodName, elapsedMillis(startNanos));
            return result;
        } catch (Throwable throwable) {
            log.warn("[API] 接口异常: controller={}, method={}, costMs={}, errorType={}",
                    controllerName, methodName, elapsedMillis(startNanos),
                    throwable.getClass().getSimpleName());
            throw throwable;
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
// AI_GENERATE_END-----
