// AI_GENERATE_START ---
package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.domain.dto.BrokerLoginResultDto;
import com.stock.tradingExecutor.execution.ZXBrokerAdapter;
import com.stock.tradingExecutor.execution.ZXBrokerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 券商验证码登录接口。
 * 登录凭据只从运行环境读取，接口不接收密码且不向调用方返回 Token。
 *
 * @author mwangli
 * @since 2026-09-30
 */
@Slf4j
@RestController
@RequestMapping("/api/broker-auth")
@RequiredArgsConstructor
public class BrokerAuthController {

    private final ZXBrokerAdapter brokerAdapter;
    private final ZXBrokerConfig brokerConfig;

    /**
     * 使用受控百度云 OCR 和配置中的加密密码登录券商。
     *
     * @return 登录状态和脱敏账号
     */
    @PostMapping("/login")
    public ResponseDTO<BrokerLoginResultDto> login() {
        log.info("请求使用受控百度云 OCR 登录券商，自动提交开关={}",
                brokerConfig.getCaptchaAutoSubmitEnabled());
        try {
            boolean authenticated = brokerAdapter.loginConfiguredAccount();
            BrokerLoginResultDto result = BrokerLoginResultDto.builder()
                    .authenticated(authenticated)
                    .maskedAccount(maskAccount(brokerConfig.getAccount()))
                    .build();
            return authenticated ? ResponseDTO.success(result) : ResponseDTO.error("券商登录失败");
        } catch (IllegalStateException exception) {
            log.warn("券商登录未完成: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("券商登录异常", exception);
            return ResponseDTO.error("券商登录失败，请检查服务日志");
        }
    }

    private String maskAccount(String account) {
        if (account == null || account.length() <= 4) {
            return "****";
        }
        return "****" + account.substring(account.length() - 4);
    }
}
// AI_GENERATE_END ---