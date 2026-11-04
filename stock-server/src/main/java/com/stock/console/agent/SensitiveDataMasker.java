// AI_GENERATE_START -
package com.stock.console.agent;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SensitiveDataMasker {

    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?token|token|password|passwd|secret|authorization|username|account)"
                    + "(\\s*[=:：]\\s*[\\\"']?)([^\\s,;\\\"'}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{8,}");
    private static final Pattern GITHUB_TOKEN = Pattern.compile("\\bgh[pousr]_[A-Za-z0-9_]{20,}\\b");

    public String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = NAMED_SECRET.matcher(value).replaceAll("$1$2***");
        masked = BEARER.matcher(masked).replaceAll("Bearer ***");
        return GITHUB_TOKEN.matcher(masked).replaceAll("***");
    }
}
// AI_GENERATE_END -
